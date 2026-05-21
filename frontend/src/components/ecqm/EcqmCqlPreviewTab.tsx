import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import {
  Code as CodeIcon,
  CheckCircle as ValidateIcon,
  Publish as PublishIcon,
} from '@mui/icons-material'
import type { CqlTranslationResponse } from '../../types'
import { useGenerateEcqmCql, useValidateEcqmCql, usePublishEcqm } from '../../hooks/useEcqm'

interface Props {
  artifactId: number
  /**
   * PAT-129: timestamp of the last server-confirmed save. When this changes
   * we drop the locally cached CQL/validation/warnings — the artifact has
   * been edited since the last generate, so the displayed CQL is stale.
   */
  artifactUpdatedAt?: string
  onPublished?: () => void
}

export default function EcqmCqlPreviewTab({ artifactId, artifactUpdatedAt, onPublished }: Props) {
  const { t } = useTranslation('ecqm')
  const [cql, setCql] = useState<string | null>(null)
  const [warnings, setWarnings] = useState<string[]>([])
  const [validation, setValidation] = useState<CqlTranslationResponse | null>(null)
  // Track which version of the artifact produced the cached CQL so we can flag
  // it as stale when the artifact has been updated since.
  const [generatedAt, setGeneratedAt] = useState<string | undefined>()
  const generateCql = useGenerateEcqmCql()
  const validateCql = useValidateEcqmCql()
  const publish = usePublishEcqm()

  // Invalidate cached CQL when the artifact changes — the user almost
  // certainly wants to regenerate after editing.
  useEffect(() => {
    if (generatedAt && artifactUpdatedAt && artifactUpdatedAt !== generatedAt) {
      // Keep cql visible but mark stale; user can choose to regenerate.
      // We don't auto-fire generate to avoid surprising the user with a slow op.
    }
  }, [artifactUpdatedAt, generatedAt])

  const isStale = !!cql && !!generatedAt && !!artifactUpdatedAt && artifactUpdatedAt !== generatedAt

  const handleGenerate = () => {
    setValidation(null)
    generateCql.mutate(artifactId, {
      onSuccess: (data) => {
        setCql(data.cql)
        setWarnings(data.warnings || [])
        setGeneratedAt(artifactUpdatedAt)
      },
    })
  }

  const handleValidate = () => {
    validateCql.mutate(artifactId, {
      onSuccess: (data) => { setValidation(data) },
      onError: () => { setValidation(null) },
    })
  }

  const handlePublish = () => {
    publish.mutate(artifactId, {
      onSuccess: () => { onPublished?.() },
    })
  }

  const validationPassed = validation?.success === true
  const validationErrors = validation && !validation.success
    ? (validation.errors || []).map((e) => `Line ${e.startLine}:${e.startColumn} — ${e.message}`)
    : []
  const validationWarnings = validation?.warnings || []

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
          onClick={handleValidate} disabled={validateCql.isPending || isStale}
        >
          {validateCql.isPending ? t('cqlPreview.validating') : t('cqlPreview.validate')}
        </Button>
        <Button
          variant="contained" color="success" startIcon={<PublishIcon />}
          onClick={handlePublish} disabled={publish.isPending || isStale}
        >
          {publish.isPending ? t('cqlPreview.publishing') : t('cqlPreview.publishToMeasure')}
        </Button>
      </Stack>

      {generateCql.isPending && <CircularProgress size={24} />}

      {isStale && (
        <Alert severity="warning" sx={{ mb: 2 }} role="alert">
          {t('cqlPreview.staleWarning')}
        </Alert>
      )}

      {generateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {generateCql.error instanceof Error ? generateCql.error.message : t('cqlPreview.genFailed')}
        </Alert>
      )}

      {validationPassed && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('cqlPreview.validPassed')}</Alert>
      )}
      {validationErrors.length > 0 && (
        <Alert severity="error" sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('cqlPreview.validFailed')}</Typography>
          <ul style={{ margin: 0 }}>
            {validationErrors.map((e, i) => <li key={i}>{e}</li>)}
          </ul>
        </Alert>
      )}
      {validateCql.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('cqlPreview.validFailed')}</Alert>
      )}
      {validationWarnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('cqlPreview.warnings')}</Typography>
          <ul style={{ margin: 0 }}>
            {validationWarnings.map((w, i) => <li key={i}>{w.message}</li>)}
          </ul>
        </Alert>
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
          sx={(theme) => ({
            p: 2, fontFamily: 'monospace', fontSize: '0.85rem',
            whiteSpace: 'pre-wrap', overflow: 'auto', maxHeight: '70vh',
            bgcolor: theme.palette.mode === 'dark' ? 'grey.900' : 'grey.50',
            opacity: isStale ? 0.7 : 1,
          })}
        >
          {cql}
        </Paper>
      )}
    </Box>
  )
}
