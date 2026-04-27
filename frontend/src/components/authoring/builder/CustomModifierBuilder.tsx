import { useState, useCallback, useMemo } from 'react'
import {
  Box, Stack, Typography, Button, IconButton, Select, MenuItem, TextField,
  FormControl, InputLabel, Dialog, DialogTitle, DialogContent, DialogActions,
  Chip,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  Add as AddIcon, Delete as DeleteIcon, Close as CloseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { Modifier } from '../../../types/authoring'
import { generateId } from '../../../utils/validation'
import { useQueryBuilderResources, useQueryBuilderOperators } from '../../../hooks/useCqlImport'
import { CONJUNCTION_COLOR_AND, CONJUNCTION_COLOR_OR } from '../../../constants/authoringConstants'
import { escapeCqlString, escapeCqlIdentifier } from '../../../utils/cqlString'

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?)?$/
const DURATION_NUM_RE = /^\d+(\.\d+)?$/
const ALLOWED_DURATION_UNITS = new Set(['year', 'years', 'month', 'months', 'week', 'weeks', 'day', 'days', 'hour', 'hours', 'minute', 'minutes'])

/** A single rule: field → operator → value */
interface ModifierRule {
  id: string
  field: string
  operator: string
  value: string
}

/** A group of rules combined with AND/OR */
interface ModifierRuleGroup {
  id: string
  conjunction: 'AND' | 'OR'
  rules: ModifierRule[]
  groups: ModifierRuleGroup[]
}

// Fallback operators when API data isn't available yet. Labels resolve via
// i18n at use site so the dropdown isn't English-only on first paint.
const FALLBACK_OPERATORS_BY_TYPE: Record<string, Array<{ op: string; labelKey?: string; label?: string }>> = {
  code: [
    { op: 'equals', labelKey: 'customModifier.ops.equals' },
    { op: 'not_equals', labelKey: 'customModifier.ops.notEquals' },
    { op: 'in', labelKey: 'customModifier.ops.in' },
    { op: 'is_null', labelKey: 'customModifier.ops.isNull' },
    { op: 'is_not_null', labelKey: 'customModifier.ops.isNotNull' },
  ],
  string: [
    { op: 'equals', labelKey: 'customModifier.ops.equals' },
    { op: 'not_equals', labelKey: 'customModifier.ops.notEquals' },
    { op: 'starts_with', labelKey: 'customModifier.ops.startsWith' },
    { op: 'ends_with', labelKey: 'customModifier.ops.endsWith' },
    { op: 'contains', labelKey: 'customModifier.ops.contains' },
    { op: 'is_null', labelKey: 'customModifier.ops.isNull' },
  ],
  decimal: [
    { op: 'equals', label: '=' },
    { op: 'not_equals', label: '!=' },
    { op: 'gt', label: '>' },
    { op: 'gte', label: '>=' },
    { op: 'lt', label: '<' },
    { op: 'lte', label: '<=' },
    { op: 'is_null', labelKey: 'customModifier.ops.isNull' },
  ],
  dateTime: [
    { op: 'before', labelKey: 'customModifier.ops.before' },
    { op: 'after', labelKey: 'customModifier.ops.after' },
    { op: 'within_last', labelKey: 'customModifier.ops.withinLast' },
    { op: 'is_null', labelKey: 'customModifier.ops.isNull' },
    { op: 'is_not_null', labelKey: 'customModifier.ops.isNotNull' },
  ],
}

/** Convert "list_of_xxx" inputType to FHIR resource name, e.g. list_of_conditions → Condition */
function inputTypeToResourceName(inputType: string): string | null {
  const m = inputType.match(/^list_of_(.+)$/)
  if (!m) return null
  const snake = m[1]
  // Handle common plurals: conditions→Condition, observations→Observation, etc.
  const OVERRIDES: Record<string, string> = {
    allergy_intolerances: 'AllergyIntolerance',
    medication_requests: 'MedicationRequest',
    medication_statements: 'MedicationStatement',
    service_requests: 'ServiceRequest',
    diagnostic_reports: 'DiagnosticReport',
  }
  if (OVERRIDES[snake]) return OVERRIDES[snake]
  // Default: strip trailing 's', PascalCase
  const singular = snake.endsWith('s') ? snake.slice(0, -1) : snake
  return singular.charAt(0).toUpperCase() + singular.slice(1)
}

/** Capitalize first letter of each word for labels: "clinicalStatus" → "Clinical Status" */
function toLabel(name: string): string {
  return name.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()).trim()
}

