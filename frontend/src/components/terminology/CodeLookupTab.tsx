import { useState, useEffect, useMemo } from 'react'
import {
  Box,
  TextField,
  Stack,
  Typography,
  Button,
  CircularProgress,
  Alert,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Divider,
  Collapse,
} from '@mui/material'
import {
  Search as SearchIcon,
  ContentCopy as CopyIcon,
  ManageSearch as TextSearchIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useLookupCode, useSearchCodes } from '../../hooks/useTerminology'
import { useIgCodeSystems } from '../../hooks/useImplementationGuide'

const INTERNATIONAL_CODE_SYSTEMS = [
  { label: 'LOINC', url: 'http://loinc.org' },
  { label: 'SNOMED CT', url: 'http://snomed.info/sct' },
  { label: 'ICD-10-CM', url: 'http://hl7.org/fhir/sid/icd-10-cm' },
  { label: 'RxNorm', url: 'http://www.nlm.nih.gov/research/umls/rxnorm' },
  { label: 'CPT', url: 'http://www.ama-assn.org/go/cpt' },
]

export default function CodeLookupTab() {
  const [system, setSystem] = useState('')
  const [code, setCode] = useState('')
  const [searchText, setSearchText] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [showTextSearch, setShowTextSearch] = useState(false)
  const [showIntlSystems, setShowIntlSystems] = useState(true)
  const lookupMutation = useLookupCode()
  const { data: searchResults, isFetching: isSearching } = useSearchCodes(system, debouncedSearch)
  const { data: igCodeSystems } = useIgCodeSystems()

  const twcoreCodeSystems = useMemo(() => {
    if (!igCodeSystems || igCodeSystems.length === 0) return []
    return igCodeSystems.map((cs) => ({
      label: cs.title || cs.name,
      url: cs.url,
    }))
  }, [igCodeSystems])

  // Combined list for label lookup (TWCORE first, then international)
  const ALL_CODE_SYSTEMS = useMemo(
    () => [...twcoreCodeSystems, ...INTERNATIONAL_CODE_SYSTEMS],
    [twcoreCodeSystems]
  )

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 500)
    return () => clearTimeout(timer)
  }, [searchText])

  const handleLookup = () => {
    if (system && code) {
      lookupMutation.mutate({ system, code })
    }
  }

  const handleCopyCql = () => {
    if (!lookupMutation.data) return
    const d = lookupMutation.data
    const systemLabel = ALL_CODE_SYSTEMS.find((cs) => cs.url === d.system)?.label || d.system
    const cql = `code "${d.display}": '${d.code}' from "${systemLabel}"`
    navigator.clipboard.writeText(cql)
  }

  const handleSelectSearchResult = (resultCode: string) => {
    setCode(resultCode)
    lookupMutation.mutate({ system, code: resultCode })
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          TW Core IG Code Systems
        </Typography>
        {twcoreCodeSystems.length > 0 ? (
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
            {twcoreCodeSystems.map((cs) => (
              <Chip
                key={cs.url}
                label={cs.label}
                size="small"
                variant={system === cs.url ? 'filled' : 'outlined'}
                color={system === cs.url ? 'primary' : 'default'}
                onClick={() => setSystem(cs.url)}
                sx={{ cursor: 'pointer', maxWidth: 280 }}
              />
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', mb: 1 }}>
            Loading TW Core IG code systems...
          </Typography>
        )}
        <Button
          size="small"
          variant="text"
          onClick={() => setShowIntlSystems(!showIntlSystems)}
          sx={{ mb: 0.5, textTransform: 'none', fontSize: '0.75rem' }}
        >
          {showIntlSystems ? 'Hide International Systems' : 'Show International Systems'}
        </Button>
        <Collapse in={showIntlSystems}>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
            {INTERNATIONAL_CODE_SYSTEMS.map((cs) => (
              <Chip
                key={cs.url}
                label={cs.label}
                size="small"
                variant={system === cs.url ? 'filled' : 'outlined'}
                color={system === cs.url ? 'secondary' : 'default'}
                onClick={() => setSystem(cs.url)}
                sx={{ cursor: 'pointer' }}
              />
            ))}
          </Stack>
        </Collapse>
      </Box>

      <TextField
        label="Code System URL"
        value={system}
        onChange={(e) => setSystem(e.target.value)}
        size="small"
        fullWidth
        placeholder="http://loinc.org"
      />

      {/* Text Search Section */}
      <Box>
        <Button
          size="small"
          startIcon={<TextSearchIcon />}
          onClick={() => setShowTextSearch(!showTextSearch)}
          sx={{ mb: 0.5 }}
        >
          {showTextSearch ? 'Hide Text Search' : 'Search by Text'}
        </Button>
        <Collapse in={showTextSearch}>
          <Stack spacing={1.5} sx={{ p: 1.5, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
            <TextField
              label="Search text"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              size="small"
              fullWidth
              placeholder='e.g., "blood pressure"'
              disabled={!system}
              helperText={!system ? 'Select a code system first' : undefined}
              InputProps={{
                endAdornment: isSearching ? <CircularProgress size={18} /> : null,
              }}
            />
            {searchResults && searchResults.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 250 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell scope="col" sx={{ fontWeight: 600, py: 0.75 }}>Code</TableCell>
                      <TableCell scope="col" sx={{ fontWeight: 600, py: 0.75 }}>Display</TableCell>
                      <TableCell scope="col" sx={{ width: 40, py: 0.75 }} />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {searchResults.map((r) => (
                      <TableRow
                        key={r.code}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => handleSelectSearchResult(r.code)}
                      >
                        <TableCell sx={{ py: 0.5, fontFamily: 'monospace', fontSize: '0.8rem' }}>
                          {r.code}
                        </TableCell>
                        <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }}>
                          {r.display}
                        </TableCell>
                        <TableCell sx={{ py: 0.5 }}>
                          <IconButton
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation()
                              const label = ALL_CODE_SYSTEMS.find((cs) => cs.url === r.system)?.label || r.system
                              navigator.clipboard.writeText(
                                `code "${r.display}": '${r.code}' from "${label}"`
                              )
                            }}
                            aria-label="Copy code"
                          >
                            <CopyIcon fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
            {searchResults && searchResults.length === 0 && debouncedSearch.length >= 2 && !isSearching && (
              <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                No results found for &quot;{debouncedSearch}&quot;
              </Typography>
            )}
          </Stack>
        </Collapse>
      </Box>

      <Divider />

      <TextField
        label="Code"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        size="small"
        fullWidth
        placeholder="e.g., 4548-4"
        onKeyDown={(e) => { if (e.key === 'Enter') handleLookup() }}
      />

      <GradientButton
        onClick={handleLookup}
        disabled={lookupMutation.isPending || !system || !code}
        startIcon={lookupMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
        sx={{
          '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' },
        }}
      >
        {lookupMutation.isPending ? 'Looking up...' : 'Lookup Code'}
      </GradientButton>

      {lookupMutation.isError && (
        <Alert severity="error">
          Lookup failed: {(lookupMutation.error as Error).message}
        </Alert>
      )}

      {lookupMutation.data && (
        <Paper
          elevation={0}
          sx={{
            p: 2,
            bgcolor: 'rgba(13,115,119,0.04)',
            border: '1px solid rgba(13,115,119,0.15)',
            borderRadius: '8px',
          }}
        >
          <Stack spacing={1.5}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle2" color="primary.dark">Code Details</Typography>
              <Button size="small" startIcon={<CopyIcon />} onClick={handleCopyCql}>
                Copy to CQL
              </Button>
            </Stack>

            <Box>
              <Typography variant="caption" color="text.secondary">System</Typography>
              <Typography variant="body2">{lookupMutation.data.system}</Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Code</Typography>
              <Typography variant="body2" fontWeight={600}>{lookupMutation.data.code}</Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Display</Typography>
              <Typography variant="body2">{lookupMutation.data.display}</Typography>
            </Box>

            {lookupMutation.data.name && (
              <Box>
                <Typography variant="caption" color="text.secondary">Name</Typography>
                <Typography variant="body2">{lookupMutation.data.name}</Typography>
              </Box>
            )}

            {lookupMutation.data.designations?.length > 0 && (
              <Box>
                <Typography variant="caption" color="text.secondary">Designations</Typography>
                <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                  {lookupMutation.data.designations.map((d, i) => (
                    <Chip key={i} label={d} size="small" variant="outlined" />
                  ))}
                </Stack>
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  )
}
