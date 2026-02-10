import { useState } from 'react'
import { Box, Stack, Alert, CircularProgress, Typography } from '@mui/material'
import GradientButton from '../../common/GradientButton'
import { useGenerateArtifactCql, useValidateArtifactCql } from '../../../hooks/useArtifactCql'
import type { CqlTranslationResponse } from '../../../types'

interface CqlPreviewPanelProps {
  artifactId: number
  onSaveBeforeGenerate?: () => Promise<void>
  isDirty?: boolean
}

export default function CqlPreviewPanel({ artifactId, onSaveBeforeGenerate, isDirty }: CqlPreviewPanelProps) {
  const [cql, setCql] = useState<string | null>(null)
  const [validation, setValidation] = useState<CqlTranslationResponse | null>(null)
  const [saving, setSaving] = useState(false)

  const generateMutation = useGenerateArtifactCql()
  const validateMutation = useValidateArtifactCql()

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
    generateMutation.mutate(artifactId, {
      onSuccess: (data) => {
        setCql(data.cql)
        setValidation(null)
      },
    })
  }

  const handleValidate = async () => {
    await saveFirst()
    validateMutation.mutate(artifactId, {
      onSuccess: (data) => {
        setValidation(data)
        if (!cql) {
          generateMutation.mutate(artifactId, {
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
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to generate CQL: {(generateMutation.error as Error)?.message || 'Unknown error'}
        </Alert>
      )}

      {validation && (
        <Box sx={{ mb: 2 }}>
          {validation.success ? (
            <Alert severity="success">CQL is valid - no errors found.</Alert>
          ) : (
            <Alert severity="error">
              CQL validation failed with {errors.length} error{errors.length !== 1 ? 's' : ''}.
            </Alert>
          )}
          {hasErrors && (
            <Box sx={{ mt: 1, maxHeight: 200, overflow: 'auto' }}>
              {errors.map((err, i) => (
                <Typography key={i} variant="caption" color="error" display="block" sx={{ fontFamily: 'monospace' }}>
                  {typeof err === 'object' && err !== null && 'message' in err
                    ? (err as { message: string }).message
                    : String(err)}
                </Typography>
              ))}
            </Box>
          )}
        </Box>
      )}

      {cql ? (
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            overflow: 'auto',
            p: 2,
            borderRadius: 1,
            backgroundColor: (theme) => theme.palette.mode === 'dark' ? '#1e1e1e' : '#f5f5f5',
            fontFamily: '"Fira Code", "Consolas", monospace',
            fontSize: '0.85rem',
            whiteSpace: 'pre-wrap',
            lineHeight: 1.6,
            border: 1,
            borderColor: 'divider',
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
