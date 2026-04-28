import { useState, useRef, useCallback } from 'react'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../../constants/timing'
import {
  Stack,
  Alert,
  CircularProgress,
  Typography,
  Box,
  Button,
  Snackbar,
  useTheme,
} from '@mui/material'
import {
  PlayArrow as ExecuteIcon,
  ContentCopy as CopyIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import Editor, { type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { extractApiError } from '../../utils/errorUtils'
import GradientButton from '../common/GradientButton'
import { formatJson } from '../../utils/fhirBrowserUtils'

interface TransactionTabProps {
  fhirServer: string
}

const TRANSACTION_TEMPLATE = JSON.stringify(
  {
    resourceType: 'Bundle',
    type: 'transaction',
    entry: [
      {
        resource: {
          resourceType: 'Patient',
          name: [{ family: 'Example', given: ['Transaction'] }],
        },
        request: {
          method: 'POST',
          url: 'Patient',
        },
      },
    ],
  },
  null,
  2
)

export default function TransactionTab({ fhirServer }: TransactionTabProps) {
  const { t } = useTranslation('fhir')
  const theme = useTheme()
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null)
  const [result, setResult] = useState<object | null>(null)
  const [copied, setCopied] = useState(false)

  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor
  }, [])

  const executeMutation = useMutation({
    mutationFn: () => {
      const value = editorRef.current?.getValue() || ''
      return fhirApi.executeTransaction(value, fhirServer)
    },
    onSuccess: (data) => {
      setResult(data as object)
    },
  })

  const handleCopyResult = () => {
    if (result) {
      navigator.clipboard.writeText(formatJson(result))
      setCopied(true)
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="subtitle2">{t('transaction.title')}</Typography>
      <Typography variant="body2" color="text.secondary">
        {t('transaction.description')}
      </Typography>

      <Box sx={{ border: '1px solid rgba(0,0,0,0.12)', borderRadius: 1 }}>
        <Editor
          height="350px"
          language="json"
          theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
          defaultValue={TRANSACTION_TEMPLATE}
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
        onClick={() => executeMutation.mutate()}
        disabled={executeMutation.isPending}
        startIcon={executeMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <ExecuteIcon />}
        sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
      >
        {executeMutation.isPending ? t('transaction.executing') : t('transaction.executeButton')}
      </GradientButton>

      {executeMutation.isError && (
        <Alert severity="error">
          {t('transaction.transactionFailed', { error: extractApiError(executeMutation.error) })}
        </Alert>
      )}

      {result && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" mb={1}>
            <Typography variant="subtitle2">{t('transaction.response')}</Typography>
            <Button size="small" startIcon={<CopyIcon />} onClick={handleCopyResult}>
              {t('transaction.copy')}
            </Button>
          </Stack>
          <Box
            component="pre"
            sx={{
              p: 2,
              bgcolor: 'grey.900',
              color: 'grey.300',
              borderRadius: '8px',
              fontSize: '0.75rem',
              overflow: 'auto',
              maxHeight: 400,
              fontFamily: '"Consolas", "Monaco", monospace',
              m: 0,
            }}
          >
            {formatJson(result)}
          </Box>
        </Box>
      )}

      <Snackbar open={copied} autoHideDuration={COPY_FEEDBACK_TIMEOUT_MS} onClose={() => setCopied(false)}>
        <Alert severity="success" variant="filled">{t('transaction.jsonCopied')}</Alert>
      </Snackbar>
    </Stack>
  )
}
