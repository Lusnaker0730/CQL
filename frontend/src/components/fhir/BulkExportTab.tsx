import { useState, useRef, useEffect, useCallback } from 'react'
import { POLL_INTERVAL_MS } from '../../constants/timing'
import {
  Stack,
  Typography,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  CircularProgress,
  Alert,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  Link,
  Box,
} from '@mui/material'
import {
  CloudUpload as ExportIcon,
  Stop as StopIcon,
  Download as DownloadIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import type { BulkExportParams, BulkExportStatusResult } from '../../types'

interface BulkExportTabProps {
  fhirServer: string
}

export default function BulkExportTab({ fhirServer }: BulkExportTabProps) {
  const [exportType, setExportType] = useState('system')
  const [exportResourceTypes, setExportResourceTypes] = useState('')
  const [exportSince, setExportSince] = useState('')
  const [exportOutputFormat, setExportOutputFormat] = useState('application/fhir+ndjson')
  const [exportStatusUrl, setExportStatusUrl] = useState<string | null>(null)
  const [exportStatus, setExportStatus] = useState<BulkExportStatusResult | null>(null)
  const [isPolling, setIsPolling] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const pollingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stopPolling = useCallback(() => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current)
      pollingIntervalRef.current = null
    }
    setIsPolling(false)
  }, [])

  const pollStatus = useCallback(async (statusUrl: string) => {
    try {
      const result = await fhirApi.pollExportStatus(statusUrl)
      setExportStatus(result)
      if (result.status === 'completed' || result.status === 'error') {
        stopPolling()
        if (result.status === 'error' && result.errorMessage) {
          setExportError(result.errorMessage)
        }
      }
    } catch (err) {
      stopPolling()
      setExportError(err instanceof Error ? err.message : 'Polling failed')
    }
  }, [stopPolling])

  const startPolling = useCallback((statusUrl: string) => {
    setIsPolling(true)
    setExportError(null)
    pollStatus(statusUrl)
    pollingIntervalRef.current = setInterval(() => pollStatus(statusUrl), POLL_INTERVAL_MS)
  }, [pollStatus])

  useEffect(() => {
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current)
      }
    }
  }, [])

  const exportMutation = useMutation({
    mutationFn: (params: BulkExportParams) => fhirApi.kickOffExport(params),
    onSuccess: (data) => {
      setExportStatusUrl(data.statusUrl)
      setExportStatus(null)
      startPolling(data.statusUrl)
    },
    onError: (err) => {
      setExportError(err instanceof Error ? err.message : 'Export kick-off failed')
    },
  })

  const handleStartExport = () => {
    setExportError(null)
    setExportStatus(null)
    setExportStatusUrl(null)
    const params: BulkExportParams = {
      fhirServer,
      exportType,
      _outputFormat: exportOutputFormat,
    }
    if (exportResourceTypes.trim()) {
      params._type = exportResourceTypes.trim()
    }
    if (exportSince) {
      params._since = new Date(exportSince).toISOString()
    }
    exportMutation.mutate(params)
  }

  const handleResetExport = () => {
    stopPolling()
    setExportStatusUrl(null)
    setExportStatus(null)
    setExportError(null)
    exportMutation.reset()
  }

  const getTotalResourceCount = (): number => {
    if (!exportStatus?.output) return 0
    return exportStatus.output.reduce((sum, o) => sum + o.count, 0)
  }

  const getStatusLabel = (): string => {
    if (exportMutation.isPending) return 'Initiating...'
    if (!exportStatus) return 'Pending'
    if (exportStatus.status === 'completed') return 'Completed'
    if (exportStatus.status === 'error') return 'Error'
    return 'In Progress'
  }

  const getStatusColor = (): 'default' | 'primary' | 'success' | 'error' | 'warning' => {
    if (!exportStatus) return 'default'
    if (exportStatus.status === 'completed') return 'success'
    if (exportStatus.status === 'error') return 'error'
    return 'warning'
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center">
        <Typography variant="subtitle2">Bulk Data Export ($export)</Typography>
        <HelpTooltip text={helpContent.fhir.bulkExport} />
      </Stack>

      {!exportStatusUrl && (
        <>
          <FormControl fullWidth size="small">
            <InputLabel>Export Type</InputLabel>
            <Select
              value={exportType}
              onChange={(e) => setExportType(e.target.value)}
              label="Export Type"
            >
              <MenuItem value="system">System (All Resources)</MenuItem>
              <MenuItem value="patient">Patient Compartment</MenuItem>
              <MenuItem value="group">Group Compartment</MenuItem>
            </Select>
          </FormControl>

          <TextField
            label="Resource Types (optional)"
            value={exportResourceTypes}
            onChange={(e) => setExportResourceTypes(e.target.value)}
            size="small"
            fullWidth
            placeholder="e.g., Patient,Observation,Condition"
            helperText="Comma-separated list of resource types to include"
          />

          <TextField
            label="Since (optional)"
            value={exportSince}
            onChange={(e) => setExportSince(e.target.value)}
            size="small"
            fullWidth
            type="datetime-local"
            InputLabelProps={{ shrink: true }}
            helperText="Only include resources modified after this date"
          />

          <FormControl fullWidth size="small">
            <InputLabel>Output Format</InputLabel>
            <Select
              value={exportOutputFormat}
              onChange={(e) => setExportOutputFormat(e.target.value)}
              label="Output Format"
            >
              <MenuItem value="application/fhir+ndjson">NDJSON</MenuItem>
            </Select>
          </FormControl>

          <GradientButton
            onClick={handleStartExport}
            disabled={exportMutation.isPending}
            startIcon={exportMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <ExportIcon />}
            sx={{ '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
          >
            {exportMutation.isPending ? 'Starting Export...' : 'Start Export'}
          </GradientButton>
        </>
      )}

      {exportError && (
        <Alert severity="error">Export error: {exportError}</Alert>
      )}

      {exportStatusUrl && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" mb={1}>
            <Typography variant="subtitle2">Export Status</Typography>
            <Chip label={getStatusLabel()} size="small" color={getStatusColor()} sx={{ fontWeight: 600 }} />
            {exportStatus?.status === 'completed' && (
              <Chip
                label={`${getTotalResourceCount()} total resources`}
                size="small"
                sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }}
              />
            )}
          </Stack>

          {isPolling && <LinearProgress sx={{ mb: 2 }} />}

          <Stack direction="row" spacing={1} mb={2}>
            {isPolling && (
              <Button
                variant="outlined"
                color="warning"
                onClick={stopPolling}
                startIcon={<StopIcon />}
                size="small"
              >
                Stop Polling
              </Button>
            )}
            {(!isPolling || exportStatus?.status === 'completed' || exportStatus?.status === 'error') && (
              <Button variant="outlined" onClick={handleResetExport} startIcon={<ExportIcon />} size="small">
                New Export
              </Button>
            )}
          </Stack>

          {exportStatus?.output && exportStatus.output.length > 0 && (
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
                    <TableCell scope="col" sx={{ fontWeight: 600 }}>Resource Type</TableCell>
                    <TableCell scope="col" sx={{ fontWeight: 600 }} align="right">Count</TableCell>
                    <TableCell scope="col" sx={{ fontWeight: 600 }}>Download</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {exportStatus.output.map((output, idx) => (
                    <TableRow key={idx}>
                      <TableCell>{output.type}</TableCell>
                      <TableCell align="right">{output.count}</TableCell>
                      <TableCell>
                        <Link
                          href={output.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}
                        >
                          <DownloadIcon fontSize="small" />
                          NDJSON
                        </Link>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          {exportStatus?.status === 'completed' && exportStatus.output?.length === 0 && (
            <Alert severity="info">
              Export completed but no resources were found matching the criteria.
            </Alert>
          )}
        </Box>
      )}
    </Stack>
  )
}
