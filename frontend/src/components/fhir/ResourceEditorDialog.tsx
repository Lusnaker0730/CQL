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
} from '@mui/material'
import {
  Save as SaveIcon,
  CheckCircle as ValidateIcon,
} from '@mui/icons-material'
import Editor, { type OnMount } from '@monaco-editor/react'
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
  fhirServer: string
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
  fhirServer,
  onClose,
  onSaved,
}: ResourceEditorDialogProps) {
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
        return fhirApi.createResource(resourceType, json, fhirServer)
      }
      return fhirApi.updateResource(resourceType, resourceId!, json, fhirServer)
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
    ? `Create ${resourceType}`
    : `Edit ${resourceType}/${resourceId}`

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
            <Editor
              height="400px"
              language="json"
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
              label={validationStatus === 'valid' ? 'Valid' : 'Invalid'}
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
                    <TableCell sx={{ fontWeight: 600 }}>Severity</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Location</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Message</TableCell>
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
          Validate
        </Button>
        <GradientButton
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          startIcon={saveMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
        >
          {saveMutation.isPending ? 'Saving...' : 'Save'}
        </GradientButton>
        <Button onClick={handleClose} size="small">
          Cancel
        </Button>
      </DialogActions>
    </Dialog>
  )
}
