import { useState } from 'react'
import {
  Box,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TextField,
  InputAdornment,
  IconButton,
  Typography,
  Stack,
  Tooltip,
  CircularProgress,
  Alert,
} from '@mui/material'
import {
  Search as SearchIcon,
  Delete as DeleteIcon,
  ContentCopy as DuplicateIcon,
  Edit as EditIcon,
} from '@mui/icons-material'
import StatusChip from '../common/StatusChip'
import GradientButton from '../common/GradientButton'
import type { ArtifactSummary } from '../../types/authoring'

type SortField = 'name' | 'version' | 'status' | 'updatedAt'
type SortDir = 'asc' | 'desc'

interface ArtifactListProps {
  artifacts: ArtifactSummary[]
  loading: boolean
  error: unknown
  onSelect: (artifact: ArtifactSummary) => void
  onCreate: () => void
  onImport: () => void
  onDelete: (id: number) => void
  onDuplicate: (id: number) => void
}

export default function ArtifactList({
  artifacts,
  loading,
  error,
  onSelect,
  onCreate,
  onImport,
  onDelete,
  onDuplicate,
}: ArtifactListProps) {
  const [search, setSearch] = useState('')
  const [sortField, setSortField] = useState<SortField>('updatedAt')
  const [sortDir, setSortDir] = useState<SortDir>('desc')

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDir('asc')
    }
  }

  const filtered = artifacts
    .filter((a) => {
      if (!search) return true
      const q = search.toLowerCase()
      return a.name.toLowerCase().includes(q) || (a.description || '').toLowerCase().includes(q)
    })
    .sort((a, b) => {
      const dir = sortDir === 'asc' ? 1 : -1
      switch (sortField) {
        case 'name':
          return a.name.localeCompare(b.name) * dir
        case 'version':
          return a.version.localeCompare(b.version) * dir
        case 'status':
          return a.status.localeCompare(b.status) * dir
        case 'updatedAt':
          return a.updatedAt.localeCompare(b.updatedAt) * dir
        default:
          return 0
      }
    })

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', p: 2 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">CDS Artifacts</Typography>
          <Stack direction="row" spacing={1}>
            <GradientButton onClick={onImport} variant="outlined">Import CQL</GradientButton>
            <GradientButton onClick={onCreate}>New Artifact</GradientButton>
          </Stack>
        </Stack>

        <TextField
          size="small"
          placeholder="Search artifacts..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ mb: 2 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />

        {!!error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            Failed to load artifacts
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : filtered.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Typography color="text.secondary">
              {search ? 'No artifacts match your search.' : 'No artifacts yet. Create one to get started.'}
            </Typography>
          </Box>
        ) : (
          <TableContainer sx={{ flex: 1 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'name'}
                      direction={sortField === 'name' ? sortDir : 'asc'}
                      onClick={() => handleSort('name')}
                    >
                      Name
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'version'}
                      direction={sortField === 'version' ? sortDir : 'asc'}
                      onClick={() => handleSort('version')}
                    >
                      Version
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'status'}
                      direction={sortField === 'status' ? sortDir : 'asc'}
                      onClick={() => handleSort('status')}
                    >
                      Status
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'updatedAt'}
                      direction={sortField === 'updatedAt' ? sortDir : 'asc'}
                      onClick={() => handleSort('updatedAt')}
                    >
                      Updated
                    </TableSortLabel>
                  </TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((artifact) => (
                  <TableRow
                    key={artifact.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => onSelect(artifact)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>
                        {artifact.name}
                      </Typography>
                      {artifact.description && (
                        <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 300, display: 'block' }}>
                          {artifact.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{artifact.version}</TableCell>
                    <TableCell>
                      <StatusChip status={artifact.status} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {new Date(artifact.updatedAt).toLocaleDateString()}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={0} justifyContent="flex-end">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation()
                              onSelect(artifact)
                            }}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Duplicate">
                          <IconButton
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation()
                              onDuplicate(artifact.id)
                            }}
                          >
                            <DuplicateIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={(e) => {
                              e.stopPropagation()
                              onDelete(artifact.id)
                            }}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  )
}
