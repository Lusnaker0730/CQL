import { useState, useMemo } from 'react'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  Box,
  Button,
  IconButton,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
  FormControlLabel,
  Checkbox,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Add as AddIcon,
  ContentCopy as CopyIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useNotification } from '../../hooks/useNotification'
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

const RESOURCE_TYPES = [
  'Observation',
  'Condition',
  'Procedure',
  'MedicationRequest',
  'MedicationStatement',
  'Encounter',
  'Immunization',
  'AllergyIntolerance',
  'Device',
  'ServiceRequest',
] as const

const OPERATORS = [
  '=', '!=', '~', 'in', 'during', 'before', 'after',
  'contains', '>', '<', '>=', '<=', 'is not null', 'is null',
]

function extractName(raw: string): string {
  const m = raw.match(/^"([^"]+)"/)
  return m ? m[1] : raw
}

let clauseIdCounter = 0
function nextClauseId(): string {
  return `wc_${++clauseIdCounter}`
}

function generateAlias(resourceType: string): string {
  return resourceType.charAt(0)
}

function generateDefName(resourceType: string, terminology: string): string {
  if (!terminology) return resourceType + ' Query'
  const termName = extractName(terminology)
  return `${termName} ${resourceType}s`
}

function generateQueryCql(
  resourceType: string,
  terminology: string,
  alias: string,
  definitionName: string,
  whereClauses: WhereClause[],
  enableSort: boolean,
  sortField: string,
  sortDir: 'asc' | 'desc',
  enableReturn: boolean,
  returnExpr: string,
): string {
  const termPart = terminology ? `: "${extractName(terminology)}"` : ''
  let cql = `define "${definitionName}":\n  [${resourceType}${termPart}] ${alias}`

  const validClauses = whereClauses.filter((c) => c.field && c.operator)
  if (validClauses.length > 0) {
    validClauses.forEach((clause, idx) => {
      const prefix = idx === 0 ? '\n    where ' : `\n      ${clause.conjunction} `
      let condition = `${alias}.${clause.field} ${clause.operator}`
      if (clause.operator !== 'is null' && clause.operator !== 'is not null') {
        condition += ` ${clause.value || "'...'"}`
      }
      cql += `${prefix}${condition}`
    })
  }

  if (enableReturn && returnExpr.trim()) {
    cql += `\n    return ${returnExpr}`
  }

  if (enableSort && sortField) {
    cql += `\n    sort by ${sortField} ${sortDir}`
  }

  return cql
}

