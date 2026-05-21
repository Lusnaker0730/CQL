import { useState, useEffect } from 'react'
import DOMPurify from 'dompurify'
import { useTranslation } from 'react-i18next'
import { Box, Stack, Alert, CircularProgress, Typography } from '@mui/material'
import { WarningAmber as StaleIcon } from '@mui/icons-material'
import { useMonaco } from '../../common/MonacoEditor'
import GradientButton from '../../common/GradientButton'
import { useGenerateArtifactCql, useValidateArtifactCql, useFormatCql } from '../../../hooks/useArtifactCql'
import { usePreferences } from '../../../hooks/usePreferences'
import { registerCqlLanguage } from '../../../utils/cqlSyntax'
import type { CqlTranslationResponse } from '../../../types'
import { codeBlockSx } from '../../../constants/authoringConstants'
import { extractApiError, extractApiErrorDetails } from '../../../utils/errorUtils'

interface CqlPreviewPanelProps {
  artifactId: number
  onSaveBeforeGenerate?: () => Promise<void>
  isDirty?: boolean
}

// Module-scope guards: Monaco's setTheme is global so the previous unguarded
// call repainted every editor on the page on every keystroke; cqlRegistered
// belongs at module scope (not per-mount) since the language registration
// is global to monaco.
let cqlLanguageRegistered = false
let lastAppliedTheme: string | null = null

export default function CqlPreviewPanel({ artifactId, onSaveBeforeGenerate, isDirty }: CqlPreviewPanelProps) {
  const { t } = useTranslation('authoring')
  const [cql, setCql] = useState<string | null>(null)
  const [validation, setValidation] = useState<CqlTranslationResponse | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveErrorDetails, setSaveErrorDetails] = useState<string[]>([])
  const [cqlIsStale, setCqlIsStale] = useState(false)
  const [colorizedHtml, setColorizedHtml] = useState<string>('')

  const generateMutation = useGenerateArtifactCql()
  const validateMutation = useValidateArtifactCql()
  const formatMutation = useFormatCql()
  const monaco = useMonaco()
  const { preferences } = usePreferences()
  const isDark = preferences.themeMode === 'dark'

  // Register CQL language and colorize when cql or theme changes
  useEffect(() => {
    if (!cql || !monaco) {
      setColorizedHtml('')
      return
    }
    let cancelled = false
    if (!cqlLanguageRegistered) {
      registerCqlLanguage(monaco)
      cqlLanguageRegistered = true
    }
    const themeName = isDark ? 'cql-theme-dark' : 'cql-theme'
    if (lastAppliedTheme !== themeName) {
      monaco.editor.setTheme(themeName)
      lastAppliedTheme = themeName
    }

    monaco.editor.colorize(cql, 'cql', { tabSize: 2 }).then((html) => {
      if (!cancelled) setColorizedHtml(html)
    }).catch(() => {
      if (!cancelled) setColorizedHtml('')
    })
    return () => { cancelled = true }
  }, [cql, monaco, isDark])

  // Mark CQL as stale when artifact has been modified after generation
  useEffect(() => {
    if (isDirty && cql) {
      setCqlIsStale(true)
    }
  }, [isDirty, cql])

  const saveFirst = async () => {
    if (isDirty && onSaveBeforeGenerate) {
      setSaving(true)
      setSaveError(null)
      try {
        await onSaveBeforeGenerate()
      } catch (err) {
        setSaveError(extractApiError(err))
        setSaveErrorDetails(extractApiErrorDetails(err) || [])
        throw err
      } finally {
        setSaving(false)
      }
    }
  }

  const handleGenerate = async () => {
    if (generateMutation.isPending) return
    try { await saveFirst() } catch { return }
    try {
      const data = await generateMutation.mutateAsync(artifactId)
      setCql(data.cql)
      setValidation(null)
      setCqlIsStale(false)
    } catch {
      // mutation surface error via mutation.error UI
    }
  }

  const handleValidate = async () => {
    // Bail if either step is in flight — prevents duplicate validate→generate
    // chains where last writer wins on setValidation/setCql, and avoids
    // setting state on an unmounted component if the user navigates away.
    if (validateMutation.isPending || generateMutation.isPending) return
    try { await saveFirst() } catch { return }
    try {
      const validateData = await validateMutation.mutateAsync(artifactId)
      setValidation(validateData)
      if (!cql) {
        const genData = await generateMutation.mutateAsync(artifactId)
        setCql(genData.cql)
      }
    } catch {
      // mutation error surfaces via the validateMutation.error UI
    }
  }

  const handleFormat = () => {
    if (!cql || formatMutation.isPending) return
    formatMutation.mutate(cql, {
      onSuccess: (data) => setCql(data.cql),
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
        <GradientButton onClick={handleFormat} disabled={!cql || isLoading || formatMutation.isPending}>
          {t('cqlPreview.format')}
        </GradientButton>
        {(isLoading || formatMutation.isPending) && <CircularProgress size={20} sx={{ alignSelf: 'center' }} />}
      </Stack>

      {saveError && (
        <Alert severity="error" onClose={() => { setSaveError(null); setSaveErrorDetails([]) }} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('header.saveFailed')}</Typography>
          <Typography variant="body2">{saveError}</Typography>
          {saveErrorDetails.map((detail, i) => (
            <Typography key={i} variant="caption" display="block" sx={{ mt: 0.5, fontFamily: 'monospace' }}>
              • {detail}
            </Typography>
          ))}
        </Alert>
      )}

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

      {formatMutation.isError && (
        <Alert severity="error" onClose={() => formatMutation.reset()} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('cqlPreview.formatFailed')}</Typography>
          <Typography variant="body2">
            {(formatMutation.error as Error)?.message || 'Unknown error'}
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
          sx={(theme) => ({
            ...codeBlockSx,
            flex: 1,
            minHeight: 0,
            overflow: 'auto',
            p: 2,
            fontFamily: 'monospace',
            fontSize: '0.85rem',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
            '& .mtk1': { color: isDark ? theme.palette.text.primary : theme.palette.secondary.main },
          })}
        >
          {colorizedHtml ? (
            <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(colorizedHtml) }} />
          ) : (
            cql
          )}
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
