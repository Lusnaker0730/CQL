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
  return (
    <Box sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
      <Stack direction="row" alignItems="center" spacing={2}>
        <Tooltip title="Back to list">
          <IconButton onClick={onBack}><BackIcon /></IconButton>
        </Tooltip>
        <Box sx={{ flex: 1 }}>
          <Typography variant="h6" fontWeight={600}>{artifact.name}</Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
            <Chip label={`v${artifact.version}`} size="small" variant="outlined" />
            <Chip label={artifact.scoringType} size="small" color="primary" />
            <Chip label={artifact.status} size="small" variant="outlined" />
            {artifact.populationBasis !== 'boolean' && (
              <Chip label={`${artifact.populationBasis}-based`} size="small" color="secondary" />
            )}
            {artifact.publishedMeasureId && (
              <Chip label="Published" size="small" color="success" />
            )}
          </Stack>
        </Box>
        <Button
          variant="contained"
          startIcon={<PublishIcon />}
          onClick={onPublish}
          disabled={publishing}
        >
          {publishing ? 'Publishing...' : 'Publish'}
        </Button>
      </Stack>
    </Box>
  )
}
