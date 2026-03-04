import { MenuItem, Stack, TextField, Typography, IconButton, Paper } from '@mui/material'
import { Delete as DeleteIcon } from '@mui/icons-material'
import type { ObservationEntry } from '../../types/ecqm'
import type { FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { AGGREGATE_METHODS } from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'

interface Props {
  observation: ObservationEntry
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (updated: ObservationEntry) => void
  onRemove: () => void
}

export default function EcqmObservationEditor({ observation, templates, modifiers, onChange, onRemove }: Props) {
  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="subtitle2" fontWeight={600}>Measure Observation</Typography>
        <IconButton size="small" color="error" onClick={onRemove}><DeleteIcon fontSize="small" /></IconButton>
      </Stack>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Aggregate Method" select size="small" sx={{ width: 200 }}
          value={observation.aggregateMethod}
          onChange={(e) => onChange({ ...observation, aggregateMethod: e.target.value })}
        >
          {AGGREGATE_METHODS.map((m) => (
            <MenuItem key={m} value={m}>{m}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="Population Reference" size="small" sx={{ flex: 1 }}
          value={observation.populationRef || ''}
          onChange={(e) => onChange({ ...observation, populationRef: e.target.value })}
          placeholder="measure-population"
        />
      </Stack>
      <EcqmPopulationTreeEditor
        label="Observation Criteria"
        tree={observation.criteria}
        templates={templates}
        modifiers={modifiers}
        onUpdateTree={(updated) => onChange({ ...observation, criteria: updated })}
      />
    </Paper>
  )
}
