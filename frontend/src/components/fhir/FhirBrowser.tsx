import { useState } from 'react'
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
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'

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
  const [resourceType, setResourceType] = useState('Patient')
  const [searchParams, setSearchParams] = useState('')
  const [resourceId, setResourceId] = useState('')
  const [searchResult, setSearchResult] = useState<object | null>(null)
  const [readResult, setReadResult] = useState<object | null>(null)

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

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        FHIR Browser
      </Typography>

      <Stack spacing={2}>
        <TextField
          label="FHIR Server URL"
          value={fhirServer}
          onChange={(e) => setFhirServer(e.target.value)}
          size="small"
          fullWidth
        />

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

            <Button
              variant="contained"
              onClick={handleSearch}
              disabled={searchMutation.isPending}
              startIcon={searchMutation.isPending ? <CircularProgress size={20} /> : <SearchIcon />}
            >
              {searchMutation.isPending ? 'Searching...' : 'Search'}
            </Button>

            {searchMutation.isError && (
              <Alert severity="error">
                Search failed: {(searchMutation.error as Error).message}
              </Alert>
            )}

            {searchResult && (
              <Box>
                <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                  <Typography variant="subtitle2">Results</Typography>
                  <Chip label={`${getResourceCount(searchResult)} resources`} size="small" />
                </Stack>
                <Box
                  component="pre"
                  sx={{
                    p: 2,
                    bgcolor: 'grey.100',
                    borderRadius: 1,
                    fontSize: '0.75rem',
                    overflow: 'auto',
                    maxHeight: 400,
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

            <Button
              variant="contained"
              onClick={handleRead}
              disabled={readMutation.isPending || !resourceId}
              startIcon={readMutation.isPending ? <CircularProgress size={20} /> : <SearchIcon />}
            >
              {readMutation.isPending ? 'Loading...' : 'Read Resource'}
            </Button>

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
                    bgcolor: 'grey.100',
                    borderRadius: 1,
                    fontSize: '0.75rem',
                    overflow: 'auto',
                    maxHeight: 400,
                  }}
                >
                  {formatJson(readResult) as string}
                </Box>
              </Box>
            )}
          </Stack>
        </TabPanel>
      </Stack>
    </Paper>
  )
}
