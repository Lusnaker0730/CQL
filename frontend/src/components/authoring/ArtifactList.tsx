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
import { List, type RowComponentProps } from 'react-window'
import StatusChip from '../common/StatusChip'
import GradientButton from '../common/GradientButton'
import type { ArtifactSummary } from '../../types/authoring'
import { ARTIFACT_ROW_HEIGHT, LIST_MAX_HEIGHT } from '../../constants/layout'

type SortField = 'name' | 'version' | 'status' | 'updatedAt'

// Semver-aware comparison. Falls back to localeCompare for non-numeric
// segments (e.g. `1.0.0-rc1`). Empty strings sort last.
function compareSemver(a: string, b: string): number {
  if (!a && !b) return 0
  if (!a) return 1
  if (!b) return -1
  const aSeg = a.split('.')
  const bSeg = b.split('.')
  const len = Math.max(aSeg.length, bSeg.length)
  for (let i = 0; i < len; i++) {
    const aPart = aSeg[i] ?? ''
    const bPart = bSeg[i] ?? ''
    const aNum = Number(aPart)
    const bNum = Number(bPart)
    const aIsNum = !Number.isNaN(aNum) && /^\d+$/.test(aPart)
    const bIsNum = !Number.isNaN(bNum) && /^\d+$/.test(bPart)
    if (aIsNum && bIsNum) {
      if (aNum !== bNum) return aNum - bNum
    } else {
      const cmp = aPart.localeCompare(bPart)
      if (cmp !== 0) return cmp
    }
  }
  return 0
}
type SortDir = 'asc' | 'desc'

/** Fixed column widths so the header table and virtualised body rows stay aligned. */
const COL_WIDTHS = { name: '40%', version: '12%', status: '12%', updated: '20%', actions: '16%' } as const

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
          // Semver-aware compare so 1.10.0 sorts after 1.2.0 instead of before
          return compareSemver(a.version, b.version) * dir
        case 'status':
          return a.status.localeCompare(b.status) * dir
        case 'updatedAt':
          // ISO-8601 timestamps sort lexically; explicit Date compare avoids
          // surprises if the field ever stops being strict ISO.
          return (Date.parse(a.updatedAt || '') - Date.parse(b.updatedAt || '')) * dir
        default:
          return 0
      }
    })

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', p: 2 }}>
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2
          }}>
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
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }
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
            <Typography sx={{
              color: "text.secondary"
            }}>
              {search ? t('list.noResults') : t('list.emptyState')}
            </Typography>
          </Box>
        ) : (
          <>
            <TableContainer>
              <Table size="small" stickyHeader sx={{ tableLayout: 'fixed' }}>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: COL_WIDTHS.name }}>
                      <TableSortLabel
                        active={sortField === 'name'}
                        direction={sortField === 'name' ? sortDir : 'asc'}
                        onClick={() => handleSort('name')}
                      >
                        {t('list.colName')}
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sx={{ width: COL_WIDTHS.version }}>
                      <TableSortLabel
                        active={sortField === 'version'}
                        direction={sortField === 'version' ? sortDir : 'asc'}
                        onClick={() => handleSort('version')}
                      >
                        {t('list.colVersion')}
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sx={{ width: COL_WIDTHS.status }}>
                      <TableSortLabel
                        active={sortField === 'status'}
                        direction={sortField === 'status' ? sortDir : 'asc'}
                        onClick={() => handleSort('status')}
                      >
                        {t('list.colStatus')}
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sx={{ width: COL_WIDTHS.updated }}>
                      <TableSortLabel
                        active={sortField === 'updatedAt'}
                        direction={sortField === 'updatedAt' ? sortDir : 'asc'}
                        onClick={() => handleSort('updatedAt')}
                      >
                        {t('list.colUpdated')}
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sx={{ width: COL_WIDTHS.actions }} align="right">{t('list.colActions')}</TableCell>
                  </TableRow>
                </TableHead>
              </Table>
            </TableContainer>
            <ArtifactVirtualList
              artifacts={filtered}
              onSelect={onSelect}
              onDuplicate={onDuplicate}
              onDelete={onDelete}
              t={t}
              tc={tc}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}

interface ArtifactVirtualListProps {
  artifacts: ArtifactSummary[]
  onSelect: (artifact: ArtifactSummary) => void
  onDuplicate: (id: number) => void
  onDelete: (id: number) => void
  t: (key: string) => string
  tc: (key: string) => string
}

// react-window 2.x: row is a separate component receiving { index, style, ...rowProps }.
// Spread rowProps carry the per-list data without re-rendering on every parent re-render
// (List automatically memoizes them).
type ArtifactRowProps = {
  artifacts: ArtifactSummary[]
  onSelect: (artifact: ArtifactSummary) => void
  onDuplicate: (id: number) => void
  onDelete: (id: number) => void
  t: (key: string) => string
  tc: (key: string) => string
}

function ArtifactRow({
  index,
  style,
  artifacts,
  onSelect,
  onDuplicate,
  onDelete,
  t,
  tc,
}: RowComponentProps<ArtifactRowProps>) {
  const artifact = artifacts[index]
  // Row is a flex container matching the header table column widths.
  // Wrapping each virtualized row in <Table> generated invalid HTML and
  // forced a fresh layout root per row — flex avoids both.
  return (
    <Box
      style={style}
      onClick={() => onSelect(artifact)}
      sx={{
        display: 'flex',
        alignItems: 'center',
        cursor: 'pointer',
        px: 2,
        borderBottom: 1,
        borderColor: 'divider',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Box sx={{ width: COL_WIDTHS.name, overflow: 'hidden', textOverflow: 'ellipsis', pr: 1 }}>
        <Typography variant="body2" noWrap sx={{
          fontWeight: 500
        }}>
          {artifact.name}
        </Typography>
        {artifact.description && (
          <Typography
            variant="caption"
            noWrap
            sx={{
              color: "text.secondary",
              display: 'block'
            }}>
            {artifact.description}
          </Typography>
        )}
      </Box>
      <Box sx={{ width: COL_WIDTHS.version }}>
        <Typography variant="body2">{artifact.version}</Typography>
      </Box>
      <Box sx={{ width: COL_WIDTHS.status }}>
        <StatusChip status={artifact.status} />
      </Box>
      <Box sx={{ width: COL_WIDTHS.updated }}>
        <Typography variant="caption">
          {new Date(artifact.updatedAt).toLocaleDateString()}
        </Typography>
      </Box>
      <Box sx={{ width: COL_WIDTHS.actions }}>
        <Stack direction="row" spacing={0} sx={{
          justifyContent: "flex-end"
        }}>
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
      </Box>
    </Box>
  );
}

function ArtifactVirtualList({
  artifacts,
  onSelect,
  onDuplicate,
  onDelete,
  t,
  tc,
}: ArtifactVirtualListProps) {
  // react-window 2.x: List auto-sizes from its container, so we wrap it in a Box
  // that enforces our explicit height (shrinks for short lists, caps at LIST_MAX_HEIGHT).
  // overscanCount was removed in v2 in favor of a dynamic overscan strategy.
  return (
    <Box sx={{ height: Math.min(artifacts.length * ARTIFACT_ROW_HEIGHT, LIST_MAX_HEIGHT), width: '100%' }}>
      <List
        rowComponent={ArtifactRow}
        rowCount={artifacts.length}
        rowHeight={ARTIFACT_ROW_HEIGHT}
        rowProps={{ artifacts, onSelect, onDuplicate, onDelete, t, tc }}
      />
    </Box>
  )
}
