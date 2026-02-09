import { useState, useEffect } from 'react'
import {
  Box,
  TextField,
  Stack,
  Typography,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  InputAdornment,
  ToggleButtonGroup,
  ToggleButton,
} from '@mui/material'
import {
  Search as SearchIcon,
  ExpandMore as ExpandIcon,
  ContentCopy as CopyIcon,
  Cloud as RemoteIcon,
  Storage as LocalIcon,
} from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'
import { useIgValueSets } from '../../hooks/useImplementationGuide'
import type { ValueSetSearchResult, ValueSetSummary } from '../../types'

type SourceMode = 'remote' | 'local' | 'both'

export default function ValueSetTab() {
  const [searchTitle, setSearchTitle] = useState('')
  const [debouncedTitle, setDebouncedTitle] = useState('')
  const [selectedVs, setSelectedVs] = useState<ValueSetSearchResult | null>(null)
  const [codeFilter, setCodeFilter] = useState('')
  const [source, setSource] = useState<SourceMode>('remote')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedTitle(searchTitle), 300)
    return () => clearTimeout(timer)
  }, [searchTitle])

  const { data: remoteResults, isLoading: isSearchingRemote, error: remoteError } = useSearchValueSets(
    (source === 'remote' || source === 'both') ? debouncedTitle : undefined
  )
  const { data: localResults, isLoading: isSearchingLocal } = useIgValueSets(
    (source === 'local' || source === 'both') ? (debouncedTitle || undefined) : undefined
  )
  const expandMutation = useExpandValueSet()

  const isSearching = isSearchingRemote || isSearchingLocal

  // Merge results based on source
  const searchResults: (ValueSetSearchResult & { source?: string })[] = (() => {
    const results: (ValueSetSearchResult & { source?: string })[] = []
    if (source === 'remote' || source === 'both') {
      if (remoteResults) {
        results.push(...remoteResults.map(r => ({ ...r, source: 'remote' as const })))
      }
    }
    if (source === 'local' || source === 'both') {
      if (localResults) {
        const mapped = localResults.map((vs: ValueSetSummary) => ({
          id: vs.url,
          url: vs.url,
          name: vs.name || '',
          title: vs.title || '',
          source: 'local' as const,
        }))
        // Avoid duplicates by URL
        const existingUrls = new Set(results.map(r => r.url))
        results.push(...mapped.filter(m => !existingUrls.has(m.url)))
      }
    }
    return results
  })()

  const searchError = remoteError

  const handleExpand = (vs: ValueSetSearchResult) => {
    setSelectedVs(vs)
    setCodeFilter('')
    expandMutation.mutate({ url: vs.url })
  }

  const handleCopyCql = (vs: ValueSetSearchResult) => {
    const cql = `valueset "${vs.title || vs.name}": '${vs.url}'`
    navigator.clipboard.writeText(cql)
  }

  const filteredCodes = expandMutation.data?.expansion?.contains?.filter(
    (c) =>
      !codeFilter ||
      c.code.toLowerCase().includes(codeFilter.toLowerCase()) ||
      c.display.toLowerCase().includes(codeFilter.toLowerCase()) ||
      c.system.toLowerCase().includes(codeFilter.toLowerCase())
  ) || []

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          label="Search ValueSets by title"
          value={searchTitle}
          onChange={(e) => setSearchTitle(e.target.value)}
          size="small"
          sx={{ flex: 1 }}
          placeholder="e.g., diabetes, blood pressure"
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ color: 'text.secondary' }} />
              </InputAdornment>
            ),
            endAdornment: isSearching ? (
              <InputAdornment position="end">
                <CircularProgress size={18} />
              </InputAdornment>
            ) : undefined,
          }}
        />
        <ToggleButtonGroup
          value={source}
          exclusive
          onChange={(_, val) => val && setSource(val as SourceMode)}
          size="small"
        >
          <ToggleButton value="remote" title="Remote terminology server">
            <RemoteIcon fontSize="small" sx={{ mr: 0.5 }} />
            <Typography variant="caption">Remote</Typography>
          </ToggleButton>
          <ToggleButton value="local" title="Local IG packages">
            <LocalIcon fontSize="small" sx={{ mr: 0.5 }} />
            <Typography variant="caption">Local IG</Typography>
          </ToggleButton>
          <ToggleButton value="both" title="Both sources">
            <Typography variant="caption">Both</Typography>
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {searchError && (
        <Alert severity="error">Search failed: {(searchError as Error).message}</Alert>
      )}

      {searchResults && searchResults.length > 0 && (
        <Box>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            {searchResults.length} result{searchResults.length !== 1 ? 's' : ''}
          </Typography>
          <Stack spacing={1}>
            {searchResults.map((vs) => (
              <Paper
                key={vs.id || vs.url}
                elevation={0}
                sx={{
                  p: 1.5,
                  border: '1px solid',
                  borderColor: selectedVs?.url === vs.url ? 'primary.main' : 'rgba(0,0,0,0.08)',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                  '&:hover': { borderColor: 'primary.light', bgcolor: 'rgba(13,115,119,0.02)' },
                }}
                onClick={() => handleExpand(vs)}
              >
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2" fontWeight={600} noWrap>
                        {vs.title || vs.name}
                      </Typography>
                      {vs.source && (
                        <Chip
                          label={vs.source === 'local' ? 'Local IG' : 'Remote'}
                          size="small"
                          variant="outlined"
                          color={vs.source === 'local' ? 'success' : 'default'}
                          sx={{ height: 20, fontSize: '0.65rem' }}
                        />
                      )}
                    </Stack>
                    <Typography variant="caption" color="text.secondary" noWrap>
                      {vs.url}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={0.5}>
                    <IconButton
                      size="small"
                      title="Copy to CQL"
                      onClick={(e) => { e.stopPropagation(); handleCopyCql(vs) }}
                    >
                      <CopyIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" title="Expand">
                      <ExpandIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                </Stack>
              </Paper>
            ))}
          </Stack>
        </Box>
      )}

      {searchResults && searchResults.length === 0 && debouncedTitle.length >= 2 && (
        <Typography color="text.secondary" variant="body2">No ValueSets found.</Typography>
      )}

      {expandMutation.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}

      {expandMutation.isError && (
        <Alert severity="error">
          Expand failed: {(expandMutation.error as Error).message}
        </Alert>
      )}

      {expandMutation.data && selectedVs && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2">
              Codes in {selectedVs.title || selectedVs.name}
            </Typography>
            <Chip
              label={`${expandMutation.data.expansion?.total ?? filteredCodes.length}`}
              size="small"
              sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }}
            />
          </Stack>
          <TextField
            label="Filter codes"
            value={codeFilter}
            onChange={(e) => setCodeFilter(e.target.value)}
            size="small"
            fullWidth
            sx={{ mb: 1 }}
          />
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
                {filteredCodes.map((c, i) => (
                  <TableRow key={`${c.system}-${c.code}-${i}`} hover>
                    <TableCell sx={{ fontSize: '0.75rem', maxWidth: 200 }}>
                      <Typography variant="caption" noWrap>{c.system}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={c.code} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{c.display}</TableCell>
                  </TableRow>
                ))}
                {filteredCodes.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3}>
                      <Typography variant="body2" color="text.secondary">No codes found.</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}
    </Stack>
  )
}
