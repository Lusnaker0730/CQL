import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Stack, Alert, CircularProgress, Typography } from '@mui/material'
import { WarningAmber as StaleIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useGenerateArtifactCql, useValidateArtifactCql } from '../../../hooks/useArtifactCql'
import type { CqlTranslationResponse } from '../../../types'
import { codeBlockSx } from '../../../constants/authoringConstants'

interface CqlPreviewPanelProps {
  artifactId: number
  onSaveBeforeGenerate?: () => Promise<void>
  isDirty?: boolean
}

export default function CqlPreviewPanel({ artifactId, onSaveBeforeGenerate, isDirty }: CqlPreviewPanelProps) {
  const { t } = useTranslation('authoring')
  const [cql, setCql] = useState<string | null>(null)
  const [validation, setValidation] = useState<CqlTranslationResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [cqlIsStale, setCqlIsStale] = useState(false)

  const generateMutation = useGenerateArtifactCql()
  const validateMutation = useValidateArtifactCql()

  // Mark CQL as stale when artifact has been modified after generation
  useEffect(() => {
    if (isDirty && cql) {
      setCqlIsStale(true)
    }
  }, [isDirty, cql])

  const saveFirst = async () => {
    if (isDirty && onSaveBeforeGenerate) {
      setSaving(true)
      try {
        await onSaveBeforeGenerate()
      } finally {
        setSaving(false)
      }
    }
  }

  const handleGenerate = async () => {
    await saveFirst()
    generateMutation.mutate({ id: artifactId }, {
      onSuccess: (data) => {
        setCql(data.cql)
        setValidation(null)
        setCqlIsStale(false)
      },
    })
  }

  const handleValidate = async () => {
    await saveFirst()
    validateMutation.mutate(artifactId, {
      onSuccess: (data) => {
        setValidation(data)
        if (!cql) {
          generateMutation.mutate({ id: artifactId }, {
            onSuccess: (genData) => setCql(genData.cql),
          })
        }
      },
    })
  }

  const isLoading = generateMutation.isPending || validateMutation.isPending || saving
  const errors = validation?.errors || []
  const hasErrors = errors.length > 0

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {isDirty && (
        <Alert severity="info" sx={{ mb: 1 }} icon={false}>
          <Typography variant="caption">
            {t('cqlPreview.unsavedAutoSave')}
          </Typography>
        </Alert>
      )}

      {cqlIsStale && !isDirty && (
        <Alert severity="warning" sx={{ mb: 1 }} icon={<StaleIcon fontSize="small" />}>
          <Typography variant="caption">
            {t('cqlPreview.staleWarning')}
          </Typography>
        </Alert>
      )}

      <Stack direction="row" spacing={1} mb={2}>
        <GradientButton onClick={handleGenerate} disabled={isLoading}>
          {saving ? t('cqlPreview.saving') : t('cqlPreview.generateCql')}
        </GradientButton>
        <GradientButton onClick={handleValidate} disabled={isLoading}>
          {saving ? t('cqlPreview.saving') : t('cqlPreview.validate')}
        </GradientButton>
        {isLoading && <CircularProgress size={20} sx={{ alignSelf: 'center' }} />}
      </Stack>

      {generateMutation.isError && (
        <Alert severity="error" onClose={() => generateMutation.reset()} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('cqlPreview.genFailed')}</Typography>
          <Typography variant="body2" sx={{ mb: 0.5 }}>
            {(generateMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('cqlPreview.genFailedHint')}
          </Typography>
        </Alert>
      )}

      {validation && (
        <Box sx={{ mb: 2 }}>
          {validation.success ? (
            <Alert severity="success">{t('cqlPreview.validSuccess')}</Alert>
          ) : (
            <Alert severity="error">
              <Typography variant="subtitle2">
                {t('cqlPreview.validationErrors', { count: errors.length })}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                {t('cqlPreview.validationReview')}
              </Typography>
            </Alert>
          )}
          {hasErrors && (
            <Box sx={{ mt: 1, maxHeight: 200, overflow: 'auto', p: 1.5, backgroundColor: 'action.hover', borderRadius: 1 }}>
              {errors.map((err, i) => {
                const msg = typeof err === 'object' && err !== null && 'message' in err
                  ? (err as { message: string }).message
                  : String(err)
                return (
                  <Typography key={i} variant="caption" color="error" display="block" sx={{ fontFamily: 'monospace', mb: 0.5 }}>
                    {i + 1}. {msg}
                  </Typography>
                )
              })}
            </Box>
          )}
        </Box>
      )}

      {cql ? (
        <Box
          sx={{
            ...codeBlockSx,
            flex: 1,
            minHeight: 0,
            overflow: 'auto',
            p: 2,
          }}
        >
          {cql}
        </Box>
      ) : (
        <Box sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
          <Typography variant="body2">
            {t('cqlPreview.emptyState')}
          </Typography>
        </Box>
      )}
    </Box>
  )
}
