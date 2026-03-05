import { useTranslation } from 'react-i18next'
import { Box, Button, Chip, IconButton, Stack, Typography, Tooltip } from '@mui/material'
import { ArrowBack as BackIcon, Publish as PublishIcon } from '@mui/icons-material'
import type { EcqmArtifact } from '../../types/ecqm'

interface Props {
  artifact: EcqmArtifact
  onBack: () => void
  onPublish: () => void
  publishing?: boolean
}

export default function EcqmArtifactWorkspaceHeader({ artifact, onBack, onPublish, publishing }: Props) {
  const { t } = useTranslation('ecqm')
  return (
    <Box sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
      <Stack direction="row" alignItems="center" spacing={2}>
        <Tooltip title={t('header.backToList')}>
          <IconButton onClick={onBack}><BackIcon /></IconButton>
        </Tooltip>
        <Box sx={{ flex: 1 }}>
          <Typography variant="h6" fontWeight={600}>{artifact.name}</Typography>
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
