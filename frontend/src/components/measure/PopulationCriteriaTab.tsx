import { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  MenuItem,
  Alert,
  IconButton,
  Paper,
  Divider,
  Chip,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Save as SaveIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { helpContent } from '../../constants/helpContent'
import type { MeasureDefinition, GroupDefinition, PopulationDefinition, StratifierDefinition } from '../../types'

interface PopulationCriteriaTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

const POPULATION_TYPES = [
  { value: 'initial-population', label: 'Initial Population' },
  { value: 'denominator', label: 'Denominator' },
  { value: 'denominator-exclusion', label: 'Denominator Exclusion' },
  { value: 'denominator-exception', label: 'Denominator Exception' },
  { value: 'numerator', label: 'Numerator' },
  { value: 'numerator-exclusion', label: 'Numerator Exclusion' },
]

function createEmptyGroup(index: number): GroupDefinition {
  return {
    groupId: `group-${index + 1}`,
    description: '',
    populations: [
      { populationType: 'initial-population', criteriaExpression: '' },
      { populationType: 'denominator', criteriaExpression: '' },
      { populationType: 'numerator', criteriaExpression: '' },
    ],
    stratifiers: [],
  }
}

export default function PopulationCriteriaTab({ measure, onMeasureUpdate }: PopulationCriteriaTabProps) {
  const queryClient = useQueryClient()
  const [groups, setGroups] = useState<GroupDefinition[]>(
    measure.groupDefinitions?.length ? measure.groupDefinitions : [createEmptyGroup(0)]
  )
  const [isDirty, setIsDirty] = useState(false)

  useUnsavedChangesGuard(isDirty)

  const { data: expressions = [] } = useQuery({
    queryKey: ['cql-expressions', measure.id],
    queryFn: () => measureApi.getCqlExpressions(measure.id!),
    enabled: !!measure.id && !!measure.cqlContent,
  })

  const expressionNames = expressions.map((e) => e.name)

  const saveMutation = useMutation({
    mutationFn: () =>
      measureApi.updateMeasure(measure.id!, { ...measure, groupDefinitions: groups }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      onMeasureUpdate(updated)
      setIsDirty(false)
    },
  })

  const updateGroup = (groupIdx: number, field: keyof GroupDefinition, value: unknown) => {
    setGroups((prev) => {
      const updated = [...prev]
      updated[groupIdx] = { ...updated[groupIdx], [field]: value }
      return updated
    })
    setIsDirty(true)
  }

  const updatePopulation = (groupIdx: number, popIdx: number, field: keyof PopulationDefinition, value: string) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops[popIdx] = { ...pops[popIdx], [field]: value }
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const addPopulation = (groupIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops.push({ populationType: '', criteriaExpression: '' })
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const removePopulation = (groupIdx: number, popIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops.splice(popIdx, 1)
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const addStratifier = (groupIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats.push({ stratifierId: `strat-${strats.length + 1}`, criteriaExpression: '', description: '' })
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const updateStratifier = (groupIdx: number, stratIdx: number, field: keyof StratifierDefinition, value: string) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats[stratIdx] = { ...strats[stratIdx], [field]: value }
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const removeStratifier = (groupIdx: number, stratIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats.splice(stratIdx, 1)
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const addGroup = () => {
    setGroups((prev) => [...prev, createEmptyGroup(prev.length)])
    setIsDirty(true)
  }

  const removeGroup = (idx: number) => {
    setGroups((prev) => prev.filter((_, i) => i !== idx))
    setIsDirty(true)
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Typography variant="h6">Population Criteria</Typography>
          <HelpTooltip text={helpContent.measures.populationCriteria} />
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button size="small" startIcon={<AddIcon />} onClick={addGroup}>
            Add Group
          </Button>
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!isDirty || saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? 'Saving...' : 'Save'}
          </GradientButton>
        </Stack>
      </Stack>

      {saveMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(saveMutation.error as Error).message}
        </Alert>
      )}

      {expressionNames.length === 0 && measure.cqlContent && (
        <Alert severity="info" sx={{ mb: 2 }}>
          No CQL expressions found. Save your CQL in the CQL tab first, then the expression dropdowns will be populated.
        </Alert>
      )}

      <Stack spacing={3}>
        {groups.map((group, groupIdx) => (
          <Paper key={groupIdx} variant="outlined" sx={{ p: 2 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="subtitle1" fontWeight={600}>
                  {group.groupId || `Group ${groupIdx + 1}`}
                </Typography>
                <Chip label={`${(group.populations || []).length} populations`} size="small" />
              </Stack>
              {groups.length > 1 && (
                <IconButton size="small" color="error" onClick={() => removeGroup(groupIdx)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>

            <TextField
              label="Group Description"
              size="small"
              fullWidth
              value={group.description || ''}
              onChange={(e) => updateGroup(groupIdx, 'description', e.target.value)}
              sx={{ mb: 2 }}
            />

            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Populations
            </Typography>

            <Stack spacing={1.5} mb={2}>
              {(group.populations || []).map((pop, popIdx) => (
                <Stack key={popIdx} direction="row" spacing={1} alignItems="center">
                  <TextField
                    select
                    label="Type"
                    size="small"
                    value={pop.populationType}
                    onChange={(e) => updatePopulation(groupIdx, popIdx, 'populationType', e.target.value)}
                    sx={{ minWidth: 200 }}
                  >
                    {POPULATION_TYPES.map((pt) => (
                      <MenuItem key={pt.value} value={pt.value}>
                        {pt.label}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    select={expressionNames.length > 0}
                    label="CQL Expression"
                    size="small"
                    fullWidth
                    value={pop.criteriaExpression}
                    onChange={(e) => updatePopulation(groupIdx, popIdx, 'criteriaExpression', e.target.value)}
                  >
                    {expressionNames.map((name) => (
                      <MenuItem key={name} value={name}>
                        {name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <IconButton size="small" color="error" onClick={() => removePopulation(groupIdx, popIdx)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={() => addPopulation(groupIdx)} sx={{ alignSelf: 'flex-start' }}>
                Add Population
              </Button>
            </Stack>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Stratifiers
            </Typography>

            <Stack spacing={1.5}>
              {(group.stratifiers || []).map((strat, stratIdx) => (
                <Stack key={stratIdx} direction="row" spacing={1} alignItems="center">
                  <TextField
                    label="Stratifier ID"
                    size="small"
                    value={strat.stratifierId}
                    onChange={(e) => updateStratifier(groupIdx, stratIdx, 'stratifierId', e.target.value)}
                    sx={{ minWidth: 140 }}
                  />
                  <TextField
                    select={expressionNames.length > 0}
                    label="CQL Expression"
                    size="small"
                    fullWidth
                    value={strat.criteriaExpression}
                    onChange={(e) => updateStratifier(groupIdx, stratIdx, 'criteriaExpression', e.target.value)}
                  >
                    {expressionNames.map((name) => (
                      <MenuItem key={name} value={name}>
                        {name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <IconButton size="small" color="error" onClick={() => removeStratifier(groupIdx, stratIdx)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={() => addStratifier(groupIdx)} sx={{ alignSelf: 'flex-start' }}>
                Add Stratifier
              </Button>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Box>
  )
}
