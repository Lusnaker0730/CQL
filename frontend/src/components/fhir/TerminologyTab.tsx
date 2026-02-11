import { useState } from 'react'
import {
  Stack,
  TextField,
  Alert,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Paper,
  Box,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Search as SearchIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'

export default function TerminologyTab() {
  // ValueSet Expand
  const [vsUrl, setVsUrl] = useState('')
  const [vsFilter, setVsFilter] = useState('')

  // Code Lookup
  const [lookupSystem, setLookupSystem] = useState('')
  const [lookupCode, setLookupCode] = useState('')

  // Code Search
  const [searchSystem, setSearchSystem] = useState('')
  const [searchText, setSearchText] = useState('')

  const expandMutation = useMutation({
    mutationFn: () => fhirApi.expandValueSet(vsUrl, vsFilter || undefined),
  })

  const lookupMutation = useMutation({
    mutationFn: () => fhirApi.lookupCode(lookupSystem, lookupCode),
  })

  const searchMutation = useMutation({
    mutationFn: () => fhirApi.searchCodes(searchSystem, searchText),
  })

  const expandCodes = expandMutation.data?.expansion?.contains || []
  const lookupData = lookupMutation.data
  const searchResults = searchMutation.data || []

  return (
    <Stack spacing={2}>
      <Alert severity="info" sx={{ '& .MuiAlert-message': { fontSize: '0.8rem' } }}>
        Terminology operations are routed through the platform&apos;s terminology server. Use this as a quick lookup tool. For comprehensive terminology browsing, visit the dedicated Terminology page.
      </Alert>

      {/* ValueSet Expand */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">ValueSet $expand</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label="ValueSet URL"
              value={vsUrl}
              onChange={(e) => setVsUrl(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., http://hl7.org/fhir/ValueSet/administrative-gender"
            />
            <TextField
              label="Filter (optional)"
              value={vsFilter}
              onChange={(e) => setVsFilter(e.target.value)}
              size="small"
              fullWidth
              placeholder="Text filter for codes"
            />
            <GradientButton
              onClick={() => expandMutation.mutate()}
              disabled={expandMutation.isPending || !vsUrl}
              startIcon={expandMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              Expand
            </GradientButton>
            {expandMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {(expandMutation.error as Error).message}
              </Alert>
            )}
            {expandCodes.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>System</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Code</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Display</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {expandCodes.map((c, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ fontSize: '0.75rem', fontFamily: 'monospace' }}>{c.system || ''}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{c.code || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{c.display || ''}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>

      {/* Code Lookup */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">Code $lookup</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label="System"
              value={lookupSystem}
              onChange={(e) => setLookupSystem(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., http://loinc.org"
            />
            <TextField
              label="Code"
              value={lookupCode}
              onChange={(e) => setLookupCode(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., 8302-2"
            />
            <GradientButton
              onClick={() => lookupMutation.mutate()}
              disabled={lookupMutation.isPending || !lookupSystem || !lookupCode}
              startIcon={lookupMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              Lookup
            </GradientButton>
            {lookupMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {(lookupMutation.error as Error).message}
              </Alert>
            )}
            {lookupData && (
              <Box
                sx={{
                  p: 2,
                  bgcolor: '#F8FAFB',
                  borderRadius: '8px',
                  border: '1px solid rgba(13,115,119,0.1)',
                }}
              >
                <Typography variant="body2"><strong>Display:</strong> {lookupData.display || '-'}</Typography>
                {lookupData.designations && lookupData.designations.length > 0 && (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary">Designations:</Typography>
                    {lookupData.designations.map((d, idx) => (
                      <Typography key={idx} variant="body2" sx={{ ml: 1, fontSize: '0.8rem' }}>
                        {d}
                      </Typography>
                    ))}
                  </Box>
                )}
              </Box>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>

      {/* Code Search */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">Code Search</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label="System"
              value={searchSystem}
              onChange={(e) => setSearchSystem(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., http://loinc.org"
            />
            <TextField
              label="Search Text"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              size="small"
              fullWidth
              placeholder="e.g., blood pressure"
            />
            <GradientButton
              onClick={() => searchMutation.mutate()}
              disabled={searchMutation.isPending || !searchSystem || !searchText}
              startIcon={searchMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              Search
            </GradientButton>
            {searchMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {(searchMutation.error as Error).message}
              </Alert>
            )}
            {searchResults.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>Code</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Display</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>System</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {searchResults.map((r, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{r.code || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{r.display || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.75rem', fontFamily: 'monospace' }}>{r.system || ''}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>
    </Stack>
  )
}
