import { useState } from 'react'
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import {
  Code as CodeIcon,
  CheckCircle as ValidateIcon,
  Publish as PublishIcon,
} from '@mui/icons-material'
import { useGenerateEcqmCql, useValidateEcqmCql, usePublishEcqm } from '../../hooks/useEcqm'

interface Props {
  artifactId: number
  onPublished?: () => void
}

export default function EcqmCqlPreviewTab({ artifactId, onPublished }: Props) {
  const [cql, setCql] = useState<string | null>(null)
  const [warnings, setWarnings] = useState<string[]>([])
  const generateCql = useGenerateEcqmCql()
  const validateCql = useValidateEcqmCql()
  const publish = usePublishEcqm()

  const handleGenerate = () => {
    generateCql.mutate(artifactId, {
      onSuccess: (data) => {
        setCql(data.cql)
        setWarnings(data.warnings || [])
      },
    })
  }

  const handleValidate = () => {
    validateCql.mutate(artifactId)
  }

  const handlePublish = () => {
    publish.mutate(artifactId, {
      onSuccess: () => { onPublished?.() },
    })
  }

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <Button
          variant="contained" startIcon={<CodeIcon />}
          onClick={handleGenerate} disabled={generateCql.isPending}
        >
          {generateCql.isPending ? 'Generating...' : 'Generate CQL'}
        </Button>
        <Button
          variant="outlined" startIcon={<ValidateIcon />}
          onClick={handleValidate} disabled={validateCql.isPending}
        >
          {validateCql.isPending ? 'Validating...' : 'Validate'}
        </Button>
        <Button
          variant="contained" color="success" startIcon={<PublishIcon />}
          onClick={handlePublish} disabled={publish.isPending}
        >
          {publish.isPending ? 'Publishing...' : 'Publish to Measure'}
        </Button>
      </Stack>

      {generateCql.isPending && <CircularProgress size={24} />}

      {generateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {generateCql.error instanceof Error ? generateCql.error.message : 'CQL generation failed'}
        </Alert>
      )}

      {validateCql.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>CQL validation passed</Alert>
      )}
      {validateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>CQL validation failed</Alert>
      )}

      {publish.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Published to MeasureDefinition (ID: {publish.data.measureDefinitionId})
        </Alert>
      )}
      {publish.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>Publish failed</Alert>
      )}

      {warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="subtitle2">Warnings:</Typography>
          <ul style={{ margin: 0 }}>
            {warnings.map((w, i) => <li key={i}>{w}</li>)}
          </ul>
        </Alert>
      )}

      {cql && (
        <Paper
          variant="outlined"
          sx={{
            p: 2, fontFamily: 'monospace', fontSize: '0.85rem',
            whiteSpace: 'pre-wrap', overflow: 'auto', maxHeight: '70vh',
            bgcolor: 'grey.50',
          }}
        >
          {cql}
        </Paper>
      )}
    </Box>
  )
}
