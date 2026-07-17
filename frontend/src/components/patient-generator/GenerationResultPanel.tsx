import { useEffect, useRef, useState, useCallback } from 'react'
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
  CircularProgress,
  IconButton,
  Tooltip,
  LinearProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Download as DownloadIcon,
  CloudUpload as UploadIcon,
  ContentCopy as CopyIcon,
  Delete as ClearIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import { fhirApi } from '../../api/fhirApi'
import { ehrApi } from '../../api/ehrApi'
import type { GeneratedPatientData } from '../../config/twcore'

// PAT-212: fake patients upload into a tenant-scoped EHR connection instead of an
// arbitrary server URL. "" selects the shared sandbox (connectionId = null).
const SANDBOX = ''

interface GenerationResultPanelProps {
  results: GeneratedPatientData[]
  onDownload: () => void
  onClear: () => void
}

interface UploadProgress {
  done: number
  total: number
  failures: { patientId: string; error: string }[]
}

export default function GenerationResultPanel({
  results,
  onDownload,
  onClear,
}: GenerationResultPanelProps) {
  const { t } = useTranslation('patientGenerator')
  const copyToClipboard = useCopyToClipboard()
  const { showNotification } = useNotification()

  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState<UploadProgress | null>(null)
  const [connectionValue, setConnectionValue] = useState<string>(SANDBOX)

  const { data: connections } = useQuery({
    queryKey: ['ehr', 'connections'],
    queryFn: () => ehrApi.getConnections(),
  })

  const connectionId = connectionValue === SANDBOX ? null : Number(connectionValue)

  // Lazy JSON cache: only the patient whose Accordion the user expanded
  // gets serialized. With 100 patients × ~10KB JSON each, eager serialization
  // (the previous Map populated on every results change) blocked the main
  // thread for hundreds of ms.
  const [jsonCache, setJsonCache] = useState<Record<string, string>>({})

  // Guards post-await setState calls when the user navigates away mid-upload.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  const handleCopy = useCallback(() => {
    const json = JSON.stringify(results, null, 2)
    copyToClipboard(json)
  }, [results, copyToClipboard])

  const ensureJson = useCallback(
    (patientData: GeneratedPatientData) => {
      const id = patientData.patient.id as string
      if (jsonCache[id]) return jsonCache[id]
      const json = JSON.stringify(patientData, null, 2)
      setJsonCache((prev) => ({ ...prev, [id]: json }))
      return json
    },
    [jsonCache],
  )

  const handleUpload = useCallback(async () => {
    setUploading(true)
    setUploadProgress({ done: 0, total: results.length, failures: [] })
    const failures: UploadProgress['failures'] = []

    for (let i = 0; i < results.length; i++) {
      // Bail out if the user navigated away — partial uploads remain server-side
      // but we don't push more requests at the dead component.
      if (!isMountedRef.current) return

      const patientData = results[i]
      const patientId = patientData.patient.id as string
      const allResources = [
        patientData.patient,
        ...patientData.encounters,
        ...patientData.conditions,
        ...patientData.observations,
        ...patientData.medications,
        ...patientData.medication_requests,
        ...patientData.allergies,
      ]
      const bundle = {
        resourceType: 'Bundle',
        type: 'transaction',
        entry: allResources.map((resource) => {
          const ref = `${resource.resourceType as string}/${resource.id as string}`
          return {
            fullUrl: ref,
            resource,
            request: { method: 'PUT', url: ref },
          }
        }),
      }

      try {
        // Per-patient try/catch lets one bad transaction fail without aborting
        // the rest. Failure list goes into the final notification.
        await fhirApi.executeTransaction(JSON.stringify(bundle), connectionId)
      } catch (err) {
        failures.push({ patientId, error: extractApiError(err) })
      }

      if (!isMountedRef.current) return
      setUploadProgress({ done: i + 1, total: results.length, failures: [...failures] })
    }

    if (!isMountedRef.current) return
    setUploading(false)

    const succeeded = results.length - failures.length
    if (failures.length === 0) {
      showNotification(
        t('result.uploadSuccess', { count: succeeded }),
        'success',
      )
    } else if (succeeded === 0) {
      showNotification(
        t('result.uploadAllFailed', { count: failures.length }),
        'error',
      )
    } else {
      showNotification(
        t('result.uploadPartial', { succeeded, failed: failures.length }),
        'warning',
      )
    }
  }, [results, connectionId, showNotification, t])

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
          <IconButton onClick={handleCopy} aria-label={t('result.copyJson')}>
            <CopyIcon />
          </IconButton>
        </Tooltip>
        <Box sx={{ flex: 1 }} />
        <Button variant="outlined" color="error" startIcon={<ClearIcon />} onClick={onClear}>
          {t('result.clear')}
        </Button>
      </Stack>
      <Box sx={{ mb: 2 }}>
        <FormControl fullWidth size="small">
          <InputLabel>{t('result.uploadServerLabel')}</InputLabel>
          <Select
            value={connectionValue}
            onChange={(e) => setConnectionValue(e.target.value)}
            label={t('result.uploadServerLabel')}
          >
            <MenuItem value={SANDBOX}>{t('result.uploadSandboxOption')}</MenuItem>
            {(connections ?? []).map((c) => (
              <MenuItem key={c.id} value={String(c.id)}>
                {c.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Box>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <Button
          variant="outlined"
          startIcon={uploading ? <CircularProgress size={18} /> : <UploadIcon />}
          onClick={handleUpload}
          disabled={uploading}
        >
          {uploading ? t('result.uploading') : t('result.uploadToServer')}
        </Button>
      </Stack>
      {uploadProgress && (uploading || uploadProgress.failures.length > 0) && (
        <Box sx={{ mb: 2 }}>
          <Stack
            direction="row"
            spacing={1}
            sx={{
              alignItems: "center",
              mb: 0.5
            }}>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>
              {t('result.uploadProgress', {
                done: uploadProgress.done,
                total: uploadProgress.total,
                failed: uploadProgress.failures.length,
              })}
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={uploadProgress.total > 0 ? (uploadProgress.done / uploadProgress.total) * 100 : 0}
          />
          {uploadProgress.failures.length > 0 && (
            <Alert severity="warning" sx={{ mt: 1, maxHeight: 160, overflow: 'auto' }}>
              <Typography variant="caption" gutterBottom sx={{
                fontWeight: 600
              }}>
                {t('result.uploadFailureList', { count: uploadProgress.failures.length })}
              </Typography>
              {uploadProgress.failures.map((f) => (
                <Typography key={f.patientId} variant="caption" component="div" sx={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>
                  {f.patientId}: {f.error}
                </Typography>
              ))}
            </Alert>
          )}
        </Box>
      )}
      {results.map((patientData, idx) => {
        const pat = patientData.patient
        const nameObj = (pat.name as Array<{ text?: string }>)?.[0]
        const patientName = nameObj?.text ?? `Patient ${idx + 1}`
        const patientId = pat.id as string

        return (
          <Accordion
            key={patientId}
            disableGutters
            // Only serialize the JSON when the Accordion expands — avoids the
            // stringify-N-patients hit on every results change.
            onChange={(_, expanded) => {
              if (expanded) ensureJson(patientData)
            }}
          >
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, mr: 1 }}>
                <Typography variant="body2" sx={{
                  fontWeight: 600
                }}>
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
                {jsonCache[patientId] ?? t('result.expandToView')}
              </Box>
            </AccordionDetails>
          </Accordion>
        );
      })}
    </Paper>
  );
}
