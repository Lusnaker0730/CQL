import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('ecqm')
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
          {generateCql.isPending ? t('cqlPreview.generating') : t('cqlPreview.generate')}
        </Button>
        <Button
          variant="outlined" startIcon={<ValidateIcon />}
          onClick={handleValidate} disabled={validateCql.isPending}
        >
          {validateCql.isPending ? t('cqlPreview.validating') : t('cqlPreview.validate')}
        </Button>
        <Button
          variant="contained" color="success" startIcon={<PublishIcon />}
          onClick={handlePublish} disabled={publish.isPending}
        >
          {publish.isPending ? t('cqlPreview.publishing') : t('cqlPreview.publishToMeasure')}
        </Button>
      </Stack>

      {generateCql.isPending && <CircularProgress size={24} />}

      {generateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {generateCql.error instanceof Error ? generateCql.error.message : t('cqlPreview.genFailed')}
        </Alert>
      )}

      {validateCql.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('cqlPreview.validPassed')}</Alert>
      )}
      {validateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('cqlPreview.validFailed')}</Alert>
      )}

      {publish.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('cqlPreview.publishedSuccess', { id: publish.data.measureDefinitionId })}
        </Alert>
      )}
      {publish.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('cqlPreview.publishFailed')}</Alert>
      )}

      {warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('cqlPreview.warnings')}</Typography>
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
