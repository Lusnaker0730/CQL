import { useState, useCallback } from 'react'
import {
  Box, Stack, Typography, Button, IconButton, Select, MenuItem, TextField,
  FormControl, InputLabel, Dialog, DialogTitle, DialogContent, DialogActions,
  Chip,
} from '@mui/material'
import {
  Add as AddIcon, Delete as DeleteIcon, Close as CloseIcon,
} from '@mui/icons-material'
import type { Modifier } from '../../../types/authoring'

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

// FHIR resource fields that can be filtered on
const FHIR_FIELD_MAP: Record<string, Array<{ field: string; label: string; type: string }>> = {
  list_of_conditions: [
    { field: 'clinicalStatus', label: 'Clinical Status', type: 'code' },
    { field: 'verificationStatus', label: 'Verification Status', type: 'code' },
    { field: 'severity', label: 'Severity', type: 'code' },
    { field: 'onsetDateTime', label: 'Onset Date', type: 'dateTime' },
    { field: 'abatementDateTime', label: 'Abatement Date', type: 'dateTime' },
  ],
  list_of_observations: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'effectiveDateTime', label: 'Effective Date', type: 'dateTime' },
    { field: 'valueQuantity.value', label: 'Value (Quantity)', type: 'decimal' },
    { field: 'valueQuantity.unit', label: 'Value Unit', type: 'string' },
    { field: 'issued', label: 'Issued Date', type: 'dateTime' },
  ],
  list_of_procedures: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'performedDateTime', label: 'Performed Date', type: 'dateTime' },
  ],
  list_of_encounters: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'class', label: 'Class', type: 'code' },
    { field: 'period.start', label: 'Period Start', type: 'dateTime' },
    { field: 'period.end', label: 'Period End', type: 'dateTime' },
  ],
  list_of_medication_requests: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'intent', label: 'Intent', type: 'code' },
    { field: 'authoredOn', label: 'Authored On', type: 'dateTime' },
  ],
  list_of_medication_statements: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'effectiveDateTime', label: 'Effective Date', type: 'dateTime' },
  ],
  list_of_allergy_intolerances: [
    { field: 'clinicalStatus', label: 'Clinical Status', type: 'code' },
    { field: 'verificationStatus', label: 'Verification Status', type: 'code' },
    { field: 'type', label: 'Type', type: 'code' },
    { field: 'criticality', label: 'Criticality', type: 'code' },
  ],
  list_of_immunizations: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'occurrenceDateTime', label: 'Occurrence Date', type: 'dateTime' },
  ],
  list_of_devices: [
    { field: 'status', label: 'Status', type: 'code' },
  ],
  list_of_service_requests: [
    { field: 'status', label: 'Status', type: 'code' },
    { field: 'intent', label: 'Intent', type: 'code' },
    { field: 'authoredOn', label: 'Authored On', type: 'dateTime' },
  ],
}

const OPERATORS_BY_TYPE: Record<string, Array<{ op: string; label: string }>> = {
  code: [
    { op: 'equals', label: 'equals' },
    { op: 'not_equals', label: 'does not equal' },
    { op: 'in', label: 'is in' },
    { op: 'is_null', label: 'is null' },
    { op: 'is_not_null', label: 'is not null' },
  ],
  string: [
    { op: 'equals', label: 'equals' },
    { op: 'not_equals', label: 'does not equal' },
    { op: 'starts_with', label: 'starts with' },
    { op: 'ends_with', label: 'ends with' },
    { op: 'contains', label: 'contains' },
    { op: 'is_null', label: 'is null' },
  ],
  decimal: [
    { op: 'equals', label: '=' },
    { op: 'not_equals', label: '!=' },
    { op: 'gt', label: '>' },
    { op: 'gte', label: '>=' },
    { op: 'lt', label: '<' },
    { op: 'lte', label: '<=' },
    { op: 'is_null', label: 'is null' },
  ],
  dateTime: [
    { op: 'before', label: 'is before' },
    { op: 'after', label: 'is after' },
    { op: 'within_last', label: 'within the last' },
    { op: 'is_null', label: 'is null' },
    { op: 'is_not_null', label: 'is not null' },
  ],
}