export default function QueryBuilder({ valueSets, codes, onInsert, onCancel }: QueryBuilderProps) {
  const { showNotification } = useNotification()
  const [resourceType, setResourceType] = useState<string>('Observation')
  const [terminology, setTerminology] = useState('')
  const [alias, setAlias] = useState('O')
  const [aliasEdited, setAliasEdited] = useState(false)
  const [definitionName, setDefinitionName] = useState('Observation Query')
  const [nameEdited, setNameEdited] = useState(false)
  const [whereClauses, setWhereClauses] = useState<WhereClause[]>([])
  const [enableSort, setEnableSort] = useState(false)
  const [sortField, setSortField] = useState('')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc')
  const [enableReturn, setEnableReturn] = useState(false)
  const [returnExpr, setReturnExpr] = useState('')

  const terminologyOptions = useMemo(() => {
    const vsOptions = valueSets.map((vs) => ({ raw: vs, label: `VS: ${extractName(vs)}` }))
    const codeOptions = codes.map((c) => ({ raw: c, label: `Code: ${extractName(c)}` }))
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

  const cqlPreview = generateQueryCql(
    resourceType,
    terminology,
    alias,
    definitionName || 'Untitled',
    whereClauses,
    enableSort,
    sortField,
    sortDir,
    enableReturn,
    returnExpr,
  )

  const handleInsert = () => {
    if (!definitionName.trim()) return
    onInsert(cqlPreview)
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(cqlPreview)
      showNotification('Copied to clipboard', 'success', 2000)
    } catch {
      showNotification('Failed to copy', 'error', 2000)
    }
  }

  return (
    <Stack spacing={1}>
      <TextField
        select
        size="small"
        label="Resource Type"
        value={resourceType}
        onChange={(e) => handleResourceChange(e.target.value)}
      >
        {RESOURCE_TYPES.map((rt) => (
          <MenuItem key={rt} value={rt}>{rt}</MenuItem>
        ))}
      </TextField>

      <TextField
        select
        size="small"
        label="Terminology Source"
        value={terminology}
        onChange={(e) => handleTerminologyChange(e.target.value)}
        SelectProps={{ displayEmpty: true }}
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
          label="Alias"
          value={alias}
          onChange={(e) => { setAlias(e.target.value); setAliasEdited(true) }}
          sx={{ width: 80 }}
        />
        <TextField
          size="small"
          label="Definition Name"
          value={definitionName}
          onChange={(e) => { setDefinitionName(e.target.value); setNameEdited(true) }}
          sx={{ flex: 1 }}
        />
      </Stack>

      {/* Where Clauses */}
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        Where Clauses
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
                AND
              </ToggleButton>
              <ToggleButton value="or" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
                OR
              </ToggleButton>
            </ToggleButtonGroup>
          )}
          <Stack direction="row" spacing={0.5} alignItems="center">
            <TextField
              select
              size="small"
              label="Field"
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
              label="Op"
              value={clause.operator}
              onChange={(e) => updateClause(clause.id, { operator: e.target.value })}
              sx={{ flex: 1, minWidth: 80 }}
            >
              {OPERATORS.map((op) => (
                <MenuItem key={op} value={op}>{op}</MenuItem>
              ))}
            </TextField>
            {clause.operator !== 'is null' && clause.operator !== 'is not null' && (
              clause.operator === 'in' ? (
                <TextField
                  select
                  size="small"
                  label="Value"
                  value={clause.value}
                  onChange={(e) => updateClause(clause.id, { value: e.target.value })}
                  sx={{ flex: 2 }}
                  SelectProps={{ displayEmpty: true }}
                >
                  <MenuItem value="" disabled>
                    <em>Select...</em>
                  </MenuItem>
                  {terminologyOptions.filter((o) => o.raw).map((opt) => (
                    <MenuItem key={opt.raw} value={`"${extractName(opt.raw)}"`}>
                      {opt.label}
                    </MenuItem>
                  ))}
                </TextField>
              ) : (
                <TextField
                  size="small"
                  label="Value"
                  value={clause.value}
                  onChange={(e) => updateClause(clause.id, { value: e.target.value })}
                  sx={{ flex: 2 }}
                  placeholder="e.g. 'active'"
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
        Add Where Clause
      </Button>

      {/* Sort */}
      <FormControlLabel
        control={<Checkbox size="small" checked={enableSort} onChange={(e) => setEnableSort(e.target.checked)} />}
        label={<Typography variant="body2">Sort</Typography>}
      />
      {enableSort && (
        <Stack direction="row" spacing={1}>
          <TextField
            select
            size="small"
            label="Sort Field"
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
              asc
            </ToggleButton>
            <ToggleButton value="desc" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              desc
            </ToggleButton>
          </ToggleButtonGroup>
        </Stack>
      )}

      {/* Return */}
      <FormControlLabel
        control={<Checkbox size="small" checked={enableReturn} onChange={(e) => setEnableReturn(e.target.checked)} />}
        label={<Typography variant="body2">Return Expression</Typography>}
      />
      {enableReturn && (
        <TextField
          size="small"
          label="Return Expression"
          value={returnExpr}
          onChange={(e) => setReturnExpr(e.target.value)}
          placeholder={`e.g. ${alias}.value`}
          sx={{ '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
        />
      )}

      {/* CQL Preview */}
      <Box
        sx={{
          p: 1,
          bgcolor: 'rgba(27,58,92,0.04)',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          fontFamily: 'monospace',
          fontSize: '0.75rem',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          maxHeight: 120,
          overflow: 'auto',
        }}
      >
        {cqlPreview}
      </Box>

      {/* Actions */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton onClick={handleInsert} disabled={!definitionName.trim()}>
          Insert
        </GradientButton>
        <Tooltip title="Copy to clipboard">
          <IconButton size="small" onClick={handleCopy} aria-label="Copy to clipboard">
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Button size="small" onClick={onCancel}>Cancel</Button>
      </Stack>
    </Stack>
  )
}
