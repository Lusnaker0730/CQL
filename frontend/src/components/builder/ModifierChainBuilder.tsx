import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  Typography,
  IconButton,
  Chip,
  TextField,
  MenuItem,
  Button,
  Box,
  Tooltip,
  Paper,
} from '@mui/material'
import {
  Add as AddIcon,
  Close as CloseIcon,
  DragIndicator as DragIcon,
} from '@mui/icons-material'

/** A modifier in the chain */
export interface ChainModifier {
  id: string
  type: ModifierType
  values: Record<string, string>
}

export type ModifierType =
  | 'activeOnly'
  | 'confirmedOnly'
  | 'completedOnly'
  | 'verified'
  | 'lookBack'
  | 'mostRecent'
  | 'first'
  | 'exists'
  | 'count'
  | 'valueComparison'
  | 'booleanCheck'
  | 'equalsString'
  | 'startsWithString'
  | 'endsWithString'
  | 'qualifier'
  | 'filterUnit'
  | 'convertUnit'
  | 'quantityValue'
  | 'conceptValue'
  | 'numericValue'
  | 'codeText'
  | 'codeValue'
  | 'displayValue'

type ModifierCategory = 'filter' | 'aggregate' | 'compare' | 'string' | 'unit'

interface ModifierDef {
  type: ModifierType
  labelKey: string
  category: ModifierCategory
  /** Only allow one instance of this modifier in the chain */
  singleton?: boolean
  /** Which resource types this modifier applies to (empty = all) */
  resourceTypes?: string[]
  fields: { key: string; labelKey: string; type: 'text' | 'select' | 'number'; options?: { value: string; labelKey: string }[] }[]
}

