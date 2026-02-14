import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Stack, TextField,
  Typography, Box, LinearProgress, IconButton, Divider, Switch, FormControlLabel,
} from '@mui/material'
import { Close as CloseIcon, Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { Artifact } from '../../types/authoring'

interface CpgMetadataEditorProps {
  open: boolean
  onClose: () => void
  artifact: Artifact
  onUpdate: (updates: Partial<Artifact>) => void
}

interface ContactDetail {
  name?: string
  telecom?: Array<{ system?: string; value?: string }>
}

export default function CpgMetadataEditor({ open, onClose, artifact, onUpdate }: CpgMetadataEditorProps) {
  const [local, setLocal] = useState<Record<string, unknown>>({})

  useEffect(() => {
    if (open) {
      setLocal({
        url: artifact.url || '',
        publisher: artifact.publisher || '',
        purpose: artifact.purpose || '',
        usageInfo: artifact.usageInfo || '',
        copyright: artifact.copyright || '',
        experimental: artifact.experimental || false,
        approvalDate: artifact.approvalDate || '',
        lastReviewDate: artifact.lastReviewDate || '',
        effectivePeriodStart: artifact.effectivePeriodStart || '',
        effectivePeriodEnd: artifact.effectivePeriodEnd || '',
        author: artifact.author || [],
        reviewer: artifact.reviewer || [],
        endorser: artifact.endorser || [],
        relatedArtifact: artifact.relatedArtifact || [],
      })
    }
  }, [open, artifact])

  const update = (key: string, value: unknown) => {
    setLocal((prev) => ({ ...prev, [key]: value }))
  }

  const handleSave = () => {
    onUpdate(local as Partial<Artifact>)
    onClose()
  }

  // Calculate completion score
  const fields = ['url', 'publisher', 'purpose', 'copyright', 'approvalDate', 'lastReviewDate',
    'effectivePeriodStart', 'effectivePeriodEnd']
  const filledCount = fields.filter((f) => {
    const v = local[f]
    return v && typeof v === 'string' && v.trim().length > 0
  }).length
  const listFields = ['author', 'reviewer', 'endorser']
  const filledLists = listFields.filter((f) => Array.isArray(local[f]) && (local[f] as unknown[]).length > 0).length
  const totalFields = fields.length + listFields.length
  const progress = Math.round(((filledCount + filledLists) / totalFields) * 100)

  const contacts = (key: string) => {
    const list = (local[key] as ContactDetail[]) || []
    return {
      list,
      add: () => update(key, [...list, { name: '' }]),
      remove: (i: number) => update(key, list.filter((_, idx) => idx !== i)),
      updateName: (i: number, name: string) =>
        update(key, list.map((c, idx) => (idx === i ? { ...c, name } : c))),
    }
  }

  const relatedArtifacts = (local.relatedArtifact as Array<Record<string, unknown>>) || []

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        CPG on FHIR Metadata
        <IconButton onClick={onClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {/* Progress bar */}
        <Box sx={{ mb: 3 }}>
          <Stack direction="row" justifyContent="space-between" mb={0.5}>
            <Typography variant="caption" color="text.secondary">Completion</Typography>
            <Typography variant="caption" color="text.secondary">{progress}%</Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={progress}
            sx={{ height: 8, borderRadius: 4 }}
          />
        </Box>

        <Stack spacing={2.5}>
          {/* Basic Info */}
          <TextField
            label="Canonical URL"
            size="small"
            fullWidth
            value={local.url || ''}
            onChange={(e) => update('url', e.target.value)}
            placeholder="https://example.org/Library/MyArtifact"
          />
          <TextField
            label="Publisher"
            size="small"
            fullWidth
            value={local.publisher || ''}
            onChange={(e) => update('publisher', e.target.value)}
          />
          <TextField
            label="Purpose"
            size="small"
            fullWidth
            multiline
            minRows={2}
            value={local.purpose || ''}
            onChange={(e) => update('purpose', e.target.value)}
          />
          <TextField
            label="Usage / Description"
            size="small"
            fullWidth
            multiline
            minRows={2}
            value={local.usageInfo || ''}
            onChange={(e) => update('usageInfo', e.target.value)}
          />
          <TextField
            label="Copyright"
            size="small"
            fullWidth
            value={local.copyright || ''}
            onChange={(e) => update('copyright', e.target.value)}
          />
          <FormControlLabel
            control={
              <Switch
                checked={!!local.experimental}
                onChange={(e) => update('experimental', e.target.checked)}
              />
            }
            label="Experimental"
          />

          <Divider />

          {/* Dates */}
          <Typography variant="subtitle2">Dates</Typography>
          <Stack direction="row" spacing={2}>
            <TextField
              label="Approval Date"
              type="date"
              size="small"
              fullWidth
              value={local.approvalDate || ''}
              onChange={(e) => update('approvalDate', e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Last Review Date"
              type="date"
              size="small"
              fullWidth
              value={local.lastReviewDate || ''}
              onChange={(e) => update('lastReviewDate', e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField
              label="Effective Period Start"
              type="date"
              size="small"
              fullWidth
              value={local.effectivePeriodStart || ''}
              onChange={(e) => update('effectivePeriodStart', e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Effective Period End"
              type="date"
              size="small"
              fullWidth
              value={local.effectivePeriodEnd || ''}
              onChange={(e) => update('effectivePeriodEnd', e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
          </Stack>

          <Divider />

          {/* Contacts: Author, Reviewer, Endorser */}
          {(['author', 'reviewer', 'endorser'] as const).map((key) => {
            const c = contacts(key)
            return (
              <Box key={key}>
                <Stack direction="row" alignItems="center" spacing={1} mb={1}>
                  <Typography variant="subtitle2" sx={{ textTransform: 'capitalize' }}>{key}s</Typography>
                  <IconButton size="small" onClick={c.add} aria-label={`Add ${key}`}><AddIcon fontSize="small" /></IconButton>
                </Stack>
                {c.list.map((contact, i) => (
                  <Stack key={i} direction="row" spacing={1} mb={0.5} alignItems="center">
                    <TextField
                      size="small"
                      label={`${key} name`}
                      value={contact.name || ''}
                      onChange={(e) => c.updateName(i, e.target.value)}
                      sx={{ flex: 1 }}
                    />
                    <IconButton size="small" color="error" onClick={() => c.remove(i)} aria-label={`Remove ${key}`}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </Box>
            )
          })}

          <Divider />

          {/* Related Artifacts */}
          <Box>
            <Stack direction="row" alignItems="center" spacing={1} mb={1}>
              <Typography variant="subtitle2">Related Artifacts</Typography>
              <IconButton size="small" onClick={() => update('relatedArtifact', [...relatedArtifacts, { type: 'citation', display: '', url: '' }])} aria-label="Add related artifact">
                <AddIcon fontSize="small" />
              </IconButton>
            </Stack>
            {relatedArtifacts.map((ra, i) => (
              <Stack key={i} direction="row" spacing={1} mb={0.5} alignItems="center">
                <TextField
                  size="small"
                  label="Type"
                  select
                  value={ra.type || 'citation'}
                  onChange={(e) => {
                    const updated = [...relatedArtifacts]
                    updated[i] = { ...updated[i], type: e.target.value }
                    update('relatedArtifact', updated)
                  }}
                  sx={{ minWidth: 130 }}
                >
                  {['documentation', 'justification', 'citation', 'predecessor', 'successor', 'derived-from', 'depends-on', 'composed-of'].map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </TextField>
                <TextField
                  size="small"
                  label="Display"
                  value={(ra.display as string) || ''}
                  onChange={(e) => {
                    const updated = [...relatedArtifacts]
                    updated[i] = { ...updated[i], display: e.target.value }
                    update('relatedArtifact', updated)
                  }}
                  sx={{ flex: 1 }}
                />
                <TextField
                  size="small"
                  label="URL"
                  value={(ra.url as string) || ''}
                  onChange={(e) => {
                    const updated = [...relatedArtifacts]
                    updated[i] = { ...updated[i], url: e.target.value }
                    update('relatedArtifact', updated)
                  }}
                  sx={{ flex: 1 }}
                />
                <IconButton size="small" color="error" onClick={() => update('relatedArtifact', relatedArtifacts.filter((_, idx) => idx !== i))} aria-label="Remove related artifact">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSave}>Save Metadata</Button>
      </DialogActions>
    </Dialog>
  )
}
