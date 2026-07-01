import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('authoring')
  const { t: tc } = useTranslation('common')
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
        {t('cpg.title')}
        <IconButton onClick={onClose} size="small" aria-label={tc('actions.close')}><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {/* Progress bar */}
        <Box sx={{ mb: 3 }}>
          <Stack
            direction="row"
            sx={{
              justifyContent: "space-between",
              mb: 0.5
            }}>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('cpg.completion')}</Typography>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{progress}%</Typography>
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
            label={t('cpg.canonicalUrl')}
            size="small"
            fullWidth
            value={local.url || ''}
            onChange={(e) => update('url', e.target.value)}
            placeholder={t('cpg.canonicalPlaceholder')}
          />
          <TextField
            label={t('cpg.publisher')}
            size="small"
            fullWidth
            value={local.publisher || ''}
            onChange={(e) => update('publisher', e.target.value)}
          />
          <TextField
            label={t('cpg.purpose')}
            size="small"
            fullWidth
            multiline
            minRows={2}
            value={local.purpose || ''}
            onChange={(e) => update('purpose', e.target.value)}
          />
          <TextField
            label={t('cpg.usageDescription')}
            size="small"
            fullWidth
            multiline
            minRows={2}
            value={local.usageInfo || ''}
            onChange={(e) => update('usageInfo', e.target.value)}
          />
          <TextField
            label={t('cpg.copyright')}
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
            label={t('cpg.experimental')}
          />

          <Divider />

          {/* Dates */}
          <Typography variant="subtitle2">{t('cpg.dates')}</Typography>
          <Stack direction="row" spacing={2}>
            <TextField
              label={t('cpg.approvalDate')}
              type="date"
              size="small"
              fullWidth
              value={local.approvalDate || ''}
              onChange={(e) => update('approvalDate', e.target.value)}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
            <TextField
              label={t('cpg.lastReviewDate')}
              type="date"
              size="small"
              fullWidth
              value={local.lastReviewDate || ''}
              onChange={(e) => update('lastReviewDate', e.target.value)}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField
              label={t('cpg.effectivePeriodStart')}
              type="date"
              size="small"
              fullWidth
              value={local.effectivePeriodStart || ''}
              onChange={(e) => update('effectivePeriodStart', e.target.value)}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
            <TextField
              label={t('cpg.effectivePeriodEnd')}
              type="date"
              size="small"
              fullWidth
              value={local.effectivePeriodEnd || ''}
              onChange={(e) => update('effectivePeriodEnd', e.target.value)}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
          </Stack>

          <Divider />

          {/* Contacts: Author, Reviewer, Endorser */}
          {(['author', 'reviewer', 'endorser'] as const).map((key) => {
            const c = contacts(key)
            const contactLabels = {
              author: { title: t('cpg.authors'), add: t('cpg.addAuthor'), name: t('cpg.authorName'), remove: t('cpg.removeAuthor') },
              reviewer: { title: t('cpg.reviewers'), add: t('cpg.addReviewer'), name: t('cpg.reviewerName'), remove: t('cpg.removeReviewer') },
              endorser: { title: t('cpg.endorsers'), add: t('cpg.addEndorser'), name: t('cpg.endorserName'), remove: t('cpg.removeEndorser') },
            }
            const labels = contactLabels[key]
            return (
              <Box key={key}>
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{
                    alignItems: "center",
                    mb: 1
                  }}>
                  <Typography variant="subtitle2">{labels.title}</Typography>
                  <IconButton size="small" onClick={c.add} aria-label={labels.add}><AddIcon fontSize="small" /></IconButton>
                </Stack>
                {c.list.map((contact, i) => (
                  <Stack
                    key={i}
                    direction="row"
                    spacing={1}
                    sx={{
                      mb: 0.5,
                      alignItems: "center"
                    }}>
                    <TextField
                      size="small"
                      label={labels.name}
                      value={contact.name || ''}
                      onChange={(e) => c.updateName(i, e.target.value)}
                      sx={{ flex: 1 }}
                    />
                    <IconButton size="small" color="error" onClick={() => c.remove(i)} aria-label={labels.remove}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </Box>
            );
          })}

          <Divider />

          {/* Related Artifacts */}
          <Box>
            <Stack
              direction="row"
              spacing={1}
              sx={{
                alignItems: "center",
                mb: 1
              }}>
              <Typography variant="subtitle2">{t('cpg.relatedArtifacts')}</Typography>
              <IconButton size="small" onClick={() => update('relatedArtifact', [...relatedArtifacts, { type: 'citation', display: '', url: '' }])} aria-label={t('cpg.addRelatedArtifact')}>
                <AddIcon fontSize="small" />
              </IconButton>
            </Stack>
            {relatedArtifacts.map((ra, i) => (
              <Stack
                key={i}
                direction="row"
                spacing={1}
                sx={{
                  mb: 0.5,
                  alignItems: "center"
                }}>
                <TextField
                  size="small"
                  label={t('cpg.typeLabel')}
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
                  label={t('cpg.displayLabel')}
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
                  label={t('cpg.urlLabel')}
                  value={(ra.url as string) || ''}
                  onChange={(e) => {
                    const updated = [...relatedArtifacts]
                    updated[i] = { ...updated[i], url: e.target.value }
                    update('relatedArtifact', updated)
                  }}
                  sx={{ flex: 1 }}
                />
                <IconButton size="small" color="error" onClick={() => update('relatedArtifact', relatedArtifacts.filter((_, idx) => idx !== i))} aria-label={t('cpg.removeRelatedArtifact')}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{tc('actions.cancel')}</Button>
        <Button variant="contained" onClick={handleSave}>{t('cpg.saveMetadata')}</Button>
      </DialogActions>
    </Dialog>
  );
}
