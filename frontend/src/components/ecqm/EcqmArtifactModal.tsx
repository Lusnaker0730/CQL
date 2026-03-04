import { useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, MenuItem, Stack,
} from '@mui/material'
import { SCORING_TYPES, POPULATION_BASIS_OPTIONS } from '../../constants/ecqmConstants'

interface Props {
  open: boolean
  onClose: () => void
  onSubmit: (data: { name: string; scoringType: string; populationBasis: string; description?: string }) => void
}

export default function EcqmArtifactModal({ open, onClose, onSubmit }: Props) {
  const [name, setName] = useState('')
  const [scoringType, setScoringType] = useState('proportion')
  const [populationBasis, setPopulationBasis] = useState('boolean')
  const [description, setDescription] = useState('')

  const handleSubmit = () => {
    if (!name.trim()) return
    onSubmit({ name: name.trim(), scoringType, populationBasis, description: description.trim() || undefined })
    setName('')
    setScoringType('proportion')
    setPopulationBasis('boolean')
    setDescription('')
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create eCQM Artifact</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Name" required fullWidth autoFocus
            value={name} onChange={(e) => setName(e.target.value)}
          />
          <TextField
            label="Scoring Type" select fullWidth
            value={scoringType} onChange={(e) => setScoringType(e.target.value)}
          >
            {SCORING_TYPES.map((st) => (
              <MenuItem key={st.id} value={st.id}>{st.label}</MenuItem>
            ))}
          </TextField>
          <TextField
            label="Population Basis" select fullWidth
            value={populationBasis} onChange={(e) => setPopulationBasis(e.target.value)}
          >
            {POPULATION_BASIS_OPTIONS.map((pb) => (
              <MenuItem key={pb.id} value={pb.id}>{pb.label}</MenuItem>
            ))}
          </TextField>
          <TextField
            label="Description" fullWidth multiline rows={2}
            value={description} onChange={(e) => setDescription(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!name.trim()}>Create</Button>
      </DialogActions>
    </Dialog>
  )
}
