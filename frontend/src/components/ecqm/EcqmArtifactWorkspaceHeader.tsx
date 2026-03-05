import { useTranslation } from 'react-i18next'
import {
  Box, Button, Chip, CircularProgress, IconButton, Stack, Typography, Tooltip,
} from '@mui/material'
import {
  ArrowBack as BackIcon,
  Publish as PublishIcon,
  Save as SaveIcon,
  CheckCircle as CheckIcon,
  ErrorOutline as ErrorIcon,
} from '@mui/icons-material'
import type { EcqmArtifact } from '../../types/ecqm'
import type { SaveStatus } from './EcqmArtifactWorkspace'

interface Props {
  artifact: EcqmArtifact
  saveStatus: SaveStatus
  onBack: () => void
  onSave: () => void
  onPublish: () => void
  publishing?: boolean
}

export default function EcqmArtifactWorkspaceHeader({
  artifact, saveStatus, onBack, onSave, onPublish, publishing,
}: Props) {
  const { t } = useTranslation('ecqm')

  const saveIndicator = (() => {
    switch (saveStatus) {
      case 'saving':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'text.secondary' }}>
            <CircularProgress size={14} color="inherit" />
            <Typography variant="caption">{t('workspace.saving')}</Typography>
          </Stack>
        )
      case 'saved':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'success.main' }}>
            <CheckIcon fontSize="small" />
            <Typography variant="caption">{t('workspace.saved')}</Typography>
          </Stack>
        )
      case 'dirty':
        return (
          <Typography variant="caption" sx={{ color: 'warning.main' }}>
            {t('workspace.unsavedChanges')}
          </Typography>
        )
      case 'error':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'error.main' }}>
            <ErrorIcon fontSize="small" />
            <Typography variant="caption">{t('workspace.saveFailed')}</Typography>
          </Stack>
        )
      default:
        return null
    }
  })()

  return (
    <Box sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
      <Stack direction="row" alignItems="center" spacing={2}>
        <Tooltip title={t('header.backToList')}>
          <IconButton onClick={onBack}><BackIcon /></IconButton>
        </Tooltip>
        <Box sx={{ flex: 1 }}>
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <Typography variant="h6" fontWeight={600}>{artifact.name}</Typography>
            {saveIndicator}
          </Stack>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
            <Chip label={`v${artifact.version}`} size="small" variant="outlined" />
            <Chip label={artifact.scoringType} size="small" color="primary" />
            <Chip label={artifact.status} size="small" variant="outlined" />
            {artifact.populationBasis !== 'boolean' && (
              <Chip label={t('header.basedSuffix', { basis: artifact.populationBasis })} size="small" color="secondary" />
            )}
            {artifact.publishedMeasureId && (
              <Chip label={t('list.published')} size="small" color="success" />
            )}
          </Stack>
        </Box>
        <Tooltip title="Ctrl+S">
          <span>
            <Button
              variant="outlined"
              startIcon={saveStatus === 'saving' ? <CircularProgress size={14} /> : <SaveIcon />}
              onClick={onSave}
              disabled={saveStatus === 'idle' || saveStatus === 'saved' || saveStatus === 'saving'}
            >
              {t('header.save')}
            </Button>
          </span>
        </Tooltip>
        <Button
          variant="contained"
          startIcon={<PublishIcon />}
          onClick={onPublish}
          disabled={publishing}
        >
          {publishing ? t('header.publishing') : t('header.publish')}
        </Button>
      </Stack>
    </Box>
  )
}
