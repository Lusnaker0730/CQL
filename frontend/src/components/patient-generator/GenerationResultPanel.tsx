import { useState, useMemo, useCallback } from 'react'
import {
  Box,
  Typography,
  Button,
  Stack,
  Paper,
  Alert,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  TextField,
  CircularProgress,
  IconButton,
  Tooltip,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Download as DownloadIcon,
  CloudUpload as UploadIcon,
  ContentCopy as CopyIcon,
  Delete as ClearIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import type { GeneratedPatientData } from '../../config/twcore'

interface GenerationResultPanelProps {
  results: GeneratedPatientData[]
  onDownload: () => void
  onClear: () => void
}

export default function GenerationResultPanel({
  results,
  onDownload,
  onClear,
}: GenerationResultPanelProps) {
  const { t } = useTranslation('patientGenerator')
  const copyToClipboard = useCopyToClipboard()
  const [serverUrl, setServerUrl] = useState('http://localhost:8090/fhir')
  const [uploading, setUploading] = useState(false)
  const [uploadMessage, setUploadMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const handleCopy = useCallback(() => {
    const json = JSON.stringify(results, null, 2)
    copyToClipboard(json)
  }, [results, copyToClipboard])

  const patientJsonCache = useMemo(() => {
    const cache = new Map<string, string>()
    for (const p of results) {
      cache.set(p.patient.id as string, JSON.stringify(p, null, 2))
    }
    return cache
  }, [results])

  const handleUpload = useCallback(async () => {
    if (!serverUrl.trim()) return
    setUploading(true)
    setUploadMessage(null)
    let uploadedCount = 0

    try {
      for (const patientData of results) {
        const allResources = [
          patientData.patient,
          ...patientData.encounters,
          ...patientData.conditions,
          ...patientData.observations,
          ...patientData.medications,
          ...patientData.medication_requests,
          ...patientData.allergies,
        ]
        for (const resource of allResources) {
          const response = await fetch(`${serverUrl}/${resource.resourceType}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/fhir+json' },
            body: JSON.stringify(resource),
          })
          if (response.ok) uploadedCount++
        }
      }
      setUploadMessage({ type: 'success', text: t('result.uploadSuccess', { count: uploadedCount }) })
    } catch (err) {
      setUploadMessage({
        type: 'error',
        text: t('result.uploadError', { error: err instanceof Error ? err.message : String(err) }),
      })
    } finally {
      setUploading(false)
    }
  }, [serverUrl, results, t])

  if (results.length === 0) return null

  return (
    <Paper sx={{ p: 3 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6">
          {t('result.title')}
        </Typography>
        <Chip
          label={t('result.patientCount', { count: results.length })}
          color="success"
        />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <Button variant="contained" startIcon={<DownloadIcon />} onClick={onDownload}>
          {t('result.download')}
        </Button>
        <Tooltip title={t('result.copyJson')}>
          <IconButton onClick={handleCopy}>
            <CopyIcon />
          </IconButton>
        </Tooltip>
        <Box sx={{ flex: 1 }} />
        <Button variant="outlined" color="error" startIcon={<ClearIcon />} onClick={onClear}>
          {t('result.clear')}
        </Button>
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <TextField
          size="small"
          label={t('result.serverUrl')}
          value={serverUrl}
          onChange={(e) => setServerUrl(e.target.value)}
          sx={{ flex: 1 }}
        />
        <Button
          variant="outlined"
          startIcon={uploading ? <CircularProgress size={18} /> : <UploadIcon />}
          onClick={handleUpload}
          disabled={uploading || !serverUrl.trim()}
        >
          {uploading ? t('result.uploading') : t('result.uploadToServer')}
        </Button>
      </Stack>

      {uploadMessage && (
        <Alert severity={uploadMessage.type} sx={{ mb: 2 }} onClose={() => setUploadMessage(null)}>
          {uploadMessage.text}
        </Alert>
      )}

      {results.map((patientData, idx) => {
        const pat = patientData.patient
        const nameObj = (pat.name as Array<{ text?: string }>)?.[0]
        const patientName = nameObj?.text ?? `Patient ${idx + 1}`
        const patientId = pat.id as string

        return (
          <Accordion key={patientId} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, mr: 1 }}>
                <Typography variant="body2" fontWeight={600}>
                  {patientName}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  ({patientId})
                </Typography>
                <Box sx={{ flex: 1 }} />
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('result.resourceSummary', {
                    conditions: patientData.conditions.length,
                    observations: patientData.observations.length,
                    medications: patientData.medications.length,
                    allergies: patientData.allergies.length,
                    encounters: patientData.encounters.length,
                  })}
                </Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Box
                component="pre"
                sx={{
                  bgcolor: 'action.hover',
                  p: 2,
                  borderRadius: 1,
                  overflow: 'auto',
                  maxHeight: 400,
                  fontSize: '0.75rem',
                  fontFamily: 'monospace',
                  m: 0,
                }}
              >
                {patientJsonCache.get(patientId)}
              </Box>
            </AccordionDetails>
          </Accordion>
        )
      })}
    </Paper>
  )
}
