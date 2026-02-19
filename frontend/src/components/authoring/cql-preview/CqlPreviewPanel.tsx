import { useState, useEffect } from 'react'
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
            You have unsaved changes. They will be auto-saved when you generate or validate.
          </Typography>
        </Alert>
      )}

      {cqlIsStale && !isDirty && (
        <Alert severity="warning" sx={{ mb: 1 }} icon={<StaleIcon fontSize="small" />}>
          <Typography variant="caption">
            The artifact has been modified since this CQL was generated. Click &quot;Generate CQL&quot; to update.
          </Typography>
        </Alert>
      )}

      <Stack direction="row" spacing={1} mb={2}>
        <GradientButton onClick={handleGenerate} disabled={isLoading}>
          {saving ? 'Saving...' : 'Generate CQL'}
        </GradientButton>
        <GradientButton onClick={handleValidate} disabled={isLoading}>
          {saving ? 'Saving...' : 'Validate'}
        </GradientButton>
        {isLoading && <CircularProgress size={20} sx={{ alignSelf: 'center' }} />}
      </Stack>

      {generateMutation.isError && (
        <Alert severity="error" onClose={() => generateMutation.reset()} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">CQL Generation Failed</Typography>
          <Typography variant="body2" sx={{ mb: 0.5 }}>
            {(generateMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Common causes: empty element fields, missing value sets, or incompatible modifier chains. Check the Inclusions and Exclusions tabs for incomplete elements.
          </Typography>
        </Alert>
      )}

      {validation && (
        <Box sx={{ mb: 2 }}>
          {validation.success ? (
            <Alert severity="success">CQL is valid — no errors found. Ready for testing or deployment.</Alert>
          ) : (
            <Alert severity="error">
              <Typography variant="subtitle2">
                Validation found {errors.length} error{errors.length !== 1 ? 's' : ''}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Review the errors below. Line numbers indicate where the issue occurs in the generated CQL.
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
            Click &quot;Generate CQL&quot; to preview the generated CQL code from your artifact.
          </Typography>
        </Box>
      )}
    </Box>
  )
}
