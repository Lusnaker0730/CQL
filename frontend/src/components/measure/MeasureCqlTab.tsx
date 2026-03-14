import { useEffect, useRef, useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { extractApiError } from '../../utils/errorUtils'
import { Box, Stack, Button, CircularProgress, Alert, Chip } from '@mui/material'
import { Translate as TranslateIcon, Save as SaveIcon, FileUpload as ImportIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useSelector, useDispatch } from 'react-redux'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { RootState } from '../../store'
import { setCqlContent, setErrors, setWarnings, setElmJson } from '../../store/editorSlice'
import CqlEditor from '../editor/CqlEditor'
import type { CqlEditorHandle } from '../editor/CqlEditor'
import { measureApi, cqlApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import ImportCqlFromLibraryDialog from './ImportCqlFromLibraryDialog'
import type { MeasureDefinition } from '../../types'
import { AUTOSAVE_DEBOUNCE_MS } from '../../constants/timing'

const AUTOSAVE_KEY = 'cql-measure-autosave'

interface MeasureCqlTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
  readOnly?: boolean
}

export default function MeasureCqlTab({ measure, onMeasureUpdate, readOnly }: MeasureCqlTabProps) {
  const { t } = useTranslation('measures')
  const dispatch = useDispatch()
  const { errors, warnings } = useSelector((state: RootState) => state.editor)
  const queryClient = useQueryClient()
  const autosaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [restoredDraft, setRestoredDraft] = useState(false)
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const cqlEditorRef = useRef<CqlEditorHandle>(null)
  // Local mirror of editor content — updated via onContentChanged callback
  const [localContent, setLocalContent] = useState('')

  // Track measure.cqlContent in a ref so the initialization effect can read it
  // without re-running when the user edits content in the editor
  const measureCqlRef = useRef(measure.cqlContent)
  measureCqlRef.current = measure.cqlContent

  useEffect(() => {
    // Check for autosaved draft on mount / measure change
    const saved = localStorage.getItem(`${AUTOSAVE_KEY}-${measure.id}`)
    const serverCql = measureCqlRef.current
    if (saved && serverCql && saved !== serverCql) {
      dispatch(setCqlContent(saved))
      setLocalContent(saved)
      setRestoredDraft(true)
    } else if (serverCql) {
      dispatch(setCqlContent(serverCql))
      setLocalContent(serverCql)
    }
    // Clear errors from previous measure
    dispatch(setErrors([]))
    dispatch(setWarnings([]))
    return () => {
      if (autosaveTimer.current) clearTimeout(autosaveTimer.current)
    }
  }, [measure.id, dispatch])

  // Called on every keystroke from the editor
  const handleContentChanged = useCallback((content: string) => {
    setLocalContent(content)
  }, [])

  // Auto-save to localStorage on content change (debounced)
  useEffect(() => {
    if (autosaveTimer.current) clearTimeout(autosaveTimer.current)
    autosaveTimer.current = setTimeout(() => {
      if (localContent && localContent !== (measure.cqlContent || '')) {
        localStorage.setItem(`${AUTOSAVE_KEY}-${measure.id}`, localContent)
      }
    }, AUTOSAVE_DEBOUNCE_MS)
  }, [localContent, measure.id, measure.cqlContent])

  const saveMutation = useMutation({
    mutationFn: () => {
      cqlEditorRef.current?.flushContent()
      const content = cqlEditorRef.current?.getContent() ?? localContent
      return measureApi.updateMeasure(measure.id!, { ...measure, cqlContent: content })
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      queryClient.invalidateQueries({ queryKey: ['cql-expressions', measure.id] })
      onMeasureUpdate(updated)
      localStorage.removeItem(`${AUTOSAVE_KEY}-${measure.id}`)
      setRestoredDraft(false)
    },
  })

  const translateMutation = useMutation({
    mutationFn: () => {
      cqlEditorRef.current?.flushContent()
      const content = cqlEditorRef.current?.getContent() ?? localContent
      return cqlApi.translate({ cql: content })
    },
    onSuccess: (data) => {
      if (data.success) {
        dispatch(setElmJson(data.elmJson || null))
        dispatch(setErrors([]))
        dispatch(setWarnings(data.warnings || []))
      } else {
        dispatch(setElmJson(null))
        dispatch(setErrors(data.errors || []))
        dispatch(setWarnings(data.warnings || []))
      }
    },
    onError: (error: Error) => {
      dispatch(setErrors([{ severity: 'Error', message: error.message }]))
    },
  })

  const handleImportCql = useCallback((cqlContent: string) => {
    dispatch(setCqlContent(cqlContent))
    setLocalContent(cqlContent)
  }, [dispatch])

  const isDirty = localContent !== (measure.cqlContent || '')
  useUnsavedChangesGuard(isDirty)

  const dismissDraft = useCallback(() => {
    localStorage.removeItem(`${AUTOSAVE_KEY}-${measure.id}`)
    dispatch(setCqlContent(measure.cqlContent || ''))
    setLocalContent(measure.cqlContent || '')
    setRestoredDraft(false)
  }, [measure.id, measure.cqlContent, dispatch])

  const errorCount = errors.length
  const warningCount = warnings.length

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ p: 1, borderBottom: '1px solid', borderColor: 'divider', flexWrap: 'wrap' }}>
        <Button
          size="small"
          variant="outlined"
          startIcon={translateMutation.isPending ? <CircularProgress size={16} /> : <TranslateIcon />}
          disabled={translateMutation.isPending || !localContent}
          onClick={() => translateMutation.mutate()}
        >
          {t('cql.translate')}
        </Button>
        <GradientButton
          startIcon={<SaveIcon />}
          disabled={!isDirty || saveMutation.isPending || readOnly}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? t('cql.saving') : t('cql.saveCql')}
        </GradientButton>
        <Button
          size="small"
          variant="outlined"
          startIcon={<ImportIcon />}
          disabled={readOnly}
          onClick={() => setImportDialogOpen(true)}
        >
          {t('cql.importFromLibrary')}
        </Button>
        {isDirty && (
          <Alert severity="info" sx={{ py: 0, px: 1, fontSize: '0.75rem' }}>
            {t('cql.unsavedChanges')}
          </Alert>
        )}
        {errorCount > 0 && (
          <Chip label={t('cql.errorCount', { count: errorCount })} size="small" color="error" sx={{ height: 22 }} />
        )}
        {warningCount > 0 && (
          <Chip label={t('cql.warningCount', { count: warningCount })} size="small" color="warning" sx={{ height: 22 }} />
        )}
      </Stack>

      {restoredDraft && (
        <Alert
          severity="info"
          sx={{ mx: 1, mt: 1 }}
          action={
            <Button color="inherit" size="small" onClick={dismissDraft}>
              {t('cql.discardDraft')}
            </Button>
          }
        >
          {t('cql.draftRestored')}
        </Alert>
      )}

      {translateMutation.isError && (
        <Alert severity="error" sx={{ mx: 1, mt: 1 }}>
          {t('cql.translationFailed', { error: extractApiError(translateMutation.error) })}
        </Alert>
      )}

      <Box sx={{ flex: 1, minHeight: 0 }}>
        <CqlEditor ref={cqlEditorRef} height="100%" readOnly={readOnly} onContentChanged={handleContentChanged} />
      </Box>

      <ImportCqlFromLibraryDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        onImport={handleImportCql}
        hasExistingContent={!!localContent.trim()}
      />
    </Box>
  )
}
