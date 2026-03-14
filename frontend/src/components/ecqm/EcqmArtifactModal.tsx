import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, MenuItem, Stack,
} from '@mui/material'
import { SCORING_TYPES, POPULATION_BASIS_OPTIONS } from '../../constants/ecqmConstants'
import { ECQM } from '../../constants/fieldConstraints'

interface Props {
  open: boolean
  onClose: () => void
  onSubmit: (data: { name: string; scoringType: string; populationBasis: string; description?: string }) => void
}

export default function EcqmArtifactModal({ open, onClose, onSubmit }: Props) {
  const { t } = useTranslation('ecqm')
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
      <DialogTitle>{t('modal.createTitle')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label={t('modal.name')} required fullWidth autoFocus
            value={name} onChange={(e) => setName(e.target.value)}
            inputProps={{ maxLength: ECQM.name.maxLength }}
          />
          <TextField
            label={t('modal.scoringType')} select fullWidth
            value={scoringType} onChange={(e) => setScoringType(e.target.value)}
          >
            {SCORING_TYPES.map((st) => (
              <MenuItem key={st.id} value={st.id}>{t(`scoring.${st.id === 'continuous-variable' ? 'continuousVariable' : st.id}`)}</MenuItem>
            ))}
          </TextField>
          <TextField
            label={t('modal.populationBasis')} select fullWidth
            value={populationBasis} onChange={(e) => setPopulationBasis(e.target.value)}
          >
            {POPULATION_BASIS_OPTIONS.map((pb) => (
              <MenuItem key={pb.id} value={pb.id}>{t(`populationBasisOptions.${pb.id}`)}</MenuItem>
            ))}
          </TextField>
          <TextField
            label={t('modal.description')} fullWidth multiline rows={2}
            value={description} onChange={(e) => setDescription(e.target.value)}
            inputProps={{ maxLength: ECQM.description.maxLength }}
            helperText={`${description.length} / ${ECQM.description.maxLength}`}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.cancel')}</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!name.trim()}>{t('common:actions.create')}</Button>
      </DialogActions>
    </Dialog>
  )
}
