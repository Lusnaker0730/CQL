import { useState, useMemo } from 'react'
import { alpha } from '@mui/material/styles'
import {
  Stack,
  CircularProgress,
  Alert,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Button,
  Box,
} from '@mui/material'
import {
  Search as SearchIcon,
  Add as AddIcon,
  NavigateBefore as PrevIcon,
  NavigateNext as NextIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { extractApiError } from '../../utils/errorUtils'
import {
  getResourceCount,
  getDisplayFields,
} from '../../utils/fhirBrowserUtils'
import type { FhirResource } from '../../utils/fhirBrowserUtils'
import ResourceDetailDialog from './ResourceDetailDialog'
import ResourceEditorDialog from './ResourceEditorDialog'
import SearchParamBuilder from './SearchParamBuilder'
import QueryHistory from './QueryHistory'
import useFhirQueryHistory from '../../hooks/useFhirQueryHistory'
import type { HistoryEntry } from '../../hooks/useFhirQueryHistory'

/** A single entry in a FHIR Bundle search response. */
interface FhirBundleEntry {
  resource?: FhirResource
  fullUrl?: string
  search?: { mode?: string; score?: number }
}

/** A FHIR Bundle returned from a search operation. */
interface FhirBundle extends Record<string, unknown> {
  resourceType: 'Bundle'
  type?: string
  total?: number
  entry?: FhirBundleEntry[]
  link?: Array<{ relation: string; url: string }>
}

const PAGE_SIZE = 20

interface SearchTabProps {
  fhirServer: string
  resourceType: string
}

export default function SearchTab({ fhirServer, resourceType }: SearchTabProps) {
  const { t } = useTranslation('fhir')
  const [searchParams, setSearchParams] = useState('')
  const [searchMode, setSearchMode] = useState<'structured' | 'raw'>('structured')
  const [searchResult, setSearchResult] = useState<FhirBundle | null>(null)
  const [selectedResource, setSelectedResource] = useState<FhirResource | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [currentPage, setCurrentPage] = useState(0)

  const { recent, favorites, addEntry, toggleFavorite, removeEntry, clearHistory } = useFhirQueryHistory()

  const searchMutation = useMutation({
    mutationFn: (params?: { type?: string; raw?: string }) => {
      const rt = params?.type || resourceType
      const p = params?.raw ?? searchParams
      return fhirApi.search(rt, p, fhirServer)
    },
    // Read the actual params we searched with from `variables` rather than
    // closure-captured React state — `setSearchParams` from history-replay
    // hasn't necessarily committed yet when this fires, so reading state
    // would record the *previous* search and history would drift.
    onSuccess: (data, variables) => {
      setSearchResult(data as FhirBundle)
      setCurrentPage(0)
      const recordedType = variables?.type || resourceType
      const recordedParams = variables?.raw ?? searchParams
      addEntry(recordedType, recordedParams, fhirServer)
    },
  })

  const handleSearch = () => {
    searchMutation.mutate({})
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch()
    }
  }

  const allEntries = useMemo(() => searchResult?.entry || [], [searchResult])
  const totalEntries = allEntries.length
  const totalPages = Math.ceil(totalEntries / PAGE_SIZE)

  const pagedEntries = useMemo(
    () => allEntries.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE),
    [allEntries, currentPage]
  )

  const fields = getDisplayFields(resourceType)

  const handleRowClick = (resource: FhirResource) => {
    setSelectedResource(resource)
    setDetailOpen(true)
  }

  const handleHistorySelect = (entry: HistoryEntry) => {
    setSearchParams(entry.params)
    searchMutation.mutate({ type: entry.resourceType, raw: entry.params })
  }

  const handleRefresh = () => {
    searchMutation.mutate({})
  }

  const handlePrevPage = () => {
    setCurrentPage(p => Math.max(0, p - 1))
  }

  const handleNextPage = () => {
    setCurrentPage(p => Math.min(totalPages - 1, p + 1))
  }

  return (
    <Stack spacing={2} onKeyDown={handleKeyDown}>
      <QueryHistory
        recent={recent}
        favorites={favorites}
        onSelect={handleHistorySelect}
        onToggleFavorite={toggleFavorite}
        onRemove={removeEntry}
        onClearHistory={clearHistory}
      />

      <SearchParamBuilder
        resourceType={resourceType}
        value={searchParams}
        onChange={setSearchParams}
        mode={searchMode}
        onModeChange={setSearchMode}
      />

      <Stack direction="row" spacing={1}>
        <GradientButton
          onClick={handleSearch}
          disabled={searchMutation.isPending}
          startIcon={searchMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
          sx={{ '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
        >
          {searchMutation.isPending ? t('search.searching') : t('search.searchButton')}
        </GradientButton>
        <Button
          variant="outlined"
          size="small"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{ textTransform: 'none' }}
        >
          {t('search.createResource')}
        </Button>
      </Stack>

      {searchMutation.isError && (
        <Alert severity="error">
          {t('search.searchFailed', { error: extractApiError(searchMutation.error) })}
        </Alert>
      )}

      {searchMutation.isPending && (
        <Alert severity="info">{t('search.searching')}</Alert>
      )}

      {searchResult && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" mb={1} justifyContent="space-between">
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle2">{t('search.results')}</Typography>
              <Chip
                label={t('search.resourceCount', { count: totalEntries || getResourceCount(searchResult) })}
                size="small"
                sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), color: 'primary.main', fontWeight: 600 }}
              />
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              {totalPages > 1 && (
                <Typography variant="caption" color="text.secondary">
                  {currentPage + 1} / {totalPages}
                </Typography>
              )}
              <Stack direction="row" spacing={0.5}>
                {currentPage > 0 && (
                  <Button
                    size="small"
                    startIcon={<PrevIcon />}
                    onClick={handlePrevPage}
                  >
                    {t('search.prev')}
                  </Button>
                )}
                {currentPage < totalPages - 1 && (
                  <Button
                    size="small"
                    endIcon={<NextIcon />}
                    onClick={handleNextPage}
                  >
                    {t('search.next')}
                  </Button>
                )}
              </Stack>
            </Stack>
          </Stack>

          {pagedEntries.length > 0 ? (
            <TableContainer
              sx={{
                bgcolor: 'action.hover',
                borderRadius: '8px',
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>{t('search.colType')}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t('search.colId')}</TableCell>
                    {fields.map(f => (
                      <TableCell key={f.key} sx={{ fontWeight: 600 }}>{f.label}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pagedEntries.map((entry, idx) => {
                    const resource: FhirResource = entry.resource || { resourceType: '' }
                    return (
                      <TableRow
                        key={resource.id || idx}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => handleRowClick(resource)}
                      >
                        <TableCell>{resource.resourceType || ''}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                          {resource.id || ''}
                        </TableCell>
                        {fields.map(f => (
                          <TableCell key={f.key} sx={{ fontSize: '0.8rem', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {f.extract(resource)}
                          </TableCell>
                        ))}
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <Alert severity="info">{t('search.noResources')}</Alert>
          )}
        </Box>
      )}

      <ResourceDetailDialog
        open={detailOpen}
        resource={selectedResource}
        resourceType={selectedResource?.resourceType || resourceType}
        resourceId={selectedResource?.id || ''}
        fhirServer={fhirServer}
        onClose={() => setDetailOpen(false)}
        onDeleted={handleRefresh}
        onUpdated={handleRefresh}
      />

      <ResourceEditorDialog
        open={createOpen}
        mode="create"
        resourceType={resourceType}
        fhirServer={fhirServer}
        onClose={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false)
          handleRefresh()
        }}
      />
    </Stack>
  )
}
