import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
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
  const { t } = useTranslation('terminology')
  const [searchTitle, setSearchTitle] = useState('')
  const [selectedVs, setSelectedVs] = useState<ValueSetSearchResult | null>(null)
  const [codeFilter, setCodeFilter] = useState('')
  const [source, setSource] = useState<SourceMode>('remote')

  const debouncedTitle = useDebouncedValue(searchTitle, SEARCH_DEBOUNCE_GENERAL_MS)

  const { data: remoteResults, isLoading: isSearchingRemote, error: remoteError } = useSearchValueSets(
    (source === 'remote' || source === 'both') ? debouncedTitle : undefined
  )
  const { data: localResults, isLoading: isSearchingLocal } = useIgValueSets(
    (source === 'local' || source === 'both') ? (debouncedTitle || undefined) : undefined
  )
  const expandMutation = useExpandValueSet()

  const isSearching = isSearchingRemote || isSearchingLocal

  // Merge results based on source
  const searchResults = useMemo(() => {
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
  }, [source, remoteResults, localResults])

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

  const filteredCodes = useMemo(() => {
    const codes = expandMutation.data?.expansion?.contains
    if (!codes) return []
    if (!codeFilter) return codes
    const q = codeFilter.toLowerCase()
    return codes.filter(
      (c) =>
        c.code.toLowerCase().includes(q) ||
        c.display.toLowerCase().includes(q) ||
        c.system.toLowerCase().includes(q)
    )
  }, [expandMutation.data, codeFilter])

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          label={t('valueSet.searchLabel')}
          value={searchTitle}
          onChange={(e) => setSearchTitle(e.target.value)}
          size="small"
          sx={{ flex: 1 }}
          placeholder={t('valueSet.searchPlaceholder')}
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
          <ToggleButton value="remote" title={t('valueSet.sourceRemoteTitle')}>
            <RemoteIcon fontSize="small" sx={{ mr: 0.5 }} />
            <Typography variant="caption">{t('valueSet.sourceRemote')}</Typography>
          </ToggleButton>
          <ToggleButton value="local" title={t('valueSet.sourceLocalTitle')}>
            <LocalIcon fontSize="small" sx={{ mr: 0.5 }} />
            <Typography variant="caption">{t('valueSet.sourceLocal')}</Typography>
          </ToggleButton>
          <ToggleButton value="both" title={t('valueSet.sourceBothTitle')}>
            <Typography variant="caption">{t('valueSet.sourceBoth')}</Typography>
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {searchError && (
        <Alert severity="error">{t('valueSet.searchFailed', { error: (searchError as Error).message })}</Alert>
      )}

      {searchResults && searchResults.length > 0 && (
        <Box>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            {t('valueSet.resultCount', { count: searchResults.length })}
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
                          label={vs.source === 'local' ? t('valueSet.chipLocalIg') : t('valueSet.chipRemote')}
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
                      title={t('valueSet.copyCql')}
                      onClick={(e) => { e.stopPropagation(); handleCopyCql(vs) }}
                      aria-label={t('valueSet.copyCql')}
                    >
                      <CopyIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" title={t('valueSet.expand')} aria-label={t('valueSet.expand')}>
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
        <Typography color="text.secondary" variant="body2">{t('valueSet.noResults')}</Typography>
      )}

      {expandMutation.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}

      {expandMutation.isError && (
        <Alert severity="error">
          {t('valueSet.expandFailed', { error: (expandMutation.error as Error).message })}
        </Alert>
      )}

      {expandMutation.data && selectedVs && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2">
              {t('valueSet.codesIn', { name: selectedVs.title || selectedVs.name })}
            </Typography>
            <Chip
              label={`${expandMutation.data.expansion?.total ?? filteredCodes.length}`}
              size="small"
              sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }}
            />
          </Stack>
          <TextField
            label={t('valueSet.filterCodes')}
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
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('valueSet.colSystem')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('valueSet.colCode')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('valueSet.colDisplay')}</TableCell>
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
                      <Typography variant="body2" color="text.secondary">{t('valueSet.noCodesFound')}</Typography>
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
