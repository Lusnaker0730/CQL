import { useEffect, useRef, useCallback, useState } from 'react'
import { Box, Stack, Button, CircularProgress, Alert, Chip } from '@mui/material'
import { Translate as TranslateIcon, Save as SaveIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useSelector, useDispatch } from 'react-redux'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { RootState } from '../../store'
import { setCqlContent, setErrors, setWarnings, setElmJson } from '../../store/editorSlice'
import CqlEditor from '../editor/CqlEditor'
import { measureApi, cqlApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import type { MeasureDefinition } from '../../types'

const AUTOSAVE_KEY = 'cql-measure-autosave'

interface MeasureCqlTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
  readOnly?: boolean
}

export default function MeasureCqlTab({ measure, onMeasureUpdate, readOnly }: MeasureCqlTabProps) {
  const dispatch = useDispatch()
  const { cqlContent, errors, warnings } = useSelector((state: RootState) => state.editor)
  const queryClient = useQueryClient()
  const autosaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [restoredDraft, setRestoredDraft] = useState(false)

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
      setRestoredDraft(true)
    } else if (serverCql) {
      dispatch(setCqlContent(serverCql))
    }
    // Clear errors from previous measure
    dispatch(setErrors([]))
    dispatch(setWarnings([]))
    return () => {
      if (autosaveTimer.current) clearTimeout(autosaveTimer.current)
    }
  }, [measure.id, dispatch])

  // Auto-save to localStorage on content change (debounced 5s)
  useEffect(() => {
    if (autosaveTimer.current) clearTimeout(autosaveTimer.current)
    autosaveTimer.current = setTimeout(() => {
      if (cqlContent && cqlContent !== (measure.cqlContent || '')) {
        localStorage.setItem(`${AUTOSAVE_KEY}-${measure.id}`, cqlContent)
      }
    }, 5000)
  }, [cqlContent, measure.id, measure.cqlContent])

  const saveMutation = useMutation({
    mutationFn: () =>
      measureApi.updateMeasure(measure.id!, { ...measure, cqlContent }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      queryClient.invalidateQueries({ queryKey: ['cql-expressions', measure.id] })
      onMeasureUpdate(updated)
      localStorage.removeItem(`${AUTOSAVE_KEY}-${measure.id}`)
      setRestoredDraft(false)
    },
  })

  const translateMutation = useMutation({
    mutationFn: () => cqlApi.translate({ cql: cqlContent }),
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

  const isDirty = cqlContent !== (measure.cqlContent || '')
  useUnsavedChangesGuard(isDirty)

  const dismissDraft = useCallback(() => {
    localStorage.removeItem(`${AUTOSAVE_KEY}-${measure.id}`)
    dispatch(setCqlContent(measure.cqlContent || ''))
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
          disabled={translateMutation.isPending || !cqlContent}
          onClick={() => translateMutation.mutate()}
        >
          Translate
        </Button>
        <GradientButton
          startIcon={<SaveIcon />}
          disabled={!isDirty || saveMutation.isPending || readOnly}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? 'Saving...' : 'Save CQL'}
        </GradientButton>
        {isDirty && (
          <Alert severity="info" sx={{ py: 0, px: 1, fontSize: '0.75rem' }}>
            Unsaved changes
          </Alert>
        )}
        {errorCount > 0 && (
          <Chip label={`${errorCount} error${errorCount > 1 ? 's' : ''}`} size="small" color="error" sx={{ height: 22 }} />
        )}
        {warningCount > 0 && (
          <Chip label={`${warningCount} warning${warningCount > 1 ? 's' : ''}`} size="small" color="warning" sx={{ height: 22 }} />
        )}
      </Stack>

      {restoredDraft && (
        <Alert
          severity="info"
          sx={{ mx: 1, mt: 1 }}
          action={
            <Button color="inherit" size="small" onClick={dismissDraft}>
              Discard Draft
            </Button>
          }
        >
          Restored unsaved draft from a previous session.
        </Alert>
      )}

      {translateMutation.isError && (
        <Alert severity="error" sx={{ mx: 1, mt: 1 }}>
          Translation failed: {(translateMutation.error as Error).message}
        </Alert>
      )}

      <Box sx={{ flex: 1, minHeight: 0 }}>
        <CqlEditor height="100%" readOnly={readOnly} />
      </Box>
    </Box>
  )
}