const MODIFIER_DEFS: ModifierDef[] = [
  // Filters
  {
    type: 'activeOnly', labelKey: 'activeOnly', category: 'filter', singleton: true,
    resourceTypes: ['MedicationRequest', 'MedicationStatement', 'ServiceRequest'],
    fields: [],
  },
  {
    type: 'confirmedOnly', labelKey: 'confirmedOnly', category: 'filter', singleton: true,
    resourceTypes: ['Condition', 'AllergyIntolerance'],
    fields: [],
  },
  {
    type: 'completedOnly', labelKey: 'completedOnly', category: 'filter', singleton: true,
    resourceTypes: ['Procedure', 'Encounter'],
    fields: [],
  },
  {
    type: 'verified', labelKey: 'verified', category: 'filter', singleton: true,
    resourceTypes: ['Observation'],
    fields: [],
  },
  {
    type: 'lookBack', labelKey: 'lookBack', category: 'filter',
    fields: [
      { key: 'value', labelKey: 'value', type: 'number' },
      {
        key: 'unit', labelKey: 'unit', type: 'select',
        options: [
          { value: 'years', labelKey: 'years' },
          { value: 'months', labelKey: 'months' },
          { value: 'weeks', labelKey: 'weeks' },
          { value: 'days', labelKey: 'days' },
        ],
      },
    ],
  },
  {
    type: 'qualifier', labelKey: 'qualifier', category: 'filter',
    fields: [
      {
        key: 'matchType', labelKey: 'matchType', type: 'select',
        options: [
          { value: 'valueSet', labelKey: 'valueSet' },
          { value: 'code', labelKey: 'code' },
        ],
      },
      { key: 'matchValue', labelKey: 'value', type: 'text' },
    ],
  },
  // Aggregators
  {
    type: 'mostRecent', labelKey: 'mostRecent', category: 'aggregate', singleton: true,
    resourceTypes: ['Observation'],
    fields: [],
  },
  {
    type: 'first', labelKey: 'first', category: 'aggregate', singleton: true,
    fields: [],
  },
  { type: 'exists', labelKey: 'exists', category: 'aggregate', singleton: true, fields: [] },
  { type: 'count', labelKey: 'count', category: 'aggregate', singleton: true, fields: [] },
  // Comparisons
  {
    type: 'valueComparison', labelKey: 'valueComparison', category: 'compare',
    fields: [
      {
        key: 'minOp', labelKey: 'operator', type: 'select',
        options: [
          { value: '>', labelKey: '>' },
          { value: '>=', labelKey: '>=' },
          { value: '=', labelKey: '=' },
          { value: '<', labelKey: '<' },
          { value: '<=', labelKey: '<=' },
        ],
      },
      { key: 'minVal', labelKey: 'min', type: 'text' },
      {
        key: 'maxOp', labelKey: 'operator', type: 'select',
        options: [
          { value: '', labelKey: '—' },
          { value: '<', labelKey: '<' },
          { value: '<=', labelKey: '<=' },
        ],
      },
      { key: 'maxVal', labelKey: 'max', type: 'text' },
      { key: 'unit', labelKey: 'unit', type: 'text' },
    ],
  },
  {
    type: 'booleanCheck', labelKey: 'booleanCheck', category: 'compare',
    fields: [
      {
        key: 'check', labelKey: 'operator', type: 'select',
        options: [
          { value: 'is null', labelKey: 'isNull' },
          { value: 'is not null', labelKey: 'isNotNull' },
          { value: '= true', labelKey: 'isTrue' },
          { value: '= false', labelKey: 'isFalse' },
        ],
      },
    ],
  },
  // String ops
  {
    type: 'equalsString', labelKey: 'equals', category: 'string',
    fields: [{ key: 'value', labelKey: 'value', type: 'text' }],
  },
  {
    type: 'startsWithString', labelKey: 'startsWith', category: 'string',
    fields: [{ key: 'value', labelKey: 'value', type: 'text' }],
  },
  {
    type: 'endsWithString', labelKey: 'endsWith', category: 'string',
    fields: [{ key: 'value', labelKey: 'value', type: 'text' }],
  },
  // Unit
  {
    type: 'filterUnit', labelKey: 'filterUnit', category: 'unit',
    fields: [{ key: 'unit', labelKey: 'unit', type: 'text' }],
  },
  {
    type: 'convertUnit', labelKey: 'convertUnit', category: 'unit',
    fields: [{ key: 'unit', labelKey: 'unit', type: 'text' }],
  },
  // Value extraction
  {
    type: 'quantityValue', labelKey: 'quantityValue', category: 'aggregate', singleton: true,
    resourceTypes: ['Observation'],
    fields: [],
  },
  {
    type: 'conceptValue', labelKey: 'conceptValue', category: 'aggregate', singleton: true,
    resourceTypes: ['Observation'],
    fields: [],
  },
  {
    type: 'numericValue', labelKey: 'numericValue', category: 'aggregate', singleton: true,
    fields: [],
  },
  {
    type: 'codeText', labelKey: 'codeText', category: 'aggregate', singleton: true,
    fields: [],
  },
  {
    type: 'codeValue', labelKey: 'codeValue', category: 'aggregate', singleton: true,
    fields: [],
  },
  {
    type: 'displayValue', labelKey: 'displayValue', category: 'aggregate', singleton: true,
    fields: [],
  },
]

const CATEGORY_COLORS: Record<ModifierCategory, string> = {
  filter: '#0D7377',
  aggregate: '#7B1FA2',
  compare: '#1565C0',
  string: '#4E342E',
  unit: '#E65100',
}

interface ModifierChainBuilderProps {
  modifiers: ChainModifier[]
  onChange: (modifiers: ChainModifier[]) => void
  resourceType: string
  valueSets?: string[]
  codes?: string[]
}

let nextId = 1

