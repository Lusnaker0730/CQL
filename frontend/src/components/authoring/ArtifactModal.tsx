import { useState } from 'react'
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
  title = 'New CDS Artifact',
}: ArtifactModalProps) {
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
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Artifact Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            autoFocus
            size="small"
            placeholder="e.g., Statin Use for ASCVD Prevention"
          />
          <TextField
            label="Version"
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            fullWidth
            size="small"
            placeholder="1.0.0"
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            rows={3}
            size="small"
            placeholder="Describe the purpose of this CDS artifact..."
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <GradientButton onClick={handleSubmit} disabled={!name.trim()}>
          Create
        </GradientButton>
      </DialogActions>
    </Dialog>
  )
}
