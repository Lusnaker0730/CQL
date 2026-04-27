import { useState, useMemo, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { FHIR_RESOURCE_TYPES } from '../../constants/fhirResources'
import { extractCqlName } from '../../utils/cqlNames'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  Button,
  IconButton,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
  FormControlLabel,
  Checkbox,
  Divider,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Add as AddIcon,
  ContentCopy as CopyIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import { fhirResourceProperties } from '../../utils/cqlSyntax'

interface QueryBuilderProps {
  valueSets: string[]
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

interface WhereClause {
  id: string
  field: string
  operator: string
  value: string
  conjunction: 'and' | 'or'
}

interface WithClause {
  id: string
  mode: 'with' | 'without'
  resourceType: string
  alias: string
  suchThat: string
}

interface LetBinding {
  id: string
  name: string
  expression: string
}

const OPERATORS = [
  '=', '!=', '~', 'in', 'includes concept', 'during', 'before', 'after',
  'contains', '>', '<', '>=', '<=', 'is not null', 'is null',
]

const CQL_LITERAL_RE = /^("[^"]*"|'[^']*'|true|false|null|-?\d+(\.\d+)?|@\S+|Interval\b.*)$/

/**
 * Choice-typed date fields whose raw HAPI value isn't safely Comparable.
 * Sort expressions for these must extract a primitive via cast + .value
 * (per CLAUDE.md TWCDI: HAPI DateTimeType doesn't implement Comparable).
 */
const CHOICE_DATE_FIELDS = new Set([
  'effective', 'onset', 'performed', 'occurrence',
])

function buildSortExpr(field: string, alias: string): string {
  if (CHOICE_DATE_FIELDS.has(field)) {
    return `(${alias}.${field} as FHIR.dateTime).value`
  }
  // period (Encounter) is a Period, not a choice — use start
  if (field === 'period') return `${alias}.${field}.start.value`
  return field
}

function generateAlias(resourceType: string): string {
  return resourceType.charAt(0)
}

function generateDefName(resourceType: string, terminology: string): string {
  if (!terminology) return resourceType + ' Query'
  const termName = extractCqlName(terminology)
  return `${termName} ${resourceType}s`
}

function generateQueryCql(
  resourceType: string,
  terminology: string,
  alias: string,
  definitionName: string,
  whereClauses: WhereClause[],
  withClauses: WithClause[],
  letBindings: LetBinding[],
  enableSort: boolean,
  sortField: string,
  sortDir: 'asc' | 'desc',
  enableReturn: boolean,
  returnExpr: string,
  enableDistinct: boolean,
): string {
  const termPart = terminology ? `: "${extractCqlName(terminology)}"` : ''
  let cql = `define "${definitionName}":\n  [${resourceType}${termPart}] ${alias}`

  // let bindings
  const validLets = letBindings.filter((b) => b.name.trim() && b.expression.trim())
  validLets.forEach((binding) => {
    cql += `\n    let ${binding.name.trim()}: ${binding.expression.trim()}`
  })

  // with / without clauses
  const validWiths = withClauses.filter((w) => w.resourceType && w.suchThat.trim())
  validWiths.forEach((w) => {
    cql += `\n    ${w.mode} [${w.resourceType}] ${w.alias}`
    cql += `\n      such that ${w.suchThat.trim()}`
  })

  // where clauses
  const validClauses = whereClauses.filter((c) => c.field && c.operator)
  if (validClauses.length > 0) {
    validClauses.forEach((clause, idx) => {
      const prefix = idx === 0 ? '\n    where ' : `\n      ${clause.conjunction} `
      let condition: string
      if (clause.operator === 'includes concept') {
        const val = clause.value?.trim() || '"..."'
        condition = `exists (${alias}.${clause.field} C where C ~ ${val})`
      } else {
        condition = `${alias}.${clause.field} ${clause.operator}`
        if (clause.operator !== 'is null' && clause.operator !== 'is not null') {
          const raw = clause.value?.trim() || ''
          const isLiteral = CQL_LITERAL_RE.test(raw)
          condition += ` ${raw ? (isLiteral ? raw : `'${raw}'`) : "'...'"}`
        }
      }
      cql += `${prefix}${condition}`
    })
  }

  // return (with optional distinct)
  if (enableReturn && returnExpr.trim()) {
    cql += `\n    return ${enableDistinct ? 'distinct ' : ''}${returnExpr}`
  } else if (enableDistinct) {
    cql += `\n    return distinct ${alias}`
  }

  if (enableSort && sortField) {
    cql += `\n    sort by ${buildSortExpr(sortField, alias)} ${sortDir}`
  }

  return cql
}

export default function QueryBuilder({ valueSets, codes, onInsert, onCancel }: QueryBuilderProps) {
  const { t } = useTranslation('builder')
  const copyToClipboard = useCopyToClipboard()
  const [resourceType, setResourceType] = useState<string>('Observation')
  const [terminology, setTerminology] = useState('')
  const [alias, setAlias] = useState('O')
  const [aliasEdited, setAliasEdited] = useState(false)
  const [definitionName, setDefinitionName] = useState('Observation Query')
  const [nameEdited, setNameEdited] = useState(false)
  const [whereClauses, setWhereClauses] = useState<WhereClause[]>([])
  const [withClauses, setWithClauses] = useState<WithClause[]>([])
  const [letBindings, setLetBindings] = useState<LetBinding[]>([])
  const [enableSort, setEnableSort] = useState(false)
  const [sortField, setSortField] = useState('')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc')
  const [enableReturn, setEnableReturn] = useState(false)
  const [returnExpr, setReturnExpr] = useState('')
  const [enableDistinct, setEnableDistinct] = useState(false)
  const clauseIdRef = useRef(0)
  const nextClauseId = useCallback(() => `wc_${++clauseIdRef.current}`, [])

  const terminologyOptions = useMemo(() => {
    const vsOptions = valueSets.map((vs) => ({ raw: vs, label: `VS: ${extractCqlName(vs)}` }))
    const codeOptions = codes.map((c) => ({ raw: c, label: `Code: ${extractCqlName(c)}` }))
    return [{ raw: '', label: '(No filter)' }, ...vsOptions, ...codeOptions]
  }, [valueSets, codes])

  const fieldOptions = useMemo(() => {
    return fhirResourceProperties[resourceType] || []
  }, [resourceType])

  const handleResourceChange = (rt: string) => {
    setResourceType(rt)
    if (!aliasEdited) setAlias(generateAlias(rt))
    if (!nameEdited) setDefinitionName(generateDefName(rt, terminology))
  }

  const handleTerminologyChange = (raw: string) => {
    setTerminology(raw)
    if (!nameEdited) setDefinitionName(generateDefName(resourceType, raw))
  }

  // Where clause helpers
  const addWhereClause = () => {
    setWhereClauses((prev) => [
      ...prev,
      { id: nextClauseId(), field: '', operator: '=', value: '', conjunction: 'and' },
    ])
  }

  const updateClause = (id: string, updates: Partial<WhereClause>) => {
    setWhereClauses((prev) =>
      prev.map((c) => (c.id === id ? { ...c, ...updates } : c))
    )
  }

  const removeClause = (id: string) => {
    setWhereClauses((prev) => prev.filter((c) => c.id !== id))
  }

  // With/without clause helpers
  const addWithClause = () => {
    setWithClauses((prev) => [
      ...prev,
      { id: nextClauseId(), mode: 'with', resourceType: 'Condition', alias: 'C', suchThat: '' },
    ])
  }

  const updateWithClause = (id: string, updates: Partial<WithClause>) => {
    setWithClauses((prev) =>
      prev.map((w) => {
        if (w.id !== id) return w
        const updated = { ...w, ...updates }
        // Auto-update alias when resource type changes (unless user set suchThat already)
        if (updates.resourceType && !w.suchThat.trim()) {
          updated.alias = generateAlias(updates.resourceType)
        }
        return updated
      })
    )
  }

  const removeWithClause = (id: string) => {
    setWithClauses((prev) => prev.filter((w) => w.id !== id))
  }

  // Let binding helpers
  const addLetBinding = () => {
    setLetBindings((prev) => [
      ...prev,
      { id: nextClauseId(), name: '', expression: '' },
    ])
  }

  const updateLetBinding = (id: string, updates: Partial<LetBinding>) => {
    setLetBindings((prev) =>
      prev.map((b) => (b.id === id ? { ...b, ...updates } : b))
    )
  }

  const removeLetBinding = (id: string) => {
    setLetBindings((prev) => prev.filter((b) => b.id !== id))
  }

  const cqlPreview = useMemo(() => generateQueryCql(
    resourceType,
    terminology,
    alias,
    definitionName || 'Untitled',
    whereClauses,
    withClauses,
    letBindings,
    enableSort,
    sortField,
    sortDir,
    enableReturn,
    returnExpr,
    enableDistinct,
  ), [resourceType, terminology, alias, definitionName, whereClauses, withClauses, letBindings, enableSort, sortField, sortDir, enableReturn, returnExpr, enableDistinct])

  const handleInsert = () => {
    if (!definitionName.trim()) return
    onInsert(cqlPreview)
  }

  return (
    <Stack spacing={1}>
      <TextField
        select
        size="small"
        label={t('query.resourceType')}
        value={resourceType}
        onChange={(e) => handleResourceChange(e.target.value)}
      >
        {FHIR_RESOURCE_TYPES.map((rt) => (
          <MenuItem key={rt} value={rt}>{rt}</MenuItem>
        ))}
      </TextField>

      <TextField
        select
        size="small"
        label={t('query.terminologySource')}
        value={terminology}
        onChange={(e) => handleTerminologyChange(e.target.value)}
        SelectProps={{ displayEmpty: true }}
        InputLabelProps={{ shrink: true }}
      >
        {terminologyOptions.map((opt) => (
          <MenuItem key={opt.raw || '__none'} value={opt.raw}>
            {opt.label}
          </MenuItem>
        ))}
      </TextField>

      <Stack direction="row" spacing={1}>
        <TextField
          size="small"
          label={t('query.alias')}
          value={alias}
          onChange={(e) => { setAlias(e.target.value); setAliasEdited(true) }}
          sx={{ width: 80 }}
        />
        <TextField
          size="small"
          label={t('query.definitionName')}
          value={definitionName}
          onChange={(e) => { setDefinitionName(e.target.value); setNameEdited(true) }}
          sx={{ flex: 1 }}
        />
      </Stack>

      {/* Let Bindings */}
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('query.letBindings')}
      </Typography>
      {letBindings.map((binding) => (
        <Stack key={binding.id} direction="row" spacing={0.5} alignItems="center">
          <TextField
            size="small"
            label={t('query.letName')}
            value={binding.name}
            onChange={(e) => updateLetBinding(binding.id, { name: e.target.value })}
            sx={{ flex: 1 }}
            placeholder="varName"
          />
          <Typography variant="body2" color="text.secondary" sx={{ px: 0.5 }}>:</Typography>
          <TextField
            size="small"
            label={t('query.letExpression')}
            value={binding.expression}
            onChange={(e) => updateLetBinding(binding.id, { expression: e.target.value })}
            sx={{ flex: 3, '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
            placeholder={t('query.letExpressionPlaceholder', { alias })}
          />
          <IconButton size="small" onClick={() => removeLetBinding(binding.id)} aria-label="Remove let">
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Stack>
      ))}
      <Button size="small" startIcon={<AddIcon />} onClick={addLetBinding} sx={{ alignSelf: 'flex-start' }}>
        {t('query.addLetBinding')}
      </Button>

      {/* With / Without Clauses */}
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('query.withClauses')}
      </Typography>
      {withClauses.map((wc) => (
        <Stack key={wc.id} spacing={0.5}>
          <Stack direction="row" spacing={0.5} alignItems="center">
            <ToggleButtonGroup
              size="small"
              exclusive
              value={wc.mode}
              onChange={(_, v) => { if (v) updateWithClause(wc.id, { mode: v }) }}
            >
              <ToggleButton value="with" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
                {t('query.with')}
              </ToggleButton>
              <ToggleButton value="without" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
                {t('query.without')}
              </ToggleButton>
            </ToggleButtonGroup>
            <TextField
              select
              size="small"
              label={t('query.resourceType')}
              value={wc.resourceType}
              onChange={(e) => updateWithClause(wc.id, { resourceType: e.target.value, alias: generateAlias(e.target.value) })}
              sx={{ flex: 2 }}
            >
              {FHIR_RESOURCE_TYPES.map((rt) => (
                <MenuItem key={rt} value={rt}>{rt}</MenuItem>
              ))}
            </TextField>
            <TextField
              size="small"
              label={t('query.alias')}
              value={wc.alias}
              onChange={(e) => updateWithClause(wc.id, { alias: e.target.value })}
              sx={{ width: 60 }}
            />
            <IconButton size="small" onClick={() => removeWithClause(wc.id)} aria-label="Remove with">
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
          <TextField
            size="small"
            label={t('query.suchThat')}
            value={wc.suchThat}
            onChange={(e) => updateWithClause(wc.id, { suchThat: e.target.value })}
            placeholder={t('query.suchThatPlaceholder', { alias, withAlias: wc.alias })}
            sx={{ ml: 2, '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
          />
        </Stack>
      ))}
      <Button size="small" startIcon={<AddIcon />} onClick={addWithClause} sx={{ alignSelf: 'flex-start' }}>
        {t('query.addWithClause')}
      </Button>

      <Divider />

      {/* Where Clauses */}
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('query.whereClauses')}
      </Typography>
      {whereClauses.map((clause, idx) => (
        <Stack key={clause.id} spacing={0.5}>
          {idx > 0 && (
            <ToggleButtonGroup
              size="small"
              exclusive
              value={clause.conjunction}
              onChange={(_, v) => { if (v) updateClause(clause.id, { conjunction: v }) }}
              sx={{ alignSelf: 'flex-start' }}
            >
              <ToggleButton value="and" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
                {t('query.and')}
              </ToggleButton>
              <ToggleButton value="or" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
                {t('query.or')}
              </ToggleButton>
            </ToggleButtonGroup>
          )}
          <Stack direction="row" spacing={0.5} alignItems="center">
            <TextField
              select
              size="small"
              label={t('query.field')}
              value={clause.field}
              onChange={(e) => updateClause(clause.id, { field: e.target.value })}
              sx={{ flex: 2 }}
            >
              {fieldOptions.map((f) => (
                <MenuItem key={f.name} value={f.name}>{f.name}</MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label={t('query.op')}
              value={clause.operator}
              onChange={(e) => updateClause(clause.id, { operator: e.target.value })}
              sx={{ flex: 1, minWidth: 80 }}
            >
              {OPERATORS.map((op) => (
                <MenuItem key={op} value={op}>{op}</MenuItem>
              ))}
            </TextField>
            {clause.operator !== 'is null' && clause.operator !== 'is not null' && (
              clause.operator === 'in' || clause.operator === 'includes concept' ? (
                <TextField
                  select
                  size="small"
                  label={t('query.value')}
                  value={clause.value}
                  onChange={(e) => updateClause(clause.id, { value: e.target.value })}
                  sx={{ flex: 2 }}
                  SelectProps={{ displayEmpty: true }}
                  InputLabelProps={{ shrink: true }}
                >
                  <MenuItem value="" disabled>
                    <em>{t('query.selectOp')}</em>
                  </MenuItem>
                  {terminologyOptions.filter((o) => o.raw).map((opt) => (
                    <MenuItem key={opt.raw} value={`"${extractCqlName(opt.raw)}"`}>
                      {opt.label}
                    </MenuItem>
                  ))}
                </TextField>
              ) : (
                <TextField
                  size="small"
                  label={t('query.value')}
                  value={clause.value}
                  onChange={(e) => updateClause(clause.id, { value: e.target.value })}
                  sx={{ flex: 2 }}
                  placeholder={t('query.valuePlaceholder')}
                />
              )
            )}
            <IconButton size="small" onClick={() => removeClause(clause.id)} aria-label="Remove clause">
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
        </Stack>
      ))}
      <Button size="small" startIcon={<AddIcon />} onClick={addWhereClause} sx={{ alignSelf: 'flex-start' }}>
        {t('query.addWhereClause')}
      </Button>

      {/* Sort */}
      <FormControlLabel
        control={<Checkbox size="small" checked={enableSort} onChange={(e) => setEnableSort(e.target.checked)} />}
        label={<Typography variant="body2">{t('query.sort')}</Typography>}
      />
      {enableSort && (
        <Stack direction="row" spacing={1}>
          <TextField
            select
            size="small"
            label={t('query.sortField')}
            value={sortField}
            onChange={(e) => setSortField(e.target.value)}
            sx={{ flex: 1 }}
          >
            {fieldOptions.map((f) => (
              <MenuItem key={f.name} value={f.name}>{f.name}</MenuItem>
            ))}
          </TextField>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={sortDir}
            onChange={(_, v) => { if (v) setSortDir(v) }}
          >
            <ToggleButton value="asc" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('query.asc')}
            </ToggleButton>
            <ToggleButton value="desc" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('query.desc')}
            </ToggleButton>
          </ToggleButtonGroup>
        </Stack>
      )}

      {/* Return + Distinct */}
      <Stack direction="row" spacing={1} alignItems="center">
        <FormControlLabel
          control={<Checkbox size="small" checked={enableReturn} onChange={(e) => setEnableReturn(e.target.checked)} />}
          label={<Typography variant="body2">{t('query.returnExpression')}</Typography>}
        />
        <FormControlLabel
          control={<Checkbox size="small" checked={enableDistinct} onChange={(e) => setEnableDistinct(e.target.checked)} />}
          label={<Typography variant="body2">{t('query.distinct')}</Typography>}
        />
      </Stack>
      {enableReturn && (
        <TextField
          size="small"
          label={t('query.returnExpression')}
          value={returnExpr}
          onChange={(e) => setReturnExpr(e.target.value)}
          placeholder={t('query.returnExpressionPlaceholder', { alias })}
          sx={{ '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
        />
      )}

      {/* CQL Preview */}
      <CqlPreviewBox code={cqlPreview} />

      {/* Actions */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton onClick={handleInsert} disabled={!definitionName.trim()}>
          {t('common.insert')}
        </GradientButton>
        <Tooltip title={t('common.copyToClipboard')}>
          <IconButton size="small" onClick={() => copyToClipboard(cqlPreview)} aria-label={t('common.copyToClipboard')}>
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
      </Stack>
    </Stack>
  )
}
