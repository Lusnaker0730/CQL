import { useState, useRef, useCallback } from 'react'
import {
  Stack,
  Alert,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Typography,
  Box,
} from '@mui/material'
import { CheckCircle as ValidateIcon } from '@mui/icons-material'
import Editor, { type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import GradientButton from '../common/GradientButton'

const SEVERITY_COLORS: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  error: 'error',
  fatal: 'error',
  warning: 'warning',
  information: 'info',
}

const SAMPLE_JSON = JSON.stringify(
  { resourceType: 'Patient', name: [{ family: 'Test', given: ['Sample'] }] },
  null,
  2
)

export default function ValidateTab() {
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null)
  const [validationStatus, setValidationStatus] = useState<'none' | 'valid' | 'invalid'>('none')
  const [issues, setIssues] = useState<{ severity: string; location?: string; diagnostics?: string; message?: string }[]>([])

  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor
  }, [])

  const validateMutation = useMutation({
    mutationFn: () => {
      const value = editorRef.current?.getValue() || ''
      return fhirApi.validateResource(value)
    },
    onSuccess: (data) => {
      setValidationStatus(data.valid ? 'valid' : 'invalid')
      setIssues(data.issues || [])
    },
  })

  return (
    <Stack spacing={2}>
      <Typography variant="subtitle2">Validate a FHIR Resource</Typography>
      <Typography variant="body2" color="text.secondary">
        Paste a FHIR resource JSON below and click Validate to check for conformance issues.
      </Typography>

      <Box sx={{ border: '1px solid rgba(0,0,0,0.12)', borderRadius: 1 }}>
        <Editor
          height="300px"
          language="json"
          defaultValue={SAMPLE_JSON}
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

      <GradientButton
        onClick={() => validateMutation.mutate()}
        disabled={validateMutation.isPending}
        startIcon={validateMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <ValidateIcon />}
        sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
      >
        {validateMutation.isPending ? 'Validating...' : 'Validate'}
      </GradientButton>

      {validateMutation.isError && (
        <Alert severity="error">
          Validation failed: {(validateMutation.error as Error).message}
        </Alert>
      )}

      {validationStatus !== 'none' && (
        <Chip
          label={validationStatus === 'valid' ? 'Valid' : 'Invalid'}
          color={validationStatus === 'valid' ? 'success' : 'error'}
          icon={validationStatus === 'valid' ? <ValidateIcon /> : undefined}
          sx={{ alignSelf: 'flex-start' }}
        />
      )}

      {issues.length > 0 && (
        <TableContainer
          sx={{
            bgcolor: '#F8FAFB',
            borderRadius: '8px',
            border: '1px solid rgba(13,115,119,0.1)',
          }}
        >
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Severity</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Location</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Message</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {issues.map((issue, idx) => (
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
  )
}
