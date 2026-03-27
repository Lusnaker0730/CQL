import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button,
  FormControlLabel, Stack, Switch, TextField, Typography,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import type { PopulationGroup, ObservationEntry } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { SCORING_POPULATIONS, REQUIRED_POPULATIONS, createEmptyConjunctionGroup, DURATION_DEFAULT_PROPERTY } from '../../constants/ecqmConstants'
import type { PopulationKey } from '../../types/ecqm'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'
import EcqmObservationEditor from './EcqmObservationEditor'

interface Props {
  group: PopulationGroup
  scoringType: string
  populationBasis: string
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (updated: PopulationGroup) => void
}

function newId() {
  return 'obs-' + Date.now().toString(36)
}

export default function EcqmPopulationGroupEditor({
  group, scoringType, populationBasis, templates, modifiers, onChange,
}: Props) {
  const { t } = useTranslation('ecqm')
  const relevantPops = SCORING_POPULATIONS[scoringType] || []
  const requiredPops = REQUIRED_POPULATIONS[scoringType] || []
  const isRatio = scoringType === 'ratio'
  const isCv = scoringType === 'continuous-variable'
  const dualIp = isRatio && !!group.initialPopulationDenom && !!group.initialPopulationNumer

  const updatePopulation = useCallback((key: PopulationKey, tree: ConjunctionGroupType) => {
    onChange({
      ...group,
      populations: { ...group.populations, [key]: tree },
    })
  }, [group, onChange])

  const toggleDualIp = useCallback((enabled: boolean) => {
    if (enabled) {
      onChange({
        ...group,
        initialPopulationDenom: createEmptyConjunctionGroup() as ConjunctionGroupType,
        initialPopulationNumer: createEmptyConjunctionGroup() as ConjunctionGroupType,
      })
    } else {
      onChange({
        ...group,
        initialPopulationDenom: null,
        initialPopulationNumer: null,
      })
    }
  }, [group, onChange])

  const showObservations = isCv || isRatio

  const addObservation = useCallback(() => {
    const newObs: ObservationEntry = {
      observationId: newId(),
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
      aggregateMethod: isRatio ? 'Sum' : 'Average',
      populationRef: isRatio ? 'denominator' : 'measure-population',
      observationType: 'duration',
      observationUnit: isRatio ? 'years' : 'days',
      observationProperty: DURATION_DEFAULT_PROPERTY[populationBasis] || 'period',
    }
    onChange({
      ...group,
      observations: [...(group.observations || []), newObs],
    })
  }, [group, onChange, populationBasis])

  const updateObservation = useCallback((idx: number, updated: ObservationEntry) => {
    const obs = [...(group.observations || [])]
    obs[idx] = updated
    onChange({ ...group, observations: obs })
  }, [group, onChange])

  const removeObservation = useCallback((idx: number) => {
    const obs = [...(group.observations || [])]
    obs.splice(idx, 1)
    onChange({ ...group, observations: obs })
  }, [group, onChange])

  return (
    <Box>
      {/* Dual IP toggle (ratio only) */}
      {isRatio && (
        <Box sx={{ mb: 2 }}>
          <FormControlLabel
            control={<Switch checked={dualIp} onChange={(e) => toggleDualIp(e.target.checked)} />}
            label={t('populationGroups.useSeparateIp')}
          />
        </Box>
      )}

      {/* Dual IP editors */}
      {dualIp && (
        <>
          <EcqmPopulationTreeEditor
            label={t('populationGroups.ip1Label')}
            required
            tree={group.initialPopulationDenom || (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => onChange({ ...group, initialPopulationDenom: tree })}
          />
          <EcqmPopulationTreeEditor
            label={t('populationGroups.ip2Label')}
            required
            tree={group.initialPopulationNumer || (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => onChange({ ...group, initialPopulationNumer: tree })}
          />
        </>
      )}

      {/* Standard population editors */}
      {relevantPops
        .filter((key) => !(dualIp && key === 'initial-population'))
        .map((key) => (
          <EcqmPopulationTreeEditor
            key={key}
            label={t(`populationLabels.${key}`)}
            required={requiredPops.includes(key)}
            tree={(group.populations[key] as ConjunctionGroupType) || (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => updatePopulation(key, tree)}
          />
        ))}

      {showObservations && (
        <Box sx={{ mt: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" fontWeight={600}>{t('populationGroups.observations')}</Typography>
            <Button size="small" startIcon={<AddIcon />} onClick={addObservation}>
              {t('populationGroups.addObservation')}
            </Button>
          </Stack>
          {isRatio && (group.observations || []).length > 0 && (
            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <TextField
                label={t('populationGroups.rateMultiplier')}
                type="number"
                size="small"
                sx={{ width: 160 }}
                value={group.rateMultiplier ?? 1000}
                onChange={(e) => onChange({ ...group, rateMultiplier: parseFloat(e.target.value) || 1 })}
              />
              <TextField
                label={t('populationGroups.scoringUnit')}
                size="small"
                sx={{ flex: 1 }}
                value={group.scoringUnit ?? ''}
                onChange={(e) => onChange({ ...group, scoringUnit: e.target.value })}
                placeholder={t('populationGroups.scoringUnitPlaceholder')}
              />
            </Stack>
          )}
          {(group.observations || []).map((obs, idx) => (
            <EcqmObservationEditor
              key={obs.observationId}
              observation={obs}
              populationBasis={populationBasis}
              templates={templates}
              modifiers={modifiers}
              onChange={(updated) => updateObservation(idx, updated)}
              onRemove={() => removeObservation(idx)}
            />
          ))}
        </Box>
      )}
    </Box>
  )
}
