import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { measureApi } from '../../api'
import { downloadBlob } from '../../utils/download'
import { extractApiError } from '../../utils/errorUtils'

interface HumanReadablePreviewDialogProps {
  open: boolean
  onClose: () => void
  measureId: number
  measureName: string
}

/**
 * PAT-263 — the human-readable document of a measure, rendered in place instead of only being
 * downloadable. The HTML is the server's own (`GET /export/human-readable`), shown in a sandboxed
 * iframe so nothing in it can reach the application.
 */
export default function HumanReadablePreviewDialog({ open, onClose, measureId, measureName }: HumanReadablePreviewDialogProps) {
  const { t } = useTranslation('measures')
  const [html, setHtml] = useState<string | null>(null)
  const [blob, setBlob] = useState<Blob | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    setError(null)
    measureApi.exportHumanReadable(measureId)
      .then(async (b) => {
        const text = await b.text()
        if (cancelled) return
        setBlob(b)
        setHtml(text)
      })
      .catch((err) => { if (!cancelled) setError(extractApiError(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [open, measureId])

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>{t('humanReadable.title', { name: measureName })}</DialogTitle>
      <DialogContent sx={{ p: 0, minHeight: '60vh' }}>
        {loading && (
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', py: 8 }}>
            <CircularProgress size={24} />
          </Box>
        )}
        {error && <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>}
        {html !== null && !loading && !error && (
          <iframe
            title={t('humanReadable.title', { name: measureName })}
            sandbox=""
            srcDoc={html}
            style={{ width: '100%', height: '70vh', border: 0, display: 'block' }}
            data-testid="human-readable-frame"
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button
          startIcon={<DownloadIcon />}
          disabled={!blob}
          onClick={() => blob && downloadBlob(blob, `${measureName}-narrative.html`)}
        >
          {t('humanReadable.download')}
        </Button>
        <Button onClick={onClose}>{t('actions.close', { ns: 'common' })}</Button>
      </DialogActions>
    </Dialog>
  )
}
