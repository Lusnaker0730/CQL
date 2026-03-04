import { useCallback } from 'react'
import {
  Box, Button,
  FormControlLabel, Stack, Switch, Typography,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import type { PopulationGroup, ObservationEntry } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { SCORING_POPULATIONS, REQUIRED_POPULATIONS, createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import { POPULATION_LABELS, type PopulationKey } from '../../types/ecqm'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'
import EcqmObservationEditor from './EcqmObservationEditor'

interface Props {
  group: PopulationGroup
  scoringType: string
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (updated: PopulationGroup) => void
}

function newId() {
  return 'obs-' + Date.now().toString(36)
}

export default function EcqmPopulationGroupEditor({
  group, scoringType, templates, modifiers, onChange,
}: Props) {
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

  const addObservation = useCallback(() => {
    const newObs: ObservationEntry = {
      observationId: newId(),
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
      aggregateMethod: 'Count',
      populationRef: 'measure-population',
    }
    onChange({
      ...group,
      observations: [...(group.observations || []), newObs],
    })
  }, [group, onChange])

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
            label="Use separate Initial Populations for Denominator and Numerator"
          />
        </Box>
      )}

      {/* Dual IP editors */}
      {dualIp && (
        <>
          <EcqmPopulationTreeEditor
            label="Initial Population 1 (Denominator)"
            required
            tree={group.initialPopulationDenom || (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => onChange({ ...group, initialPopulationDenom: tree })}
          />
          <EcqmPopulationTreeEditor
            label="Initial Population 2 (Numerator)"
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
            label={POPULATION_LABELS[key]}
            required={requiredPops.includes(key)}
            tree={(group.populations[key] as ConjunctionGroupType) || (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => updatePopulation(key, tree)}
          />
        ))}

      {/* Observations (continuous variable) */}
      {isCv && (
        <Box sx={{ mt: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" fontWeight={600}>Observations</Typography>
            <Button size="small" startIcon={<AddIcon />} onClick={addObservation}>
              Add Observation
            </Button>
          </Stack>
          {(group.observations || []).map((obs, idx) => (
            <EcqmObservationEditor
              key={obs.observationId}
              observation={obs}
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
