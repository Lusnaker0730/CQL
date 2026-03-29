import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Stack, Alert, CircularProgress, Typography } from '@mui/material'
import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { alpha } from '@mui/material/styles'
import { registerCqlLanguage } from '../../utils/cqlSyntax'
import { cqlApi } from '../../api/cqlApi'
import GradientButton from '../common/GradientButton'
import { usePreferences } from '../../hooks/usePreferences'
import type { CqlError } from '../../types'

interface CqlLibraryEditorTabProps {
  cqlContent: string
  onChange: (content: string) => void
  readOnly?: boolean
  errors?: CqlError[]
}

export default function CqlLibraryEditorTab({
  cqlContent,
  onChange,
  readOnly = false,
  errors: externalErrors,
}: CqlLibraryEditorTabProps) {
  const { t } = useTranslation('cqlLibraries')
  const { preferences } = usePreferences()
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null)
  const lastContentRef = useRef(cqlContent)

  const [validating, setValidating] = useState(false)
  const [validationErrors, setValidationErrors] = useState<CqlError[]>([])
  const [validationSuccess, setValidationSuccess] = useState<boolean | null>(null)

  // Ref to avoid validationSuccess in handleChange deps
  const validationSuccessRef = useRef(validationSuccess)
  validationSuccessRef.current = validationSuccess

  const displayErrors = externalErrors ?? validationErrors

  const handleBeforeMount: BeforeMount = useCallback((monaco) => {
    registerCqlLanguage(monaco)
  }, [])

  const handleMount: OnMount = useCallback((ed, monaco) => {
    editorRef.current = ed
    monacoRef.current = monaco
  }, [])

  const handleChange = useCallback((value: string | undefined) => {
    if (value !== undefined && value !== lastContentRef.current) {
      lastContentRef.current = value
      onChange(value)
      // Clear validation state on edit
      if (validationSuccessRef.current !== null) {
        setValidationSuccess(null)
        setValidationErrors([])
      }
    }
  }, [onChange])

  // Sync editor content when cqlContent changes externally
  useEffect(() => {
    if (!editorRef.current) return
    if (cqlContent !== lastContentRef.current) {
      lastContentRef.current = cqlContent
      const model = editorRef.current.getModel()
      if (model) {
        const currentValue = model.getValue()
        if (currentValue !== cqlContent) {
          const fullRange = model.getFullModelRange()
          editorRef.current.executeEdits('external', [{
            range: fullRange,
            text: cqlContent,
          }])
        }
      }
    }
  }, [cqlContent])

  // Set error markers in Monaco
  useEffect(() => {
    if (!editorRef.current || !monacoRef.current) return
    const monaco = monacoRef.current
    const model = editorRef.current.getModel()
    if (!model) return

    const markers: editor.IMarkerData[] = displayErrors.map((err) => ({
      severity: err.severity === 'warning'
        ? monaco.MarkerSeverity.Warning
        : monaco.MarkerSeverity.Error,
      message: err.message,
      startLineNumber: err.startLine || 1,
      startColumn: err.startColumn || 1,
      endLineNumber: err.endLine || err.startLine || 1,
      endColumn: err.endColumn || err.startColumn || 1,
    }))

    monaco.editor.setModelMarkers(model, 'cql-library', markers)
  }, [displayErrors])

  // Switch Monaco theme when dark mode preference changes
  useEffect(() => {
    if (!monacoRef.current) return
    monacoRef.current.editor.setTheme(
      preferences.themeMode === 'dark' ? 'cql-theme-dark' : 'cql-theme'
    )
  }, [preferences.themeMode])

  // Cleanup
  useEffect(() => {
    return () => {
      editorRef.current = null
      monacoRef.current = null
    }
  }, [])

  const handleValidate = useCallback(async () => {
    const content = editorRef.current?.getModel()?.getValue() ?? cqlContent
    if (!content.trim()) return

    setValidating(true)
    setValidationSuccess(null)
    setValidationErrors([])

    try {
      const result = await cqlApi.translate({ cql: content })
      if (result.success) {
        setValidationSuccess(true)
        setValidationErrors([])
      } else {
        setValidationSuccess(false)
        setValidationErrors(result.errors || [])
      }
    } catch {
      setValidationSuccess(false)
      setValidationErrors([{ severity: 'error', message: t('editor.validateFailed') }])
    } finally {
      setValidating(false)
    }
  }, [cqlContent, t])

  const editorOptions = useMemo(() => ({
    readOnly,
    minimap: { enabled: preferences.editorMinimap },
    fontSize: preferences.editorFontSize,
    lineNumbers: 'on' as const,
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: preferences.editorTabSize,
    wordWrap: preferences.editorWordWrap,
    folding: true,
    bracketPairColorization: { enabled: true },
    formatOnPaste: false,
    formatOnType: true,
    contextmenu: true,
  }), [readOnly, preferences.editorMinimap, preferences.editorFontSize, preferences.editorTabSize, preferences.editorWordWrap])

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Stack direction="row" spacing={1} sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <GradientButton onClick={handleValidate} disabled={validating || readOnly}>
          {validating ? t('editor.validating') : t('editor.validate')}
        </GradientButton>
        {validating && <CircularProgress size={20} sx={{ alignSelf: 'center' }} />}
      </Stack>

      {validationSuccess === true && (
        <Alert severity="success" sx={{ mx: 1.5, mt: 1 }}>
          {t('editor.validationSuccess')}
        </Alert>
      )}

      {validationSuccess === false && validationErrors.length > 0 && (
        <Alert severity="error" sx={{ mx: 1.5, mt: 1 }}>
          <Typography variant="subtitle2">
            {t('editor.validationErrorCount', { count: validationErrors.length })}
          </Typography>
          <Box sx={{ mt: 0.5, maxHeight: 150, overflow: 'auto' }}>
            {validationErrors.map((err, i) => (
              <Typography key={i} variant="caption" display="block" sx={{ fontFamily: 'monospace', mb: 0.25 }}>
                {err.startLine ? `[${err.startLine}:${err.startColumn || 0}] ` : ''}{err.message}
              </Typography>
            ))}
          </Box>
        </Alert>
      )}

      <Box
        sx={(theme) => ({
          flex: 1,
          minHeight: 0,
          border: '1px solid',
          borderColor: alpha(theme.palette.primary.main, 0.15),
          borderRadius: '10px',
          overflow: 'hidden',
          m: 1.5,
          transition: 'all 0.2s ease',
          boxShadow: `inset 0 1px 3px ${alpha(theme.palette.primary.main, 0.06)}`,
          '&:focus-within': {
            borderColor: 'primary.main',
            boxShadow: `0 0 0 3px ${alpha(theme.palette.primary.main, 0.12)}, inset 0 1px 3px ${alpha(theme.palette.primary.main, 0.06)}`,
          },
        })}
      >
        <Editor
          height="calc(100vh - 280px)"
          defaultLanguage="cql"
          defaultValue={cqlContent}
          theme={preferences.themeMode === 'dark' ? 'cql-theme-dark' : 'cql-theme'}
          beforeMount={handleBeforeMount}
          onMount={handleMount}
          onChange={handleChange}
          loading={<CircularProgress />}
          options={editorOptions}
        />
      </Box>
    </Box>
  )
}
