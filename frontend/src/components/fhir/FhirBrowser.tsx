import { useState, useRef, useEffect, useCallback } from 'react'
import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  CircularProgress,
  Alert,
  Tabs,
  Tab,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  Link,
} from '@mui/material'
import {
  Search as SearchIcon,
  CloudUpload as ExportIcon,
  Stop as StopIcon,
  Download as DownloadIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import type { BulkExportParams, BulkExportStatusResult } from '../../types'
import FhirServerUrlField from '../common/FhirServerUrlField'

const FHIR_RESOURCE_TYPES = [
  'Patient',
  'Encounter',
  'Condition',
  'Observation',
  'Procedure',
  'MedicationRequest',
  'MedicationStatement',
  'DiagnosticReport',
  'ServiceRequest',
  'Immunization',
  'AllergyIntolerance',
  'CarePlan',
  'Goal',
  'Claim',
  'Coverage',
  'Organization',
  'Practitioner',
  'Location',
]

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div role="tabpanel" hidden={value !== index}>
      {value === index && <Box sx={{ py: 2 }}>{children}</Box>}
    </div>
  )
}

export default function FhirBrowser() {
  const [tabValue, setTabValue] = useState(0)
  const [fhirServer, setFhirServer] = useState('http://hapi.fhir.org/baseR4')
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [resourceType, setResourceType] = useState('Patient')
  const [searchParams, setSearchParams] = useState('')
  const [resourceId, setResourceId] = useState('')
  const [searchResult, setSearchResult] = useState<object | null>(null)
  const [readResult, setReadResult] = useState<object | null>(null)

  // Bulk Export state
  const [exportType, setExportType] = useState('system')
  const [exportResourceTypes, setExportResourceTypes] = useState('')
  const [exportSince, setExportSince] = useState('')
  const [exportOutputFormat, setExportOutputFormat] = useState('application/fhir+ndjson')
  const [exportStatusUrl, setExportStatusUrl] = useState<string | null>(null)
  const [exportStatus, setExportStatus] = useState<BulkExportStatusResult | null>(null)
  const [isPolling, setIsPolling] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const pollingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const searchMutation = useMutation({
    mutationFn: () => fhirApi.search(resourceType, searchParams, fhirServer),
    onSuccess: (data) => setSearchResult(data as object),
  })

  const readMutation = useMutation({
    mutationFn: () => fhirApi.read(resourceType, resourceId, fhirServer),
    onSuccess: (data) => setReadResult(data as object),
  })

  const handleSearch = () => {
    searchMutation.mutate()
  }

  const handleRead = () => {
    if (resourceId) {
      readMutation.mutate()
    }
  }

  const formatJson = (data: unknown): string => {
    try {
      if (typeof data === 'string') {
        return JSON.stringify(JSON.parse(data), null, 2)
      }
      return JSON.stringify(data, null, 2)
    } catch {
      return String(data)
    }
  }

  const getResourceCount = (data: unknown): number => {
    if (!data) return 0
    try {
      const parsed = typeof data === 'string' ? JSON.parse(data) : data
      if (parsed.total !== undefined) return parsed.total
      if (parsed.entry) return parsed.entry.length
      return 0
    } catch {
      return 0
    }
  }

  // Bulk Export logic
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
    pollingIntervalRef.current = setInterval(() => pollStatus(statusUrl), 5000)
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

  const handleStopPolling = () => {
    stopPolling()
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
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        FHIR Browser
      </Typography>

      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="flex-start">
          <FhirServerUrlField
            value={fhirServer}
            onChange={(value) => {
              setFhirServer(value)
              setFhirServerError(null)
            }}
            error={!!fhirServerError}
            helperText={fhirServerError}
          />
          <HelpTooltip text={helpContent.fhir.serverUrl} />
        </Stack>

        <FormControl fullWidth size="small">
          <InputLabel>Resource Type</InputLabel>
          <Select
            value={resourceType}
            onChange={(e) => setResourceType(e.target.value)}
            label="Resource Type"
          >
            {FHIR_RESOURCE_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {type}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
          <Tab label="Search" />
          <Tab label="Read" />
          <Tab label="Bulk Export" />
        </Tabs>

        <TabPanel value={tabValue} index={0}>
          <Stack spacing={2}>
            <TextField
              label="Search Parameters"
              value={searchParams}
              onChange={(e) => setSearchParams(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., name=John&birthdate=gt1990-01-01"
              helperText="Enter FHIR search parameters"
            />

            <GradientButton
              onClick={handleSearch}
              disabled={searchMutation.isPending}
              startIcon={searchMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
              sx={{
                '&.Mui-disabled': {
                  background: 'rgba(0,0,0,0.12)',
                },
              }}
            >
              {searchMutation.isPending ? 'Searching...' : 'Search'}
            </GradientButton>

            {searchMutation.isError && (
              <Alert severity="error">
                Search failed: {(searchMutation.error as Error).message}
              </Alert>
            )}

            {searchResult && (
              <Box>
                <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                  <Typography variant="subtitle2">Results</Typography>
                  <Chip
                    label={`${getResourceCount(searchResult)} resources`}
                    size="small"
                    sx={{
                      bgcolor: 'rgba(13,115,119,0.1)',
                      color: 'primary.dark',
                      fontWeight: 600,
                    }}
                  />
                </Stack>
                <Box
                  component="pre"
                  sx={{
                    p: 2,
                    bgcolor: '#F8FAFB',
                    borderRadius: '8px',
                    border: '1px solid rgba(13,115,119,0.1)',
                    fontSize: '0.75rem',
                    overflow: 'auto',
                    maxHeight: 400,
                    fontFamily: '"Consolas", monospace',
                    color: 'text.primary',
                  }}
                >
                  {formatJson(searchResult) as string}
                </Box>
              </Box>
            )}
          </Stack>
        </TabPanel>

        <TabPanel value={tabValue} index={1}>
          <Stack spacing={2}>
            <TextField
              label="Resource ID"
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., example-patient-1"
            />

            <GradientButton
              onClick={handleRead}
              disabled={readMutation.isPending || !resourceId}
              startIcon={readMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
              sx={{
                '&.Mui-disabled': {
                  background: 'rgba(0,0,0,0.12)',
                },
              }}
            >
              {readMutation.isPending ? 'Loading...' : 'Read Resource'}
            </GradientButton>

            {readMutation.isError && (
              <Alert severity="error">
                Read failed: {(readMutation.error as Error).message}
              </Alert>
            )}

            {readResult && (
              <Box>
                <Typography variant="subtitle2" mb={1}>
                  Resource
                </Typography>
                <Box
                  component="pre"
                  sx={{
                    p: 2,
                    bgcolor: '#F8FAFB',
                    borderRadius: '8px',
                    border: '1px solid rgba(13,115,119,0.1)',
                    fontSize: '0.75rem',
                    overflow: 'auto',
                    maxHeight: 400,
                    fontFamily: '"Consolas", monospace',
                    color: 'text.primary',
                  }}
                >
                  {formatJson(readResult) as string}
                </Box>
              </Box>
            )}
          </Stack>
        </TabPanel>

        <TabPanel value={tabValue} index={2}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle2">Bulk Data Export ($export)</Typography>
              <HelpTooltip text={helpContent.fhir.bulkExport} />
            </Stack>

            {/* Export Configuration */}
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
                  sx={{
                    '&.Mui-disabled': {
                      background: 'rgba(0,0,0,0.12)',
                    },
                  }}
                >
                  {exportMutation.isPending ? 'Starting Export...' : 'Start Export'}
                </GradientButton>
              </>
            )}

            {/* Error display */}
            {exportError && (
              <Alert severity="error">
                Export error: {exportError}
              </Alert>
            )}

            {/* Polling status */}
            {exportStatusUrl && (
              <Box>
                <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                  <Typography variant="subtitle2">Export Status</Typography>
                  <Chip
                    label={getStatusLabel()}
                    size="small"
                    color={getStatusColor()}
                    sx={{ fontWeight: 600 }}
                  />
                  {exportStatus?.status === 'completed' && (
                    <Chip
                      label={`${getTotalResourceCount()} total resources`}
                      size="small"
                      sx={{
                        bgcolor: 'rgba(13,115,119,0.1)',
                        color: 'primary.dark',
                        fontWeight: 600,
                      }}
                    />
                  )}
                </Stack>

                {isPolling && <LinearProgress sx={{ mb: 2 }} />}

                <Stack direction="row" spacing={1} mb={2}>
                  {isPolling && (
                    <Button
                      variant="outlined"
                      color="warning"
                      onClick={handleStopPolling}
                      startIcon={<StopIcon />}
                      size="small"
                    >
                      Stop Polling
                    </Button>
                  )}
                  {(!isPolling || exportStatus?.status === 'completed' || exportStatus?.status === 'error') && (
                    <Button
                      variant="outlined"
                      onClick={handleResetExport}
                      startIcon={<ExportIcon />}
                      size="small"
                    >
                      New Export
                    </Button>
                  )}
                </Stack>

                {/* Results table */}
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
                          <TableCell sx={{ fontWeight: 600 }}>Resource Type</TableCell>
                          <TableCell sx={{ fontWeight: 600 }} align="right">Count</TableCell>
                          <TableCell sx={{ fontWeight: 600 }}>Download</TableCell>
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
        </TabPanel>
      </Stack>
    </Paper>
  )
}
