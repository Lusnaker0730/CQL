import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
} from '@mui/material'
import GradientButton from '../common/GradientButton'
import type { ArtifactRequest } from '../../types/authoring'
import { ARTIFACT } from '../../constants/fieldConstraints'

interface ArtifactModalProps {
  open: boolean
  onClose: () => void
  onSubmit: (request: ArtifactRequest) => void
  initialValues?: Partial<ArtifactRequest>
  title?: string
}

export default function ArtifactModal({
  open,
  onClose,
  onSubmit,
  initialValues,
  title,
}: ArtifactModalProps) {
  const { t } = useTranslation('authoring')
  const { t: tc } = useTranslation('common')
  const [name, setName] = useState(initialValues?.name || '')
  const [version, setVersion] = useState(initialValues?.version || '1.0.0')
  const [description, setDescription] = useState(initialValues?.description || '')

  const handleSubmit = () => {
    if (!name.trim()) return
    onSubmit({
      name: name.trim(),
      version: version.trim() || '1.0.0',
      description: description.trim() || undefined,
    })
    setName('')
    setVersion('1.0.0')
    setDescription('')
  }

  const handleClose = () => {
    setName(initialValues?.name || '')
    setVersion(initialValues?.version || '1.0.0')
    setDescription(initialValues?.description || '')
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title || t('modal.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label={t('modal.nameLabel')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            autoFocus
            size="small"
            placeholder={t('modal.namePlaceholder')}
            slotProps={{
              htmlInput: { maxLength: ARTIFACT.name.maxLength }
            }}
          />
          <TextField
            label={t('modal.versionLabel')}
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            fullWidth
            size="small"
            placeholder="1.0.0"
            slotProps={{
              htmlInput: { maxLength: ARTIFACT.version.maxLength }
            }}
          />
          <TextField
            label={t('modal.descriptionLabel')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            rows={3}
            size="small"
            placeholder={t('modal.descriptionPlaceholder')}
            helperText={`${description.length} / ${ARTIFACT.description.maxLength}`}
            slotProps={{
              htmlInput: { maxLength: ARTIFACT.description.maxLength }
            }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{tc('actions.cancel')}</Button>
        <GradientButton onClick={handleSubmit} disabled={!name.trim()}>
          {t('modal.create')}
        </GradientButton>
      </DialogActions>
    </Dialog>
  );
}
