import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, CircularProgress, Dialog, DialogTitle, DialogContent,
  DialogActions, Tab, Tabs, TextField, Typography,
} from '@mui/material'
import { Upload as UploadIcon } from '@mui/icons-material'
import type { CqlLibrary } from '../../types'
import { useImportCqlLibrary, useCreateCqlLibrary } from '../../hooks/useCqlLibraries'
import { extractApiError } from '../../utils/errorUtils'

interface ImportLibraryDialogProps {
  open: boolean
  onClose: () => void
  onImported?: (library: CqlLibrary) => void
}

export default function ImportLibraryDialog({
  open, onClose, onImported,
}: ImportLibraryDialogProps) {
  const { t } = useTranslation('cqlLibraries')
  const [tab, setTab] = useState(0)
  const [jsonText, setJsonText] = useState('')
  const [cqlText, setCqlText] = useState('')
  const [cqlDescription, setCqlDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const importMutation = useImportCqlLibrary()
  const createMutation = useCreateCqlLibrary()

  const isPending = importMutation.isPending || createMutation.isPending

  const handleClose = useCallback(() => {
    if (isPending) return
    setJsonText('')
    setCqlText('')
    setCqlDescription('')
    setError(null)
    setTab(0)
    onClose()
  }, [isPending, onClose])

  const handleFileUpload = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (ev) => {
      const content = ev.target?.result as string
      setJsonText(content)
      setError(null)
    }
    reader.readAsText(file)
    // Reset input so the same file can be re-selected
    e.target.value = ''
  }, [])

  const handleImportFhir = useCallback(() => {
    setError(null)
    let parsed: unknown
    try {
      parsed = JSON.parse(jsonText)
    } catch {
      setError('Invalid JSON format.')
      return
    }
    importMutation.mutate(parsed, {
      onSuccess: (lib) => {
        handleClose()
        onImported?.(lib)
      },
      onError: (err) => {
        setError(extractApiError(err) || t('errors.importFailed', { error: 'Unknown error' }))
      },
    })
  }, [jsonText, importMutation, handleClose, onImported, t])

  const handleImportCql = useCallback(() => {
    setError(null)
    if (!cqlText.trim()) return
    createMutation.mutate(
      { cql: cqlText, description: cqlDescription || undefined },
      {
        onSuccess: (lib) => {
          handleClose()
          onImported?.(lib)
        },
        onError: (err) => {
          setError(extractApiError(err) || t('errors.importFailed', { error: 'Unknown error' }))
        },
      },
    )
  }, [cqlText, cqlDescription, createMutation, handleClose, onImported, t])

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('import.title')}</DialogTitle>
      <DialogContent dividers>
        <Tabs
          value={tab}
          onChange={(_, v) => { setTab(v); setError(null) }}
          sx={{ mb: 2 }}
        >
          <Tab label={t('import.fhirTab')} />
          <Tab label={t('import.cqlTab')} />
        </Tabs>

        {tab === 0 && (
          <Box>
            <Button
              variant="outlined"
              component="label"
              startIcon={<UploadIcon />}
              sx={{ mb: 2 }}
            >
              {t('import.uploadFile')}
              <input type="file" accept=".json" hidden onChange={handleFileUpload} />
            </Button>
            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              {t('import.uploadHint')}
            </Typography>
            <TextField
              fullWidth
              multiline
              minRows={8}
              maxRows={16}
              placeholder={t('import.pasteJson')}
              value={jsonText}
              onChange={(e) => { setJsonText(e.target.value); setError(null) }}
              InputProps={{ sx: { fontFamily: 'monospace', fontSize: '0.85rem' } }}
            />
          </Box>
        )}

        {tab === 1 && (
          <Box>
            <TextField
              fullWidth
              multiline
              minRows={8}
              maxRows={16}
              placeholder={t('import.pasteCql')}
              value={cqlText}
              onChange={(e) => { setCqlText(e.target.value); setError(null) }}
              InputProps={{ sx: { fontFamily: 'monospace', fontSize: '0.85rem' } }}
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              size="small"
              label={t('create.description')}
              value={cqlDescription}
              onChange={(e) => setCqlDescription(e.target.value)}
            />
          </Box>
        )}

        {error && (
          <Typography variant="body2" color="error" sx={{ mt: 1.5 }}>
            {error}
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={isPending}>
          {t('common:actions.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={tab === 0 ? handleImportFhir : handleImportCql}
          disabled={isPending || (tab === 0 ? !jsonText.trim() : !cqlText.trim())}
          startIcon={isPending ? <CircularProgress size={16} /> : undefined}
        >
          {isPending ? t('import.importing') : t('import.import')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
