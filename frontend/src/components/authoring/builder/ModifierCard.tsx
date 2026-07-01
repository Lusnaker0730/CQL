import { useMemo } from 'react'
import {
  Card, CardContent, Stack, Typography, IconButton, Tooltip, TextField,
  FormControl, InputLabel, Select, MenuItem, Alert,
} from '@mui/material'
import { Close as RemoveIcon, Warning as WarnIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import UcumUnitField from '../fields/UcumUnitField'
import type { Modifier } from '../../../types/authoring'
import { getModifierMissingFields } from '../../../utils/modifierUtils'

const COMPARISON_OPERATORS = [
  { value: '>', label: '>' },
  { value: '>=', label: '>=' },
  { value: '=', label: '=' },
  { value: '!=', label: '!=' },
  { value: '<', label: '<' },
  { value: '<=', label: '<=' },
]

// Stable comparison values; labels resolve via i18n at render time.
const BOOL_COMPARISON_VALUES = [
  { value: 'is null', labelKey: 'boolIsNull' },
  { value: 'is not null', labelKey: 'boolIsNotNull' },
  { value: 'is true', labelKey: 'boolIsTrue' },
  { value: 'is not true', labelKey: 'boolIsNotTrue' },
  { value: 'is false', labelKey: 'boolIsFalse' },
  { value: 'is not false', labelKey: 'boolIsNotFalse' },
] as const

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

  const missingFields = getModifierMissingFields(modifier)

  return (
    <Card variant="outlined" sx={{ backgroundColor: 'action.hover' }}>
      <CardContent sx={{ py: 1, px: 2, '&:last-child': { pb: 1 } }}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography
            variant="body2"
            sx={{
              fontWeight: 600,
              flex: 1
            }}>
            {modifier.name}
          </Typography>
          {missingFields.length > 0 && (
            <Tooltip title={t('modifier.missingFields', { fields: missingFields.join(', ') })}>
              <WarnIcon fontSize="small" color="warning" />
            </Tooltip>
          )}
          <Typography variant="caption" sx={{
            color: "text.secondary"
          }}>
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
  );
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
  const TIME_UNITS = useMemo(() => [
    { value: 'years', label: t('modifier.years') },
    { value: 'months', label: t('modifier.months') },
    { value: 'weeks', label: t('modifier.weeks') },
    { value: 'days', label: t('modifier.days') },
    { value: 'hours', label: t('modifier.hours') },
  ], [t])
  if (!modifier.values || Object.keys(modifier.values).length === 0) return null

  const modType = modifier.cqlTemplate || modifier.id

  // LookBack modifiers: value (number) + unit (time dropdown)
  if (modType === 'LookBackModifier' || modifier.id.startsWith('LookBack')) {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>{t('modifier.withinLast')}</Typography>
        <TextField
          size="small"
          type="number"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ width: 80 }}
          slotProps={{
            htmlInput: { min: 0 }
          }}
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
    );
  }

  // ValueComparison: operator + value (+ optional max)
  if (modType === 'ValueComparisonNumber' || modType === 'ValueComparisonObservation') {
    return (
      <Stack spacing={1} sx={{
        mt: 1
      }}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>{t('modifier.min')}</Typography>
          <FormControl size="small" sx={{ minWidth: 80 }}>
            <Select
              value={modifier.values.minOperator ?? ''}
              displayEmpty
              onChange={(e) => onValueChange('minOperator', e.target.value)}
            >
              <MenuItem value="" disabled><em>{t('modifier.operatorPlaceholder')}</em></MenuItem>
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
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>{t('modifier.max')}</Typography>
          <FormControl size="small" sx={{ minWidth: 80 }}>
            <Select
              value={modifier.values.maxOperator ?? ''}
              displayEmpty
              onChange={(e) => onValueChange('maxOperator', e.target.value)}
            >
              <MenuItem value=""><em>{t('modifier.none')}</em></MenuItem>
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
    );
  }

  // ConvertUnits / WithUnit
  if (modType === 'ConvertUnits' || modType === 'WithUnit') {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>{t('modifier.unit')}:</Typography>
        <UcumUnitField
          value={(modifier.values.unit as string) ?? ''}
          onChange={(val) => onValueChange('unit', val)}
          sx={{ width: 250 }}
        />
      </Stack>
    );
  }

  // BooleanComparison: Is (Not) Null/True/False
  if (modType === 'BooleanComparison') {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>{t('modifier.comparison')}</InputLabel>
          <Select
            value={modifier.values.value ?? ''}
            label={t('modifier.comparison')}
            onChange={(e) => onValueChange('value', e.target.value)}
          >
            {BOOL_COMPARISON_VALUES.map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>{t(`modifier.${opt.labelKey}`)}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>
    );
  }

  // String modifiers (Equals, StartsWith, EndsWith): single text value
  if (modType === 'EqualsString' || modType === 'StartsWithString' || modType === 'EndsWithString') {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>{t('modifier.value')}:</Typography>
        <TextField
          size="small"
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ flex: 1 }}
          placeholder={t('modifier.enterText')}
        />
      </Stack>
    );
  }

  // Time/DateTime: value + precision
  if (modType.includes('TimePrecise') || modType.includes('DateTimePrecise')) {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <TextField
          size="small"
          type={modType.includes('DateTime') ? 'datetime-local' : 'time'}
          value={modifier.values.value ?? ''}
          onChange={(e) => onValueChange('value', e.target.value)}
          sx={{ flex: 1 }}
          slotProps={{
            inputLabel: { shrink: true }
          }}
        />
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <InputLabel>{t('modifier.precision')}</InputLabel>
          <Select
            value={modifier.values.precision ?? ''}
            label={t('modifier.precision')}
            onChange={(e) => onValueChange('precision', e.target.value)}
          >
            <MenuItem value=""><em>{t('modifier.default')}</em></MenuItem>
            <MenuItem value="year">{t('modifier.year')}</MenuItem>
            <MenuItem value="month">{t('modifier.month')}</MenuItem>
            <MenuItem value="day">{t('modifier.day')}</MenuItem>
            <MenuItem value="hour">{t('modifier.hour')}</MenuItem>
            <MenuItem value="minute">{t('modifier.minute')}</MenuItem>
            <MenuItem value="second">{t('modifier.second')}</MenuItem>
          </Select>
        </FormControl>
      </Stack>
    );
  }

  // Qualifier: qualifier type + value set or code
  if (modType === 'Qualifier') {
    return (
      <Stack spacing={1} sx={{
        mt: 1
      }}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>{t('modifier.matchBy')}</Typography>
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <Select
              value={modifier.values.qualifier ?? 'value set'}
              displayEmpty
              onChange={(e) => onValueChange('qualifier', e.target.value)}
            >
              <MenuItem value="value set">{t('modifier.valueSet')}</MenuItem>
              <MenuItem value="code">{t('modifier.codeLabel')}</MenuItem>
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
    );
  }

  // Before/After Interval: value input
  if (modType === 'BeforeInterval' || modType === 'AfterInterval') {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
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
    );
  }

  // Contains modifiers: value (+ optional unit for quantity)
  if (modType.startsWith('Contains')) {
    return (
      <Stack
        direction="row"
        spacing={1}
        sx={{
          mt: 1,
          alignItems: "center"
        }}>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>{t('modifier.contains')}</Typography>
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
    );
  }

  // Fallback: generic key-value text fields
  return (
    <Stack
      direction="row"
      spacing={1}
      useFlexGap
      sx={{
        mt: 1,
        flexWrap: "wrap"
      }}>
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
  );
}