const CODE_VALUE_OPTIONS: Record<string, string[]> = {
  'clinicalStatus': ['active', 'recurrence', 'relapse', 'inactive', 'remission', 'resolved'],
  'verificationStatus': ['unconfirmed', 'provisional', 'differential', 'confirmed', 'refuted', 'entered-in-error'],
  'status': ['registered', 'preliminary', 'final', 'amended', 'corrected', 'cancelled', 'entered-in-error', 'unknown', 'active', 'completed', 'on-hold', 'stopped', 'draft', 'requested', 'received', 'accepted', 'in-progress', 'preparation', 'not-done'],
  'severity': ['severe', 'moderate', 'mild'],
  'criticality': ['low', 'high', 'unable-to-assess'],
  'type': ['allergy', 'intolerance'],
  'intent': ['proposal', 'plan', 'order', 'original-order', 'reflex-order', 'filler-order', 'instance-order', 'option'],
}

function generateId(): string {
  return Math.random().toString(36).slice(2, 9)
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
  const [rootGroup, setRootGroup] = useState<ModifierRuleGroup>(createEmptyGroup())

  const availableFields = FHIR_FIELD_MAP[inputType] || []

  const handleReset = () => {
    setRootGroup(createEmptyGroup())
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  const handleAdd = () => {
    if (!isGroupValid(rootGroup)) return

    // Build a custom modifier from the rules
    const cqlWhere = buildCqlWhereClause(rootGroup, availableFields)
    const modifier: Modifier = {
      id: `custom_${generateId()}`,
      name: 'Custom Filter',
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
        Build Custom Modifier
        <IconButton onClick={handleClose} size="small"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {availableFields.length === 0 ? (
          <Typography color="text.secondary" sx={{ py: 2 }}>
            Custom modifier builder is not available for the type "{inputType.replace(/_/g, ' ')}".
            Only FHIR resource list types are supported.
          </Typography>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary" mb={2}>
              Build filter rules based on FHIR resource properties. Rules with a pink background are incomplete.
            </Typography>
            <RuleGroupEditor
              group={rootGroup}
              availableFields={availableFields}
              onChange={setRootGroup}
              depth={0}
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button variant="contained" onClick={handleAdd} disabled={!isValid}>
          Add
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// ---------- RuleGroupEditor ----------

interface RuleGroupEditorProps {
  group: ModifierRuleGroup
  availableFields: Array<{ field: string; label: string; type: string }>
  onChange: (updated: ModifierRuleGroup) => void
  depth: number
}

function RuleGroupEditor({ group, availableFields, onChange, depth }: RuleGroupEditorProps) {
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

  const borderColor = group.conjunction === 'AND' ? '#0D7377' : '#E67E22'

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
          Add rule
        </Button>
        <Button size="small" startIcon={<AddIcon />} onClick={handleAddGroup}>
          Add group
        </Button>
      </Stack>

      <Stack spacing={1}>
        {group.rules.map((rule) => (
          <RuleEditor
            key={rule.id}
            rule={rule}
            availableFields={availableFields}
            onChange={(updates) => handleUpdateRule(rule.id, updates)}
            onRemove={() => handleRemoveRule(rule.id)}
          />
        ))}

        {group.groups.map((subGroup) => (
          <Box key={subGroup.id} sx={{ position: 'relative' }}>
            <RuleGroupEditor
              group={subGroup}
              availableFields={availableFields}
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
  onChange: (updates: Partial<ModifierRule>) => void
  onRemove: () => void
}

function RuleEditor({ rule, availableFields, onChange, onRemove }: RuleEditorProps) {
  const selectedField = availableFields.find((f) => f.field === rule.field)
  const fieldType = selectedField?.type || 'string'
  const operators = OPERATORS_BY_TYPE[fieldType] || OPERATORS_BY_TYPE.string
  const needsValue = !['is_null', 'is_not_null'].includes(rule.operator)
  const complete = isRuleComplete(rule)

  const codeOptions = rule.field ? CODE_VALUE_OPTIONS[rule.field] : undefined

  return (
    <Stack
      direction="row"
      spacing={1}
      alignItems="center"
      sx={{
        p: 1,
        borderRadius: 1,
        backgroundColor: complete ? 'transparent' : '#FFEBEE',
        border: 1,
        borderColor: complete ? 'divider' : '#FFCDD2',
      }}
    >
      {/* Field selector */}
      <FormControl size="small" sx={{ minWidth: 160 }}>
        <InputLabel>Property</InputLabel>
        <Select
          value={rule.field}
          label="Property"
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
          <InputLabel>Operator</InputLabel>
          <Select
            value={rule.operator}
            label="Operator"
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
            <InputLabel>Value</InputLabel>
            <Select
              value={rule.value}
              label="Value"
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
              label="Value"
              value={rule.value.split(' ')[0] || ''}
              onChange={(e) => onChange({ value: `${e.target.value} ${rule.value.split(' ')[1] || 'days'}` })}
              sx={{ width: 80 }}
            />
            <FormControl size="small" sx={{ minWidth: 80 }}>
              <Select
                value={rule.value.split(' ')[1] || 'days'}
                onChange={(e) => onChange({ value: `${rule.value.split(' ')[0] || ''} ${e.target.value}` })}
              >
                <MenuItem value="days">days</MenuItem>
                <MenuItem value="weeks">weeks</MenuItem>
                <MenuItem value="months">months</MenuItem>
                <MenuItem value="years">years</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        ) : (
          <TextField
            size="small"
            label="Value"
            value={rule.value}
            onChange={(e) => onChange({ value: e.target.value })}
            type={fieldType === 'decimal' ? 'number' : 'text'}
            sx={{ minWidth: 120 }}
          />
        )
      )}

      <Box sx={{ flex: 1 }} />
      <IconButton size="small" color="error" onClick={onRemove}>
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

  // Map field path to FHIR CQL accessor
  const accessor = rule.field.includes('.') ? rule.field : rule.field

  switch (rule.operator) {
    case 'is_null': return `${accessor} is null`
    case 'is_not_null': return `${accessor} is not null`
    case 'equals':
      if (fieldDef.type === 'code') return `${accessor} ~ '${rule.value}'`
      if (fieldDef.type === 'decimal') return `${accessor} = ${rule.value}`
      return `${accessor} = '${rule.value}'`
    case 'not_equals':
      if (fieldDef.type === 'code') return `${accessor} !~ '${rule.value}'`
      if (fieldDef.type === 'decimal') return `${accessor} != ${rule.value}`
      return `${accessor} != '${rule.value}'`
    case 'gt': return `${accessor} > ${rule.value}`
    case 'gte': return `${accessor} >= ${rule.value}`
    case 'lt': return `${accessor} < ${rule.value}`
    case 'lte': return `${accessor} <= ${rule.value}`
    case 'starts_with': return `StartsWith(${accessor}, '${rule.value}')`
    case 'ends_with': return `EndsWith(${accessor}, '${rule.value}')`
    case 'contains': return `PositionOf('${rule.value}', ${accessor}) >= 0`
    case 'before': return `${accessor} before @${rule.value}`
    case 'after': return `${accessor} after @${rule.value}`
    case 'within_last': {
      const [num, unit] = rule.value.split(' ')
      return `${accessor} >= Now() - ${num} ${unit}`
    }
    case 'in': return `${accessor} in "${rule.value}"`
    default: return null
  }
}
