import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Card, CardContent, CardActions, Typography,
  Chip, IconButton, Stack, Tooltip, Dialog, DialogTitle,
  DialogContent, DialogContentText, DialogActions, Alert,
} from '@mui/material'
import {
  Add as AddIcon,
  ContentCopy as CopyIcon,
  Delete as DeleteIcon,
  Science as ScienceIcon,
} from '@mui/icons-material'
import type { EcqmArtifactSummary } from '../../types/ecqm'

interface Props {
  artifacts: EcqmArtifactSummary[]
  onSelect: (id: number) => void
  onCreate: () => void
  onDuplicate: (id: number) => void
  onDelete: (id: number) => void
}

const scoringColors: Record<string, 'primary' | 'secondary' | 'warning' | 'info'> = {
  proportion: 'primary',
  ratio: 'secondary',
  'continuous-variable': 'warning',
  cohort: 'info',
}

export default function EcqmArtifactList({ artifacts, onSelect, onCreate, onDuplicate, onDelete }: Props) {
  const { t } = useTranslation('ecqm')
  const [deleteId, setDeleteId] = useState<number | null>(null)

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3
        }}>
        <Typography variant="h5" sx={{
          fontWeight: 600
        }}>
          <ScienceIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
          {t('list.title')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={onCreate}>
          {t('list.newEcqm')}
        </Button>
      </Stack>
      {artifacts.length === 0 && (
        <Alert severity="info">{t('list.emptyState')}</Alert>
      )}
      <Stack spacing={2}>
        {artifacts.map((a) => (
          <Card
            key={a.id}
            sx={{ cursor: 'pointer', '&:hover': { boxShadow: 4 }, transition: 'box-shadow 0.2s' }}
            onClick={() => onSelect(a.id)}
          >
            <CardContent sx={{ pb: 0.5 }}>
              <Stack
                direction="row"
                sx={{
                  justifyContent: "space-between",
                  alignItems: "flex-start"
                }}>
                <Box>
                  <Typography variant="h6">{a.name}</Typography>
                  {a.description && (
                    <Typography
                      variant="body2"
                      sx={{
                        color: "text.secondary",
                        mt: 0.5
                      }}>
                      {a.description}
                    </Typography>
                  )}
                </Box>
                <Stack direction="row" spacing={1}>
                  <Chip label={a.scoringType} size="small" color={scoringColors[a.scoringType] || 'default'} />
                  <Chip label={`v${a.version}`} size="small" variant="outlined" />
                  <Chip label={a.status} size="small" variant="outlined" />
                  {a.publishedMeasureId && (
                    <Chip label={t('list.published')} size="small" color="success" />
                  )}
                </Stack>
              </Stack>
            </CardContent>
            <CardActions sx={{ justifyContent: 'flex-end' }}>
              <Tooltip title={t('list.duplicate')}>
                <IconButton size="small" onClick={(e) => { e.stopPropagation(); onDuplicate(a.id) }}>
                  <CopyIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title={t('list.delete')}>
                <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); setDeleteId(a.id) }}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </CardActions>
          </Card>
        ))}
      </Stack>
      <Dialog open={deleteId !== null} onClose={() => setDeleteId(null)}>
        <DialogTitle>{t('list.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('list.deleteConfirm')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteId(null)}>{t('common:actions.cancel')}</Button>
          <Button color="error" variant="contained" onClick={() => { if (deleteId) onDelete(deleteId); setDeleteId(null) }}>
            {t('list.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
