import { useState } from 'react'
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
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import {
  getResourceCount,
  getDisplayFields,
  extractPaginationLinks,
  extractSearchParamsFromUrl,
} from '../../utils/fhirBrowserUtils'
import ResourceDetailDialog from './ResourceDetailDialog'
import ResourceEditorDialog from './ResourceEditorDialog'
import SearchParamBuilder from './SearchParamBuilder'
import QueryHistory from './QueryHistory'
import useFhirQueryHistory from '../../hooks/useFhirQueryHistory'
import type { HistoryEntry } from '../../hooks/useFhirQueryHistory'

interface SearchTabProps {
  fhirServer: string
  resourceType: string
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export default function SearchTab({ fhirServer, resourceType }: SearchTabProps) {
  const [searchParams, setSearchParams] = useState('')
  const [searchMode, setSearchMode] = useState<'structured' | 'raw'>('structured')
  const [searchResult, setSearchResult] = useState<any>(null)
  const [selectedResource, setSelectedResource] = useState<any>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)

  const { recent, favorites, addEntry, toggleFavorite, removeEntry, clearHistory } = useFhirQueryHistory()

  const searchMutation = useMutation({
    mutationFn: (params?: { type?: string; raw?: string }) => {
      const rt = params?.type || resourceType
      const p = params?.raw ?? searchParams
      return fhirApi.search(rt, p, fhirServer)
    },
    onSuccess: (data) => {
      setSearchResult(data)
      addEntry(resourceType, searchParams, fhirServer)
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

  const paginationLinks = searchResult ? extractPaginationLinks(searchResult) : {}

  const handlePageNav = (url: string) => {
    const { resourceType: rt, params } = extractSearchParamsFromUrl(url)
    searchMutation.mutate({ type: rt, raw: params })
  }

  const entries: any[] = searchResult?.entry || []
  const fields = getDisplayFields(resourceType)

  const handleRowClick = (resource: any) => {
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
          {searchMutation.isPending ? 'Searching...' : 'Search'}
        </GradientButton>
        <Button
          variant="outlined"
          size="small"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{ textTransform: 'none' }}
        >
          Create Resource
        </Button>
      </Stack>

      {searchMutation.isError && (
        <Alert severity="error">
          Search failed: {(searchMutation.error as Error).message}
        </Alert>
      )}

      {searchResult && (
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" mb={1} justifyContent="space-between">
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle2">Results</Typography>
              <Chip
                label={`${getResourceCount(searchResult)} resources`}
                size="small"
                sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }}
              />
            </Stack>
            <Stack direction="row" spacing={0.5}>
              {paginationLinks.prev && (
                <Button
                  size="small"
                  startIcon={<PrevIcon />}
                  onClick={() => handlePageNav(paginationLinks.prev!)}
                  disabled={searchMutation.isPending}
                >
                  Prev
                </Button>
              )}
              {paginationLinks.next && (
                <Button
                  size="small"
                  endIcon={<NextIcon />}
                  onClick={() => handlePageNav(paginationLinks.next!)}
                  disabled={searchMutation.isPending}
                >
                  Next
                </Button>
              )}
            </Stack>
          </Stack>

          {entries.length > 0 ? (
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
                    <TableCell sx={{ fontWeight: 600 }}>Type</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>ID</TableCell>
                    {fields.map(f => (
                      <TableCell key={f.key} sx={{ fontWeight: 600 }}>{f.label}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {entries.map((entry: any, idx: number) => {
                    const resource = entry.resource || {}
                    return (
                      <TableRow
                        key={idx}
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
            <Alert severity="info">No resources found.</Alert>
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
/* eslint-enable @typescript-eslint/no-explicit-any */