export default function ModifierChainBuilder({
  modifiers,
  onChange,
  resourceType,
  valueSets = [],
  codes = [],
}: ModifierChainBuilderProps) {
  const { t } = useTranslation('builder')
  const [menuOpen, setMenuOpen] = useState(false)

  const availableModifiers = MODIFIER_DEFS.filter((def) => {
    if (def.resourceTypes && !def.resourceTypes.includes(resourceType)) return false
    if (def.singleton && modifiers.some((m) => m.type === def.type)) return false
    return true
  })

  const handleAdd = (type: ModifierType) => {
    const def = MODIFIER_DEFS.find((d) => d.type === type)
    if (!def) return
    const defaults: Record<string, string> = {}
    for (const f of def.fields) {
      defaults[f.key] = f.options?.[0]?.value ?? ''
    }
    onChange([...modifiers, { id: `mod_${nextId++}`, type, values: defaults }])
    setMenuOpen(false)
  }

  const handleRemove = (id: string) => {
    onChange(modifiers.filter((m) => m.id !== id))
  }

  const handleUpdateValue = (id: string, key: string, value: string) => {
    onChange(modifiers.map((m) =>
      m.id === id ? { ...m, values: { ...m.values, [key]: value } } : m
    ))
  }

  return (
    <Stack spacing={0.5}>
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('modifierChain.title')}
      </Typography>

      {modifiers.map((mod) => {
        const def = MODIFIER_DEFS.find((d) => d.type === mod.type)
        if (!def) return null
        const catColor = CATEGORY_COLORS[def.category] || '#546E7A'

        return (
          <Paper
            key={mod.id}
            variant="outlined"
            sx={{
              p: 0.75,
              borderLeft: 3,
              borderLeftColor: catColor,
              bgcolor: `${catColor}08`,
            }}
          >
            <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: def.fields.length > 0 ? 0.5 : 0 }}>
              <DragIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
              <Chip
                label={t(`modifierChain.${def.labelKey}`)}
                size="small"
                sx={{
                  height: 20,
                  fontSize: '0.65rem',
                  fontWeight: 600,
                  color: catColor,
                  bgcolor: `${catColor}18`,
                }}
              />
              <Box sx={{ flex: 1 }} />
              <Tooltip title={t('modifierChain.removeModifier')}>
                <IconButton size="small" onClick={() => handleRemove(mod.id)} sx={{ p: 0.25 }}>
                  <CloseIcon sx={{ fontSize: 14 }} />
                </IconButton>
              </Tooltip>
            </Stack>

            {def.fields.length > 0 && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap" sx={{ pl: 2.5 }}>
                {def.fields.map((field) => {
                  // For qualifier matchValue, show valueSets or codes based on matchType
                  if (field.key === 'matchValue' && mod.type === 'qualifier') {
                    const options = mod.values.matchType === 'valueSet' ? valueSets : codes
                    if (options.length > 0) {
                      return (
                        <TextField
                          key={field.key}
                          select
                          size="small"
                          label={t(`modifierChain.${field.labelKey}`)}
                          value={mod.values[field.key] || ''}
                          onChange={(e) => handleUpdateValue(mod.id, field.key, e.target.value)}
                          sx={{ minWidth: 140, flex: 1, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                        >
                          {options.map((opt) => (
                            <MenuItem key={opt} value={opt} sx={{ fontSize: '0.75rem' }}>{opt}</MenuItem>
                          ))}
                        </TextField>
                      )
                    }
                  }

                  if (field.type === 'select' && field.options) {
                    return (
                      <TextField
                        key={field.key}
                        select
                        size="small"
                        label={t(`modifierChain.${field.labelKey}`)}
                        value={mod.values[field.key] || ''}
                        onChange={(e) => handleUpdateValue(mod.id, field.key, e.target.value)}
                        sx={{ minWidth: 80, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                      >
                        {field.options.map((opt) => (
                          <MenuItem key={opt.value} value={opt.value} sx={{ fontSize: '0.75rem' }}>
                            {opt.labelKey.length <= 2 ? opt.labelKey : t(`modifierChain.${opt.labelKey}`)}
                          </MenuItem>
                        ))}
                      </TextField>
                    )
                  }

                  return (
                    <TextField
                      key={field.key}
                      size="small"
                      type={field.type === 'number' ? 'number' : 'text'}
                      label={t(`modifierChain.${field.labelKey}`)}
                      value={mod.values[field.key] || ''}
                      onChange={(e) => handleUpdateValue(mod.id, field.key, e.target.value)}
                      sx={{ width: field.type === 'number' ? 70 : 100, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                    />
                  )
                })}
              </Stack>
            )}
          </Paper>
        )
      })}

      {menuOpen ? (
        <Paper variant="outlined" sx={{ p: 1 }}>
          <Typography variant="caption" fontWeight={600} gutterBottom display="block">
            {t('modifierChain.addModifier')}
          </Typography>
          <Stack direction="row" flexWrap="wrap" gap={0.5}>
            {availableModifiers.map((def) => (
              <Chip
                key={def.type}
                label={t(`modifierChain.${def.labelKey}`)}
                size="small"
                onClick={() => handleAdd(def.type)}
                sx={{
                  height: 22,
                  fontSize: '0.65rem',
                  cursor: 'pointer',
                  color: CATEGORY_COLORS[def.category],
                  borderColor: CATEGORY_COLORS[def.category],
                  '&:hover': { bgcolor: `${CATEGORY_COLORS[def.category]}14` },
                }}
                variant="outlined"
              />
            ))}
          </Stack>
          <Button size="small" onClick={() => setMenuOpen(false)} sx={{ mt: 0.5, fontSize: '0.7rem' }}>
            {t('common.cancel')}
          </Button>
        </Paper>
      ) : (
        <Button
          size="small"
          startIcon={<AddIcon sx={{ fontSize: 14 }} />}
          onClick={() => setMenuOpen(true)}
          sx={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}
        >
          {t('modifierChain.addModifier')}
        </Button>
      )}
    </Stack>
  )
}

/** Escape single quotes for CQL string literals */
function cqlEscapeString(value: string): string {
  return value.replace(/'/g, "\\'")
}

/** Generate CQL from modifier chain */
export function applyModifierChain(
  baseExpr: string,
  alias: string,
  resourceType: string,
  modifiers: ChainModifier[],
): string {
  let expr = baseExpr
  const whereClauses: string[] = []
  let wrapLast = false
  let wrapFirst = false
  let wrapExists = false
  let wrapCount = false
  let sortClause = ''

  for (const mod of modifiers) {
    switch (mod.type) {
      case 'activeOnly':
        whereClauses.push(`${alias}.status = 'active'`)
        break
      case 'confirmedOnly':
        whereClauses.push(`${alias}.clinicalStatus.coding.code contains 'active'`)
        break
      case 'completedOnly':
        whereClauses.push(`${alias}.status = 'completed'`)
        break
      case 'verified':
        whereClauses.push(`(${alias}.status = 'final' or ${alias}.status = 'amended' or ${alias}.status = 'corrected')`)
        break
      case 'lookBack':
        if (mod.values.value) {
          const dateExpr = getDateExprForResource(resourceType, alias)
          whereClauses.push(`${dateExpr} >= Now() - ${mod.values.value} ${mod.values.unit || 'years'}`)
        }
        break
      case 'qualifier':
        if (mod.values.matchValue) {
          const quotedVal = mod.values.matchValue.replace(/^"(.*)"$/, '$1')
          if (mod.values.matchType === 'valueSet') {
            whereClauses.push(`${alias}.code in "${quotedVal}"`)
          } else {
            whereClauses.push(`${alias}.code ~ "${quotedVal}"`)
          }
        }
        break
      case 'valueComparison':
        if (mod.values.minVal) {
          const unitSuffix = mod.values.unit ? ` '${cqlEscapeString(mod.values.unit)}'` : ''
          whereClauses.push(`${alias}.value ${mod.values.minOp || '>='} ${mod.values.minVal}${unitSuffix}`)
        }
        if (mod.values.maxVal && mod.values.maxOp) {
          const unitSuffix = mod.values.unit ? ` '${cqlEscapeString(mod.values.unit)}'` : ''
          whereClauses.push(`${alias}.value ${mod.values.maxOp} ${mod.values.maxVal}${unitSuffix}`)
        }
        break
      case 'booleanCheck':
        if (mod.values.check) {
          // Applied after query as wrapper
          expr = `(${expr}) ${mod.values.check}`
        }
        break
      case 'equalsString':
        if (mod.values.value) whereClauses.push(`${alias}.value = '${cqlEscapeString(mod.values.value)}'`)
        break
      case 'startsWithString':
        if (mod.values.value) whereClauses.push(`StartsWith(${alias}.value, '${cqlEscapeString(mod.values.value)}')`)
        break
      case 'endsWithString':
        if (mod.values.value) whereClauses.push(`EndsWith(${alias}.value, '${cqlEscapeString(mod.values.value)}')`)
        break
      case 'filterUnit':
        if (mod.values.unit) whereClauses.push(`${alias}.value.unit = '${cqlEscapeString(mod.values.unit)}'`)
        break
      case 'convertUnit':
        // handled as post-wrapper below
        break
      case 'mostRecent':
        wrapLast = true
        sortClause = `\n    sort by FHIRHelpers.ToDateTime(effective as FHIR.dateTime)`
        break
      case 'first':
        wrapFirst = true
        break
      case 'exists':
        wrapExists = true
        break
      case 'count':
        wrapCount = true
        break
      case 'quantityValue':
      case 'conceptValue':
      case 'numericValue':
      case 'codeText':
      case 'codeValue':
      case 'displayValue':
        // handled as post-wrappers below
        break
    }
  }

  // Build query
  const whereStr = whereClauses.length > 0
    ? `\n    where ${whereClauses.join('\n      and ')}`
    : ''
  const needsQuery = whereStr || sortClause

  if (needsQuery) {
    expr = `${expr} ${alias}${whereStr}${sortClause}`
  }

  // Apply wrappers
  if (wrapLast) {
    expr = needsQuery ? `Last(\n    ${expr}\n  )` : `Last(${expr})`
  } else if (wrapFirst) {
    expr = needsQuery ? `First(\n    ${expr}\n  )` : `First(${expr})`
  }

  if (wrapExists) {
    if (wrapLast || wrapFirst) {
      expr = `${expr} is not null`
    } else {
      expr = needsQuery ? `exists (\n    ${expr}\n  )` : `exists ${expr}`
    }
  } else if (wrapCount) {
    expr = needsQuery ? `Count(\n    ${expr}\n  )` : `Count(${expr})`
  }

  // Value extraction & conversion wrappers (applied after aggregation)
  for (const mod of modifiers) {
    if (mod.type === 'quantityValue') {
      expr = `FHIRHelpers.ToQuantity((${expr}).value as FHIR.Quantity)`
    } else if (mod.type === 'conceptValue') {
      expr = `FHIRHelpers.ToConcept((${expr}).value as FHIR.CodeableConcept)`
    } else if (mod.type === 'convertUnit') {
      if (mod.values.unit) expr = `convert (${expr}) to '${cqlEscapeString(mod.values.unit)}'`
    } else if (mod.type === 'numericValue') {
      expr = `FHIRHelpers.ToDecimal(((${expr}).value as FHIR.Quantity).value)`
    } else if (mod.type === 'codeText') {
      expr = `(${expr}).code.text`
    } else if (mod.type === 'codeValue') {
      expr = `FHIRHelpers.ToConcept((${expr}).code)`
    } else if (mod.type === 'displayValue') {
      expr = `(${expr}).code.coding[0].display`
    }
  }

  return expr
}

function getDateExprForResource(resourceType: string, alias: string): string {
  switch (resourceType) {
    case 'Observation':
    case 'MedicationStatement':
      return `${alias}.effective`
    case 'Condition':
    case 'AllergyIntolerance':
      return `${alias}.onset`
    case 'Procedure':
      return `${alias}.performed`
    case 'MedicationRequest':
    case 'ServiceRequest':
      return `${alias}.authoredOn`
    case 'Encounter':
      return `${alias}.period`
    case 'Immunization':
      return `${alias}.occurrence`
    default:
      return `${alias}.effective`
  }
}
