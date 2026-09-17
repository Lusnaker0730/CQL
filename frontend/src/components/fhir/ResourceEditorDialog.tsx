import { useState, useRef, useCallback } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Box,
  Stack,
  useTheme,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the Proxy-based mock
// pattern that previously worked).
import SaveIcon from '@mui/icons-material/Save'
import ValidateIcon from '@mui/icons-material/CheckCircle'
import { useTranslation } from 'react-i18next'
import Editor, { type OnMount } from '../common/MonacoEditor'
import type * as Monaco from 'monaco-editor'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import GradientButton from '../common/GradientButton'

interface ResourceEditorDialogProps {
  open: boolean
  mode: 'create' | 'edit'
  resourceType: string
  resourceId?: string
  initialJson?: string
  connectionId: number | null
  onClose: () => void
  onSaved: () => void
}

interface ValidationIssue {
  severity: string
  location?: string
  diagnostics?: string
  message?: string
}

const SEVERITY_COLORS: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  error: 'error',
  fatal: 'error',
  warning: 'warning',
  information: 'info',
}

const DEFAULT_TEMPLATE = (resourceType: string) =>
  JSON.stringify({ resourceType, id: '' }, null, 2)

export default function ResourceEditorDialog({
  open,
  mode,
  resourceType,
  resourceId,
  initialJson,
  connectionId,
  onClose,
  onSaved,
}: ResourceEditorDialogProps) {
  const { t } = useTranslation('fhir')
  const { t: tc } = useTranslation('common')
  const theme = useTheme()
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null)
  const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([])
  const [validationStatus, setValidationStatus] = useState<'none' | 'valid' | 'invalid'>('none')
  const [error, setError] = useState<string | null>(null)

  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor
  }, [])

  const getEditorValue = () => editorRef.current?.getValue() || ''

  const validateMutation = useMutation({
    mutationFn: () => fhirApi.validateResource(getEditorValue()),
    onSuccess: (data) => {
      setValidationStatus(data.valid ? 'valid' : 'invalid')
      setValidationIssues(data.issues || [])
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Validation failed')
    },
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const json = getEditorValue()
      if (mode === 'create') {
        return fhirApi.createResource(resourceType, json, connectionId)
      }
      return fhirApi.updateResource(resourceType, resourceId!, json, connectionId)
    },
    onSuccess: () => {
      onSaved()
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : 'Save failed')
    },
  })

  const handleClose = () => {
    setValidationIssues([])
    setValidationStatus('none')
    setError(null)
    onClose()
  }

  const title = mode === 'create'
    ? t('editor.createTitle', { resourceType })
    : t('editor.editTitle', { resourceType, resourceId })

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          <Box sx={{ border: '1px solid rgba(0,0,0,0.12)', borderRadius: 1 }}>
            {/* PAT-134: keying by mode+resourceType+resourceId forces React
                to remount the Monaco editor when the resource being edited
                changes. Without this, `defaultValue` (which is initial-only)
                stays at the first-rendered JSON and never updates when the
                parent reopens the dialog with a different resource. */}
            <Editor
              key={`${mode}-${resourceType}-${resourceId || 'new'}`}
              height="400px"
              language="json"
              theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
              defaultValue={initialJson || DEFAULT_TEMPLATE(resourceType)}
              onMount={handleEditorMount}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                automaticLayout: true,
              }}
            />
          </Box>

          {validationStatus !== 'none' && (
            <Chip
              label={validationStatus === 'valid' ? t('editor.valid') : t('editor.invalid')}
              color={validationStatus === 'valid' ? 'success' : 'error'}
              size="small"
              sx={{ alignSelf: 'flex-start' }}
            />
          )}

          {validationIssues.length > 0 && (
            <TableContainer sx={{ maxHeight: 200 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>{t('editor.colSeverity')}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t('editor.colLocation')}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t('editor.colMessage')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {validationIssues.map((issue, idx) => (
                    <TableRow key={idx}>
                      <TableCell>
                        <Chip
                          label={issue.severity}
                          size="small"
                          color={SEVERITY_COLORS[issue.severity] || 'default'}
                          sx={issue.severity === 'fatal' ? { fontWeight: 700 } : undefined}
                        />
                      </TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                        {issue.location || '-'}
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>
                        {issue.diagnostics || issue.message || '-'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => validateMutation.mutate()}
          disabled={validateMutation.isPending}
          startIcon={validateMutation.isPending ? <CircularProgress size={16} /> : <ValidateIcon />}
          size="small"
        >
          {t('editor.validateButton')}
        </Button>
        <GradientButton
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          startIcon={saveMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
        >
          {saveMutation.isPending ? t('editor.saving') : t('editor.saveButton')}
        </GradientButton>
        <Button onClick={handleClose} size="small">
          {tc('actions.cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
