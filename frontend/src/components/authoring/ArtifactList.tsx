import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  Alert,
  Skeleton,
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
  const { t } = useTranslation('authoring')
  const { t: tc } = useTranslation('common')
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
          <Typography variant="h6">{t('list.title')}</Typography>
          <Stack direction="row" spacing={1}>
            <GradientButton onClick={onImport} variant="outlined">{t('list.importCql')}</GradientButton>
            <GradientButton onClick={onCreate}>{t('list.newArtifact')}</GradientButton>
          </Stack>
        </Stack>

        <TextField
          size="small"
          placeholder={t('list.searchPlaceholder')}
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
            {t('list.loadError')}
          </Alert>
        )}

        {loading ? (
          <TableContainer sx={{ flex: 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('list.colName')}</TableCell>
                  <TableCell>{t('list.colVersion')}</TableCell>
                  <TableCell>{t('list.colStatus')}</TableCell>
                  <TableCell>{t('list.colUpdated')}</TableCell>
                  <TableCell align="right">{t('list.colActions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {Array.from({ length: 5 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell><Skeleton variant="text" width={180} /><Skeleton variant="text" width={120} height={14} /></TableCell>
                    <TableCell><Skeleton variant="text" width={50} /></TableCell>
                    <TableCell><Skeleton variant="rounded" width={60} height={24} /></TableCell>
                    <TableCell><Skeleton variant="text" width={80} /></TableCell>
                    <TableCell align="right"><Skeleton variant="rounded" width={80} height={28} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        ) : filtered.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Typography color="text.secondary">
              {search ? t('list.noResults') : t('list.emptyState')}
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
                      {t('list.colName')}
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'version'}
                      direction={sortField === 'version' ? sortDir : 'asc'}
                      onClick={() => handleSort('version')}
                    >
                      {t('list.colVersion')}
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'status'}
                      direction={sortField === 'status' ? sortDir : 'asc'}
                      onClick={() => handleSort('status')}
                    >
                      {t('list.colStatus')}
                    </TableSortLabel>
                  </TableCell>
                  <TableCell>
                    <TableSortLabel
                      active={sortField === 'updatedAt'}
                      direction={sortField === 'updatedAt' ? sortDir : 'asc'}
                      onClick={() => handleSort('updatedAt')}
                    >
                      {t('list.colUpdated')}
                    </TableSortLabel>
                  </TableCell>
                  <TableCell align="right">{t('list.colActions')}</TableCell>
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
                        <Tooltip title={t('list.edit')}>
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
                        <Tooltip title={t('list.duplicate')}>
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
                        <Tooltip title={tc('actions.delete')}>
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
