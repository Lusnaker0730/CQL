import {
  Card, CardContent, Stack, Typography, IconButton, Tooltip, TextField,
  FormControl, InputLabel, Select, MenuItem, Alert,
} from '@mui/material'
import { Close as RemoveIcon, Warning as WarnIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import UcumUnitField from '../fields/UcumUnitField'
import type { Modifier } from '../../../types/authoring'

const TIME_UNITS = [
  { value: 'years', label: 'Year(s)' },
  { value: 'months', label: 'Month(s)' },
  { value: 'weeks', label: 'Week(s)' },
  { value: 'days', label: 'Day(s)' },
  { value: 'hours', label: 'Hour(s)' },
]

const COMPARISON_OPERATORS = [
  { value: '>', label: '>' },
  { value: '>=', label: '>=' },
  { value: '=', label: '=' },
  { value: '!=', label: '!=' },
  { value: '<', label: '<' },
  { value: '<=', label: '<=' },
]

const BOOL_COMPARISON_OPTIONS = [
  { value: 'is null', label: 'Is Null' },
  { value: 'is not null', label: 'Is Not Null' },
  { value: 'is true', label: 'Is True' },
  { value: 'is not true', label: 'Is Not True' },
  { value: 'is false', label: 'Is False' },
  { value: 'is not false', label: 'Is Not False' },
]

interface ModifierCardProps {
  modifier: Modifier
  onRemove: () => void
  onUpdateValues: (values: Record<string, unknown>) => void
}

export default function ModifierCard({ modifier, onRemove, onUpdateValues }: ModifierCardProps) {
  const { t } = useTranslation('authoring')
  const handleValueChange = (key: string, value: unknown) => {
    onUpdateValues({ ...(modifier.values || {}), [key]: value })
  }

  const missingFields = getMissingRequiredFields(modifier)

  return (
    <Card variant="outlined" sx={{ backgroundColor: 'action.hover' }}>
      <CardContent sx={{ py: 1, px: 2, '&:last-child': { pb: 1 } }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>
            {modifier.name}
          </Typography>
          {missingFields.length > 0 && (
            <Tooltip title={t('modifier.missingFields', { fields: missingFields.join(', ') })}>
              <WarnIcon fontSize="small" color="warning" />
            </Tooltip>
          )}
          <Typography variant="caption" color="text.secondary">
            {modifier.returnType.replace(/_/g, ' ')}
          </Typography>
          <Tooltip title={t('modifier.removeModifier')}>
            <IconButton size="small" onClick={onRemove} aria-label={t('modifier.removeModifier')}>
              <RemoveIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>

        <ModifierValueEditor
          modifier={modifier}
          onValueChange={handleValueChange}
        />

        {missingFields.length > 0 && (
          <Alert severity="warning" sx={{ mt: 1, py: 0 }} icon={false}>
            <Typography variant="caption">
              {t('modifier.fillRequired', { fields: missingFields.join(', ') })}
            </Typography>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

// ----- Type-Specific Value Editors -----

function ModifierValueEditor({
  modifier,
  onValueChange,
}: {
  modifier: Modifier
  onValueChange: (key: string, value: unknown) => void
}) {
  const { t } = useTranslation('authoring')
  if (!modifier.values || Object.keys(modifier.values).length === 0) return null

  const modType = modifier.cqlTemplate || modifier.id

  // LookBack modifiers: value (number) + unit (time dropdown)
  if (modType === 'LookBackModifier' || modifier.id.startsWith('LookBack')) {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <Typography variant="body2" color="text.secondary">{t('modifier.withinLast')}</Typography>
        <TextField
          size="small"
          type="number"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ width: 80 }}
          inputProps={{ min: 0 }}
        />
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <Select
            value={modifier.values.unit ?? ''}
            displayEmpty
            onChange={(e) => onValueChange('unit', e.target.value)}
          >
            <MenuItem value="" disabled><em>{t('modifier.unit')}</em></MenuItem>
            {TIME_UNITS.map((u) => (
              <MenuItem key={u.value} value={u.value}>{u.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>
    )
  }

  // ValueComparison: operator + value (+ optional max)
  if (modType === 'ValueComparisonNumber' || modType === 'ValueComparisonObservation') {
    return (
      <Stack spacing={1} mt={1}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="body2" color="text.secondary">{t('modifier.min')}</Typography>
          <FormControl size="small" sx={{ minWidth: 80 }}>
            <Select
              value={modifier.values.minOperator ?? ''}
              displayEmpty
              onChange={(e) => onValueChange('minOperator', e.target.value)}
            >
              <MenuItem value="" disabled><em>Op</em></MenuItem>
              {COMPARISON_OPERATORS.map((op) => (
                <MenuItem key={op.value} value={op.value}>{op.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            size="small"
            type="number"
            value={modifier.values.minValue ?? ''}
            onChange={(e) => onValueChange('minValue', e.target.value)}
            sx={{ width: 100 }}
            placeholder={t('modifier.value')}
          />
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="body2" color="text.secondary">{t('modifier.max')}</Typography>
          <FormControl size="small" sx={{ minWidth: 80 }}>
            <Select
              value={modifier.values.maxOperator ?? ''}
              displayEmpty
              onChange={(e) => onValueChange('maxOperator', e.target.value)}
            >
              <MenuItem value=""><em>None</em></MenuItem>
              {COMPARISON_OPERATORS.map((op) => (
                <MenuItem key={op.value} value={op.value}>{op.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            size="small"
            type="number"
            value={modifier.values.maxValue ?? ''}
            onChange={(e) => onValueChange('maxValue', e.target.value)}
            sx={{ width: 100 }}
            placeholder={t('modifier.value')}
            disabled={!modifier.values.maxOperator}
          />
        </Stack>
        {(modifier.values.unit !== undefined) && (
          <UcumUnitField
            label={t('modifier.unit')}
            value={(modifier.values.unit as string) ?? ''}
            onChange={(val) => onValueChange('unit', val)}
            sx={{ width: 220 }}
          />
        )}
      </Stack>
    )
  }

  // ConvertUnits / WithUnit
  if (modType === 'ConvertUnits' || modType === 'WithUnit') {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <Typography variant="body2" color="text.secondary">{t('modifier.unit')}:</Typography>
        <UcumUnitField
          value={(modifier.values.unit as string) ?? ''}
          onChange={(val) => onValueChange('unit', val)}
          sx={{ width: 250 }}
        />
      </Stack>
    )
  }

  // BooleanComparison: Is (Not) Null/True/False
  if (modType === 'BooleanComparison') {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>{t('modifier.comparison')}</InputLabel>
          <Select
            value={modifier.values.value ?? ''}
            label={t('modifier.comparison')}
            onChange={(e) => onValueChange('value', e.target.value)}
          >
            {BOOL_COMPARISON_OPTIONS.map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>
    )
  }

  // String modifiers (Equals, StartsWith, EndsWith): single text value
  if (modType === 'EqualsString' || modType === 'StartsWithString' || modType === 'EndsWithString') {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <Typography variant="body2" color="text.secondary">{t('modifier.value')}:</Typography>
        <TextField
          size="small"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ flex: 1 }}
          placeholder={t('modifier.enterText')}
        />
      </Stack>
    )
  }

  // Time/DateTime: value + precision
  if (modType.includes('TimePrecise') || modType.includes('DateTimePrecise')) {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <TextField
          size="small"
          type={modType.includes('DateTime') ? 'datetime-local' : 'time'}
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ flex: 1 }}
          InputLabelProps={{ shrink: true }}
        />
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <InputLabel>{t('modifier.precision')}</InputLabel>
          <Select
            value={modifier.values.precision ?? ''}
            label={t('modifier.precision')}
            onChange={(e) => onValueChange('precision', e.target.value)}
          >
            <MenuItem value=""><em>Default</em></MenuItem>
            <MenuItem value="year">Year</MenuItem>
            <MenuItem value="month">Month</MenuItem>
            <MenuItem value="day">Day</MenuItem>
            <MenuItem value="hour">Hour</MenuItem>
            <MenuItem value="minute">Minute</MenuItem>
            <MenuItem value="second">Second</MenuItem>
          </Select>
        </FormControl>
      </Stack>
    )
  }

  // Qualifier: qualifier type + value set or code
  if (modType === 'Qualifier') {
    return (
      <Stack spacing={1} mt={1}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="body2" color="text.secondary">{t('modifier.matchBy')}</Typography>
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <Select
              value={modifier.values.qualifier ?? 'value set'}
              displayEmpty
              onChange={(e) => onValueChange('qualifier', e.target.value)}
            >
              <MenuItem value="value set">Value Set</MenuItem>
              <MenuItem value="code">Code</MenuItem>
            </Select>
          </FormControl>
        </Stack>
        {(modifier.values.qualifier ?? 'value set') === 'value set' ? (
          <TextField
            size="small"
            label={t('modifier.valueSetName')}
            value={modifier.values.valueSet ?? ''}
            onChange={(e) => onValueChange('valueSet', e.target.value)}
            sx={{ flex: 1 }}
            placeholder={t('modifier.enterValueSet')}
          />
        ) : (
          <TextField
            size="small"
            label={t('modifier.codeLabel')}
            value={modifier.values.code ?? ''}
            onChange={(e) => onValueChange('code', e.target.value)}
            sx={{ flex: 1 }}
            placeholder={t('modifier.enterCode')}
          />
        )}
      </Stack>
    )
  }

  // Before/After Interval: value input
  if (modType === 'BeforeInterval' || modType === 'AfterInterval') {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <Typography variant="body2" color="text.secondary">
          {modType === 'BeforeInterval' ? t('modifier.before') : t('modifier.after')}
        </Typography>
        <TextField
          size="small"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ flex: 1 }}
          placeholder={t('modifier.enterValueOrExpr')}
        />
      </Stack>
    )
  }

  // Contains modifiers: value (+ optional unit for quantity)
  if (modType.startsWith('Contains')) {
    return (
      <Stack direction="row" spacing={1} mt={1} alignItems="center">
        <Typography variant="body2" color="text.secondary">{t('modifier.contains')}</Typography>
        <TextField
          size="small"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ width: 150 }}
          placeholder={t('modifier.value')}
        />
        {modifier.values.unit !== undefined && (
          <UcumUnitField
            value={(modifier.values.unit as string) ?? ''}
            onChange={(val) => onValueChange('unit', val)}
            sx={{ width: 200 }}
          />
        )}
      </Stack>
    )
  }

  // Fallback: generic key-value text fields
  return (
    <Stack direction="row" spacing={1} mt={1} flexWrap="wrap" useFlexGap>
      {Object.entries(modifier.values).map(([key, val]) => (
        <TextField
          key={key}
          label={key.replace(/([A-Z])/g, ' $1').trim()}
          size="small"
          value={val ?? ''}
          onChange={(e) => onValueChange(key, e.target.value)}
          sx={{ minWidth: 120 }}
        />
      ))}
    </Stack>
  )
}

// ----- Validation -----

function getMissingRequiredFields(modifier: Modifier): string[] {
  // LookBack requires value + unit
  if (modifier.cqlTemplate === 'LookBackModifier' || modifier.id.startsWith('LookBack')) {
    const missing: string[] = []
    if (!modifier.values?.value) missing.push('value')
    if (!modifier.values?.unit) missing.push('unit')
    return missing
  }

  // ValueComparison requires minOperator + minValue
  if (modifier.cqlTemplate === 'ValueComparisonNumber' || modifier.cqlTemplate === 'ValueComparisonObservation') {
    const missing: string[] = []
    if (!modifier.values?.minOperator) missing.push('operator')
    if (!modifier.values?.minValue && modifier.values?.minValue !== 0) missing.push('value')
    return missing
  }

  // ConvertUnits / WithUnit requires unit
  if (modifier.cqlTemplate === 'ConvertUnits' || modifier.cqlTemplate === 'WithUnit') {
    return modifier.values?.unit ? [] : ['unit']
  }

  // String modifiers require value
  if (['EqualsString', 'StartsWithString', 'EndsWithString'].includes(modifier.cqlTemplate || '')) {
    return modifier.values?.value ? [] : ['value']
  }

  // Qualifier requires valueSet or code depending on qualifier type
  if (modifier.cqlTemplate === 'Qualifier') {
    const qualifier = (modifier.values?.qualifier as string) ?? 'value set'
    if (qualifier === 'value set') {
      return modifier.values?.valueSet ? [] : ['valueSet']
    }
    return modifier.values?.code ? [] : ['code']
  }

  // Before/After Interval require value
  if (modifier.cqlTemplate === 'BeforeInterval' || modifier.cqlTemplate === 'AfterInterval') {
    return modifier.values?.value ? [] : ['value']
  }

  return []
}