function createEmptyRule(): ModifierRule {
  return { id: generateId(), field: '', operator: '', value: '' }
}

function createEmptyGroup(): ModifierRuleGroup {
  return { id: generateId(), conjunction: 'AND', rules: [createEmptyRule()], groups: [] }
}

function isRuleComplete(rule: ModifierRule): boolean {
  if (!rule.field || !rule.operator) return false
  if (['is_null', 'is_not_null'].includes(rule.operator)) return true
  return !!rule.value
}

function isGroupValid(group: ModifierRuleGroup): boolean {
  return (
    group.rules.every(isRuleComplete) &&
    group.groups.every(isGroupValid) &&
    (group.rules.length > 0 || group.groups.length > 0)
  )
}

interface CustomModifierBuilderProps {
  open: boolean
  onClose: () => void
  inputType: string
  onAdd: (modifier: Modifier) => void
}

export default function CustomModifierBuilder({
  open,
  onClose,
  inputType,
  onAdd,
}: CustomModifierBuilderProps) {
  const { t } = useTranslation('authoring')
  const [rootGroup, setRootGroup] = useState<ModifierRuleGroup>(createEmptyGroup())
  const { data: apiResources } = useQueryBuilderResources()
  const { data: apiOperators } = useQueryBuilderOperators()

  // Derive field list and code value options from API resources
  const { availableFields, codeValueOptions } = useMemo(() => {
    const resourceName = inputTypeToResourceName(inputType)
    if (!resourceName || !apiResources) return { availableFields: [] as Array<{ field: string; label: string; type: string }>, codeValueOptions: {} as Record<string, string[]> }
    const resource = apiResources.find((r) => r.name === resourceName)
    if (!resource) return { availableFields: [] as Array<{ field: string; label: string; type: string }>, codeValueOptions: {} as Record<string, string[]> }
    const fields = resource.properties.map((p) => ({
      field: p.name,
      label: toLabel(p.name),
      type: p.type === 'CodeableConcept' || p.type === 'Coding' ? 'code' : p.type === 'Quantity' ? 'decimal' : p.type === 'Period' ? 'dateTime' : p.type,
    }))
    const codeOpts: Record<string, string[]> = {}
    for (const p of resource.properties) {
      if (p.values && p.values.length > 0) {
        codeOpts[p.name] = p.values
      }
    }
    return { availableFields: fields, codeValueOptions: codeOpts }
  }, [inputType, apiResources])

  // Derive operators grouped by type from API
  const operatorsByType = useMemo(() => {
    if (!apiOperators || apiOperators.length === 0) return FALLBACK_OPERATORS_BY_TYPE
    const grouped: Record<string, Array<{ op: string; label: string }>> = {}
    for (const op of apiOperators) {
      for (const opType of op.applicableTypes) {
        const normalizedType = opType === 'CodeableConcept' || opType === 'Coding' ? 'code' : opType === 'Quantity' ? 'decimal' : opType === 'Period' ? 'dateTime' : opType
        if (!grouped[normalizedType]) grouped[normalizedType] = []
        if (!grouped[normalizedType].some((o) => o.op === op.id)) {
          grouped[normalizedType].push({ op: op.id, label: op.label })
        }
      }
    }
    // Add custom operators not in backend (within_last for dateTime, ends_with for string)
    if (grouped.dateTime && !grouped.dateTime.some((o) => o.op === 'within_last')) {
      grouped.dateTime.push({ op: 'within_last', label: t('customModifier.ops.withinLast') })
    }
    if (grouped.string && !grouped.string.some((o) => o.op === 'ends_with')) {
      grouped.string.splice(3, 0, { op: 'ends_with', label: t('customModifier.ops.endsWith') })
    }
    // Add decimal-specific operators (gt/gte/lt/lte aliases for > >= < <=)
    if (grouped.decimal) {
      const aliases = [
        { op: 'gt', src: 'greater_than', label: '>' },
        { op: 'gte', src: 'greater_or_equal', label: '>=' },
        { op: 'lt', src: 'less_than', label: '<' },
        { op: 'lte', src: 'less_or_equal', label: '<=' },
      ]
      for (const a of aliases) {
        if (!grouped.decimal.some((o) => o.op === a.op)) {
          const idx = grouped.decimal.findIndex((o) => o.op === a.src)
          if (idx >= 0) grouped.decimal[idx] = { op: a.op, label: a.label }
        }
      }
    }
    return grouped
  }, [apiOperators, t])

  const handleReset = () => {
    setRootGroup(createEmptyGroup())
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  const getOperatorsForType = useCallback((type: string) => {
    const raw = operatorsByType[type] || operatorsByType.string || FALLBACK_OPERATORS_BY_TYPE.string
    // Resolve labelKey → translated label at lookup time so the dropdown
    // never renders raw English when API data isn't loaded yet.
    return raw.map((entry) => {
      if ('labelKey' in entry && entry.labelKey) return { op: entry.op, label: t(entry.labelKey) }
      return { op: entry.op, label: entry.label || entry.op }
    })
  }, [operatorsByType, t])

  const handleAdd = () => {
    if (!isGroupValid(rootGroup)) return

    // Build a custom modifier from the rules
    const cqlWhere = buildCqlWhereClause(rootGroup, availableFields)
    const modifier: Modifier = {
      id: `custom_${generateId()}`,
      name: t('customModifier.defaultName'),
      inputTypes: [inputType],
      returnType: inputType,
      cqlTemplate: `({expression}).where(${cqlWhere})`,
      values: { rules: rootGroup as unknown as Record<string, unknown> },
    }
    onAdd(modifier)
    handleReset()
  }

  const isValid = isGroupValid(rootGroup) && availableFields.length > 0

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        {t('customModifier.title')}
        <IconButton onClick={handleClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {availableFields.length === 0 ? (
          <Typography color="text.secondary" sx={{ py: 2 }}>
            {t('customModifier.notAvailable', { type: inputType.replace(/_/g, ' ') })}
          </Typography>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary" mb={2}>
              {t('customModifier.description')}
            </Typography>
            <RuleGroupEditor
              group={rootGroup}
              availableFields={availableFields}
              getOperatorsForType={getOperatorsForType}
              codeValueOptions={codeValueOptions}
              onChange={setRootGroup}
              depth={0}
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('common:actions.cancel')}</Button>
        <Button variant="contained" onClick={handleAdd} disabled={!isValid}>
          {t('common:actions.add')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// ---------- RuleGroupEditor ----------

interface RuleGroupEditorProps {
  group: ModifierRuleGroup
  availableFields: Array<{ field: string; label: string; type: string }>
  getOperatorsForType: (type: string) => Array<{ op: string; label: string }>
  codeValueOptions: Record<string, string[]>
  onChange: (updated: ModifierRuleGroup) => void
  depth: number
}

function RuleGroupEditor({ group, availableFields, getOperatorsForType, codeValueOptions, onChange, depth }: RuleGroupEditorProps) {
  const { t } = useTranslation('authoring')
  const handleToggleConjunction = () => {
    onChange({ ...group, conjunction: group.conjunction === 'AND' ? 'OR' : 'AND' })
  }

  const handleAddRule = () => {
    onChange({ ...group, rules: [...group.rules, createEmptyRule()] })
  }

  const handleAddGroup = () => {
    onChange({ ...group, groups: [...group.groups, createEmptyGroup()] })
  }

  const handleUpdateRule = useCallback(
    (ruleId: string, updates: Partial<ModifierRule>) => {
      onChange({
        ...group,
        rules: group.rules.map((r) => (r.id === ruleId ? { ...r, ...updates } : r)),
      })
    },
    [group, onChange]
  )

  const handleRemoveRule = (ruleId: string) => {
    onChange({ ...group, rules: group.rules.filter((r) => r.id !== ruleId) })
  }

  const handleUpdateSubgroup = (groupId: string, updated: ModifierRuleGroup) => {
    onChange({
      ...group,
      groups: group.groups.map((g) => (g.id === groupId ? updated : g)),
    })
  }

  const handleRemoveSubgroup = (groupId: string) => {
    onChange({ ...group, groups: group.groups.filter((g) => g.id !== groupId) })
  }

  const borderColor = group.conjunction === 'AND' ? CONJUNCTION_COLOR_AND : CONJUNCTION_COLOR_OR

  return (
    <Box
      sx={{
        border: depth > 0 ? `2px solid ${borderColor}` : 'none',
        borderRadius: 2,
        p: depth > 0 ? 2 : 0,
        ml: depth > 0 ? 2 : 0,
        backgroundColor: depth > 0 ? 'action.hover' : 'transparent',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1} mb={1}>
        <Chip
          label={group.conjunction}
          size="small"
          onClick={handleToggleConjunction}
          sx={{
            backgroundColor: borderColor,
            color: 'white',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        />
        <Button size="small" startIcon={<AddIcon />} onClick={handleAddRule}>
          {t('customModifier.addRule')}
        </Button>
        <Button size="small" startIcon={<AddIcon />} onClick={handleAddGroup}>
          {t('customModifier.addGroup')}
        </Button>
      </Stack>

      <Stack spacing={1}>
        {group.rules.map((rule) => (
          <RuleEditor
            key={rule.id}
            rule={rule}
            availableFields={availableFields}
            getOperatorsForType={getOperatorsForType}
            codeValueOptions={codeValueOptions}
            onChange={(updates) => handleUpdateRule(rule.id, updates)}
            onRemove={() => handleRemoveRule(rule.id)}
          />
        ))}

        {group.groups.map((subGroup) => (
          <Box key={subGroup.id} sx={{ position: 'relative' }}>
            <RuleGroupEditor
              group={subGroup}
              availableFields={availableFields}
              getOperatorsForType={getOperatorsForType}
              codeValueOptions={codeValueOptions}
              onChange={(updated) => handleUpdateSubgroup(subGroup.id, updated)}
              depth={depth + 1}
            />
            <IconButton
              size="small"
              color="error"
              onClick={() => handleRemoveSubgroup(subGroup.id)}
              sx={{ position: 'absolute', top: 4, right: 4 }}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Box>
        ))}
      </Stack>
    </Box>
  )
}

// ---------- RuleEditor ----------

interface RuleEditorProps {
  rule: ModifierRule
  availableFields: Array<{ field: string; label: string; type: string }>
  getOperatorsForType: (type: string) => Array<{ op: string; label: string }>
  codeValueOptions: Record<string, string[]>
  onChange: (updates: Partial<ModifierRule>) => void
  onRemove: () => void
}

function RuleEditor({ rule, availableFields, getOperatorsForType, codeValueOptions, onChange, onRemove }: RuleEditorProps) {
  const { t } = useTranslation('authoring')
  const selectedField = availableFields.find((f) => f.field === rule.field)
  const fieldType = selectedField?.type || 'string'
  const operators = getOperatorsForType(fieldType)
  const needsValue = !['is_null', 'is_not_null'].includes(rule.operator)
  const complete = isRuleComplete(rule)

  const codeOptions = rule.field ? codeValueOptions[rule.field] : undefined

  return (
    <Stack
      direction="row"
      spacing={1}
      alignItems="center"
      sx={(theme) => ({
        p: 1,
        borderRadius: 1,
        backgroundColor: complete ? 'transparent' : alpha(theme.palette.error.main, 0.08),
        border: 1,
        borderColor: complete ? 'divider' : alpha(theme.palette.error.main, 0.2),
      })}
    >
      {/* Field selector */}
      <FormControl size="small" sx={{ minWidth: 160 }}>
        <InputLabel>{t('customModifier.propertyLabel')}</InputLabel>
        <Select
          value={rule.field}
          label={t('customModifier.propertyLabel')}
          onChange={(e) => onChange({ field: e.target.value, operator: '', value: '' })}
        >
          {availableFields.map((f) => (
            <MenuItem key={f.field} value={f.field}>{f.label}</MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Operator selector */}
      {rule.field && (
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>{t('customModifier.operatorLabel')}</InputLabel>
          <Select
            value={rule.operator}
            label={t('customModifier.operatorLabel')}
            onChange={(e) => onChange({ operator: e.target.value, value: '' })}
          >
            {operators.map((op) => (
              <MenuItem key={op.op} value={op.op}>{op.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
      )}

      {/* Value input */}
      {rule.field && rule.operator && needsValue && (
        codeOptions ? (
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>{t('customModifier.valueLabel')}</InputLabel>
            <Select
              value={rule.value}
              label={t('customModifier.valueLabel')}
              onChange={(e) => onChange({ value: e.target.value })}
            >
              {codeOptions.map((v) => (
                <MenuItem key={v} value={v}>{v}</MenuItem>
              ))}
            </Select>
          </FormControl>
        ) : fieldType === 'dateTime' && rule.operator === 'within_last' ? (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <TextField
              size="small"
              type="number"
              label={t('customModifier.valueLabel')}
              value={rule.value.split(' ')[0] || ''}
              onChange={(e) => onChange({ value: `${e.target.value} ${rule.value.split(' ')[1] || 'days'}` })}
              sx={{ width: 80 }}
            />
            <FormControl size="small" sx={{ minWidth: 80 }}>
              <Select
                value={rule.value.split(' ')[1] || 'days'}
                onChange={(e) => onChange({ value: `${rule.value.split(' ')[0] || ''} ${e.target.value}` })}
              >
                <MenuItem value="days">{t('customModifier.days')}</MenuItem>
                <MenuItem value="weeks">{t('customModifier.weeks')}</MenuItem>
                <MenuItem value="months">{t('customModifier.months')}</MenuItem>
                <MenuItem value="years">{t('customModifier.years')}</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        ) : (
          <TextField
            size="small"
            label={t('customModifier.valueLabel')}
            value={rule.value}
            onChange={(e) => onChange({ value: e.target.value })}
            type={fieldType === 'decimal' ? 'number' : 'text'}
            sx={{ minWidth: 120 }}
          />
        )
      )}

      <Box sx={{ flex: 1 }} />
      <IconButton size="small" color="error" onClick={onRemove} aria-label={t('customModifier.removeRule')}>
        <DeleteIcon fontSize="small" />
      </IconButton>
    </Stack>
  )
}

// ---------- CQL Generation ----------

function buildCqlWhereClause(
  group: ModifierRuleGroup,
  fields: Array<{ field: string; label: string; type: string }>
): string {
  const parts: string[] = []

  for (const rule of group.rules) {
    const clause = buildRuleClause(rule, fields)
    if (clause) parts.push(clause)
  }

  for (const sub of group.groups) {
    const subClause = buildCqlWhereClause(sub, fields)
    if (subClause) parts.push(`(${subClause})`)
  }

  const op = group.conjunction === 'AND' ? ' and ' : ' or '
  return parts.join(op)
}

function buildRuleClause(
  rule: ModifierRule,
  fields: Array<{ field: string; label: string; type: string }>
): string | null {
  if (!rule.field || !rule.operator) return null
  const fieldDef = fields.find((f) => f.field === rule.field)
  if (!fieldDef) return null

  // Build a safe accessor from the field path. The path comes from the
  // resource metadata API but we still split on `.` and apply identifier
  // rules so a hostile/typo'd path can't inject CQL.
  const accessor = rule.field
    .split('.')
    .map((part) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(part) ? part : `"${escapeCqlIdentifier(part)}"`)
    .join('.')

  const numericLiteral = (raw: string): string | null => {
    const v = raw.trim()
    return /^-?\d+(\.\d+)?$/.test(v) ? v : null
  }

  switch (rule.operator) {
    case 'is_null': return `${accessor} is null`
    case 'is_not_null': return `${accessor} is not null`
    case 'equals':
      if (fieldDef.type === 'code') return `${accessor} ~ '${escapeCqlString(rule.value)}'`
      if (fieldDef.type === 'decimal') {
        const n = numericLiteral(rule.value)
        return n === null ? null : `${accessor} = ${n}`
      }
      return `${accessor} = '${escapeCqlString(rule.value)}'`
    case 'not_equals':
      if (fieldDef.type === 'code') return `${accessor} !~ '${escapeCqlString(rule.value)}'`
      if (fieldDef.type === 'decimal') {
        const n = numericLiteral(rule.value)
        return n === null ? null : `${accessor} != ${n}`
      }
      return `${accessor} != '${escapeCqlString(rule.value)}'`
    case 'gt':
    case 'gte':
    case 'lt':
    case 'lte': {
      const n = numericLiteral(rule.value)
      if (n === null) return null
      const sym = rule.operator === 'gt' ? '>' : rule.operator === 'gte' ? '>=' : rule.operator === 'lt' ? '<' : '<='
      return `${accessor} ${sym} ${n}`
    }
    case 'starts_with': return `StartsWith(${accessor}, '${escapeCqlString(rule.value)}')`
    case 'ends_with': return `EndsWith(${accessor}, '${escapeCqlString(rule.value)}')`
    case 'contains': return `PositionOf('${escapeCqlString(rule.value)}', ${accessor}) >= 0`
    case 'before':
    case 'after': {
      // CQL date literal `@YYYY-MM-DD[Thh:mm[:ss[.fff]]]` — reject anything else
      // so we don't paste arbitrary user text after the `@` sigil.
      if (!ISO_DATE_RE.test(rule.value)) return null
      return `${accessor} ${rule.operator} @${rule.value}`
    }
    case 'within_last': {
      const [num, unit] = rule.value.split(' ')
      if (!num || !unit) return null
      if (!DURATION_NUM_RE.test(num) || !ALLOWED_DURATION_UNITS.has(unit)) return null
      return `${accessor} >= Now() - ${num} ${unit}`
    }
    case 'in':
      // The value is a value-set name picked from a list — re-quote safely.
      return `${accessor} in "${escapeCqlIdentifier(rule.value)}"`
    default: return null
  }
}
