import { useTranslation } from 'react-i18next'
import {
  Alert, Box, MenuItem, Stack, TextField, Typography,
  IconButton, Paper, ToggleButton, ToggleButtonGroup, Tooltip,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Timer as TimerIcon,
  Science as ScienceIcon,
  AccountTree as TreeIcon,
} from '@mui/icons-material'
import type { ObservationEntry, ObservationType } from '../../types/ecqm'
import type { FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import {
  AGGREGATE_METHODS, OBSERVATION_TYPES, DURATION_UNITS,
  DURATION_PROPERTIES, DURATION_DEFAULT_PROPERTY,
  QUANTITY_PROPERTIES,
} from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'

interface Props {
  observation: ObservationEntry
  populationBasis: string
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (updated: ObservationEntry) => void
  onRemove: () => void
}

const TYPE_ICONS: Record<ObservationType, React.ReactNode> = {
  duration: <TimerIcon fontSize="small" />,
  quantity: <ScienceIcon fontSize="small" />,
  criteria: <TreeIcon fontSize="small" />,
}

export default function EcqmObservationEditor({
  observation, populationBasis, templates, modifiers, onChange, onRemove,
}: Props) {
  const { t } = useTranslation('ecqm')
  const obsType: ObservationType = observation.observationType || 'duration'
  const isEpisodeBased = populationBasis !== 'boolean'
  const paramName = isEpisodeBased ? populationBasis : 'Patient'

  const durationProps = DURATION_PROPERTIES[populationBasis] || ['period']
  const quantityProps = QUANTITY_PROPERTIES[populationBasis] || ['value']

  const currentUnit = observation.observationUnit || 'days'
  const currentProperty = observation.observationProperty
    || (obsType === 'quantity' ? quantityProps[0] : (DURATION_DEFAULT_PROPERTY[populationBasis] || 'period'))

  const handleTypeChange = (_: React.MouseEvent<HTMLElement>, newType: ObservationType | null) => {
    if (!newType) return
    const defaults: Partial<ObservationEntry> = {}
    if (newType === 'duration') {
      defaults.observationUnit = 'days'
      defaults.observationProperty = DURATION_DEFAULT_PROPERTY[populationBasis] || 'period'
    } else if (newType === 'quantity') {
      defaults.observationProperty = quantityProps[0] || 'value'
    }
    onChange({ ...observation, observationType: newType, ...defaults })
  }

  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
        <Typography variant="subtitle2" fontWeight={600}>{t('observation.title')}</Typography>
        <IconButton
          size="small"
          color="error"
          onClick={onRemove}
          aria-label={t('observation.remove')}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <TextField
          label={t('observation.aggregateMethod')} select size="small" sx={{ width: 200 }}
          value={observation.aggregateMethod}
          onChange={(e) => onChange({ ...observation, aggregateMethod: e.target.value })}
        >
          {AGGREGATE_METHODS.map((m) => (
            <MenuItem key={m} value={m}>{m}</MenuItem>
          ))}
        </TextField>
        {observation.aggregateMethod === 'Percentile' && (
          <TextField
            label={t('observation.percentileValue')}
            type="number"
            size="small"
            sx={{ width: 140 }}
            value={observation.percentileValue ?? ''}
            onChange={(e) => {
              const raw = e.target.value
              const v = raw === '' ? undefined : Number(raw)
              onChange({ ...observation, percentileValue: v })
            }}
            inputProps={{ min: 0, max: 100, step: 1 }}
            error={
              observation.percentileValue == null ||
              observation.percentileValue < 0 ||
              observation.percentileValue > 100
            }
            helperText={
              observation.percentileValue == null
                ? t('observation.percentileRequired')
                : observation.percentileValue < 0 || observation.percentileValue > 100
                  ? t('observation.percentileRange')
                  : t('observation.percentileHint')
            }
          />
        )}
        <TextField
          label={t('observation.populationRef')} size="small" sx={{ flex: 1, minWidth: 200 }}
          value={observation.populationRef || ''}
          onChange={(e) => onChange({ ...observation, populationRef: e.target.value })}
          placeholder={t('observation.populationRefPlaceholder')}
          error={!observation.populationRef}
          helperText={!observation.populationRef ? t('observation.populationRefRequired') : ' '}
        />
      </Stack>

      <Box sx={{ mb: 2 }}>
        <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
          {t('observation.observationType')}
        </Typography>
        <ToggleButtonGroup
          value={obsType}
          exclusive
          onChange={handleTypeChange}
          size="small"
          sx={{ mb: 0.5 }}
        >
          {OBSERVATION_TYPES.map((type) => (
            <ToggleButton key={type} value={type} sx={{ textTransform: 'none', px: 2 }}>
              <Tooltip title={t(`observation.types.${type}Desc`)}>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  {TYPE_ICONS[type]}
                  <span>{t(`observation.types.${type}`)}</span>
                </Stack>
              </Tooltip>
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      {obsType === 'duration' && (
        <Box sx={{ mb: 1 }}>
          <Stack direction="row" spacing={2} sx={{ mb: 1.5 }}>
            <TextField
              label={t('observation.unit')} select size="small" sx={{ width: 160 }}
              value={currentUnit}
              onChange={(e) => onChange({ ...observation, observationUnit: e.target.value })}
            >
              {DURATION_UNITS.map((u) => (
                <MenuItem key={u} value={u}>{u}</MenuItem>
              ))}
            </TextField>
            <TextField
              label={t('observation.property')} select size="small" sx={{ width: 280 }}
              value={currentProperty}
              onChange={(e) => onChange({ ...observation, observationProperty: e.target.value })}
            >
              {durationProps.map((p) => (
                <MenuItem key={p} value={p}>{`${paramName}.${p}`}</MenuItem>
              ))}
            </TextField>
          </Stack>
          <Alert severity="info" variant="outlined" sx={{ py: 0.25 }}>
            <Typography variant="body2" fontFamily="monospace" fontSize={13}>
              {t('observation.durationPreview', { unit: currentUnit, param: paramName, property: currentProperty })}
            </Typography>
          </Alert>
        </Box>
      )}

      {obsType === 'quantity' && (
        <Box sx={{ mb: 1 }}>
          <Stack direction="row" spacing={2} sx={{ mb: 1.5 }}>
            <TextField
              label={t('observation.property')} select size="small" sx={{ width: 280 }}
              value={currentProperty}
              onChange={(e) => onChange({ ...observation, observationProperty: e.target.value })}
            >
              {quantityProps.map((p) => (
                <MenuItem key={p} value={p}>{`${paramName}.${p}`}</MenuItem>
              ))}
            </TextField>
          </Stack>
          <Alert severity="info" variant="outlined" sx={{ py: 0.25 }}>
            <Typography variant="body2" fontFamily="monospace" fontSize={13}>
              {t('observation.quantityPreview', { param: paramName, property: currentProperty })}
            </Typography>
          </Alert>
        </Box>
      )}

      {obsType === 'criteria' && (
        <EcqmPopulationTreeEditor
          label={t('observation.criteria')}
          tree={observation.criteria}
          templates={templates}
          modifiers={modifiers}
          onUpdateTree={(updated) => onChange({ ...observation, criteria: updated })}
        />
      )}
    </Paper>
  )
}
