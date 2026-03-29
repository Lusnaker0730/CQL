import React, { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
import { useCqlLibraries, useDeleteCqlLibrary, useCreateCqlLibraryVersion } from '../../hooks/useCqlLibraries'
import { getStoredUsername } from '../../utils/validation'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import GradientButton from '../common/GradientButton'
import StatusChip from '../common/StatusChip'
import TableSkeleton from '../common/TableSkeleton'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import LibraryShareDialog from '../editor/LibraryShareDialog'
import ImportLibraryDialog from './ImportLibraryDialog'
import type { CqlLibrary } from '../../types'
import {
  Paper,
  Typography,
  Stack,
  TextField,
  Box,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableSortLabel,
  IconButton,
  Tabs,
  Tab,
  Tooltip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Alert,
} from '@mui/material'
import {
  Add as AddIcon,
  Search as SearchIcon,
  Upload as UploadIcon,
  Lock as LockIcon,
  Group as PeopleIcon,
  Public as PublicIcon,
  MoreVert as MoreVertIcon,
  Edit as EditIcon,
  Share as ShareIcon,
  History as VersionIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'

interface CqlLibraryListProps {
  onSelect: (id: string) => void
  onCreate: () => void
}

type SortField = 'name' | 'version' | 'status' | 'ownerUsername' | 'accessLevel' | 'updatedAt'
type SortDirection = 'asc' | 'desc'

const ACCESS_ICONS: Record<string, React.ReactElement> = {
  private: <LockIcon sx={{ fontSize: 14 }} />,
  shared: <PeopleIcon sx={{ fontSize: 14 }} />,
  public: <PublicIcon sx={{ fontSize: 14 }} />,
}

const ACCESS_LABEL_KEYS: Record<string, string> = {
  private: 'list.accessPrivate',
  shared: 'list.accessShared',
  public: 'list.accessPublic',
}

const TABLE_MIN_WIDTH = 700
const TABLE_LAYOUT = { tableLayout: 'fixed' as const }
const COL_W = {
  name: '25%',
  version: '10%',
  status: '12%',
  owner: '14%',
  access: '10%',
  updated: '15%',
  actions: '14%',
}

function comparator(a: CqlLibrary, b: CqlLibrary, field: SortField, dir: SortDirection): number {
  let aVal: string | undefined
  let bVal: string | undefined

  switch (field) {
    case 'name':
      aVal = a.name?.toLowerCase()
      bVal = b.name?.toLowerCase()
      break
    case 'version':
      aVal = a.version
      bVal = b.version
      break
    case 'status':
      aVal = a.status
      bVal = b.status
      break
    case 'ownerUsername':
      aVal = a.ownerUsername?.toLowerCase()
      bVal = b.ownerUsername?.toLowerCase()
      break
    case 'accessLevel':
      aVal = a.accessLevel
      bVal = b.accessLevel
      break
    case 'updatedAt':
      aVal = a.updatedAt
      bVal = b.updatedAt
      break
  }

  const cmp = (aVal || '').localeCompare(bVal || '')
  return dir === 'asc' ? cmp : -cmp
}

export default function CqlLibraryList({ onSelect, onCreate }: CqlLibraryListProps) {
  const { t } = useTranslation('cqlLibraries')
  const { showNotification } = useNotification()

  const [search, setSearch] = useState('')
  const [filterTab, setFilterTab] = useState(0)
  const [sortField, setSortField] = useState<SortField>('updatedAt')
  const [sortDir, setSortDir] = useState<SortDirection>('desc')

  // Delete state
  const [deleteTarget, setDeleteTarget] = useState<CqlLibrary | null>(null)

  // Share state
  const [shareTarget, setShareTarget] = useState<CqlLibrary | null>(null)

  // Import dialog state
  const [importOpen, setImportOpen] = useState(false)

  // Version confirmation state
  const [versionTarget, setVersionTarget] = useState<CqlLibrary | null>(null)

  // Actions menu state
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const [menuLibrary, setMenuLibrary] = useState<CqlLibrary | null>(null)

  const currentUser = useMemo(() => getStoredUsername(), [])
  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_GENERAL_MS)

  const { data: allLibraries = [], isLoading } = useCqlLibraries(debouncedSearch || undefined)

  const deleteMutation = useDeleteCqlLibrary()
  const versionMutation = useCreateCqlLibraryVersion()

  // Tab filtering
  const filteredLibraries = useMemo(
    () =>
      allLibraries.filter((lib) => {
        switch (filterTab) {
          case 1: // My Libraries
            return lib.ownerUsername === currentUser
          case 2: // Shared with Me
            return lib.sharedWith?.includes(currentUser) && lib.ownerUsername !== currentUser
          case 3: // Public
            return lib.accessLevel === 'public'
          default: // All
            return true
        }
      }),
    [allLibraries, filterTab, currentUser],
  )

  // Tab counts — single pass
  const tabCounts = useMemo(() => {
    let my = 0, shared = 0, pub = 0
    for (const l of allLibraries) {
      if (l.ownerUsername === currentUser) my++
      if (l.sharedWith?.includes(currentUser) && l.ownerUsername !== currentUser) shared++
      if (l.accessLevel === 'public') pub++
    }
    return { all: allLibraries.length, my, shared, pub }
  }, [allLibraries, currentUser])

  // Sorted libraries
  const sortedLibraries = useMemo(
    () => [...filteredLibraries].sort((a, b) => comparator(a, b, sortField, sortDir)),
    [filteredLibraries, sortField, sortDir],
  )

  const handleSortClick = useCallback(
    (field: SortField) => {
      if (sortField === field) {
        setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'))
      } else {
        setSortField(field)
        setSortDir('asc')
      }
    },
    [sortField],
  )

  const handleMenuOpen = useCallback((e: React.MouseEvent<HTMLElement>, lib: CqlLibrary) => {
    e.stopPropagation()
    setMenuAnchor(e.currentTarget)
    setMenuLibrary(lib)
  }, [])

  const handleMenuClose = useCallback(() => {
    setMenuAnchor(null)
    setMenuLibrary(null)
  }, [])

  const handleEdit = useCallback(() => {
    if (menuLibrary) {
      onSelect(menuLibrary.id)
    }
    handleMenuClose()
  }, [menuLibrary, onSelect, handleMenuClose])

  const handleShareClick = useCallback(() => {
    if (menuLibrary) {
      setShareTarget(menuLibrary)
    }
    handleMenuClose()
  }, [menuLibrary, handleMenuClose])

  const handleVersionClick = useCallback(() => {
    if (menuLibrary) {
      setVersionTarget(menuLibrary)
    }
    handleMenuClose()
  }, [menuLibrary, handleMenuClose])

  const handleDeleteClick = useCallback(() => {
    if (menuLibrary) {
      setDeleteTarget(menuLibrary)
    }
    handleMenuClose()
  }, [menuLibrary, handleMenuClose])

  const handleDeleteConfirm = useCallback(() => {
    if (deleteTarget) {
      deleteMutation.mutate(deleteTarget.id, {
        onError: (err) =>
          showNotification(t('errors.deleteFailed', { error: extractApiError(err) }), 'error'),
      })
    }
    setDeleteTarget(null)
  }, [deleteTarget, deleteMutation, showNotification, t])

  const handleVersionConfirm = useCallback(() => {
    if (versionTarget) {
      versionMutation.mutate(
        { name: versionTarget.name, type: 'minor' },
        {
          onError: (err) =>
            showNotification(t('errors.versionFailed', { error: extractApiError(err) }), 'error'),
        },
      )
    }
    setVersionTarget(null)
  }, [versionTarget, versionMutation, showNotification, t])

  const formatDate = useCallback(
    (dateStr?: string) => {
      if (!dateStr) return t('list.noDate')
      try {
        return new Date(dateStr).toLocaleDateString(undefined, {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
        })
      } catch {
        return t('list.noDate')
      }
    },
    [t],
  )

  return (
    <Paper sx={{ p: 1.5, height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1} flexWrap="wrap" gap={0.5}>
        <Typography variant="subtitle1" fontWeight={700}>
          {t('list.title')}
        </Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap">
          <Button size="small" startIcon={<UploadIcon />} onClick={() => setImportOpen(true)}>
            {t('list.importCql')}
          </Button>
          <GradientButton size="small" startIcon={<AddIcon />} onClick={onCreate}>
            {t('list.newLibrary')}
          </GradientButton>
        </Stack>
      </Stack>

      <TextField
        size="small"
        fullWidth
        placeholder={t('list.searchPlaceholder')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        InputProps={{ startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} /> }}
        sx={{ mb: 0.5 }}
      />

      <Tabs
        value={filterTab}
        onChange={(_, v) => setFilterTab(v)}
        sx={{
          mb: 0.5,
          minHeight: 28,
          '& .MuiTab-root': { minHeight: 28, py: 0, textTransform: 'none', fontSize: '0.8rem' },
        }}
      >
        <Tab label={`${t('list.tabs.all')} (${tabCounts.all})`} />
        <Tab label={`${t('list.tabs.myLibraries')} (${tabCounts.my})`} />
        <Tab label={`${t('list.tabs.sharedWithMe')} (${tabCounts.shared})`} />
        <Tab label={`${t('list.tabs.public')} (${tabCounts.pub})`} />
      </Tabs>

      {/* Loading skeleton */}
      {isLoading && <TableSkeleton columns={7} />}

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        <Table size="small" sx={{ minWidth: TABLE_MIN_WIDTH, ...TABLE_LAYOUT }}>
          <TableHead>
            <TableRow>
              {([
                { field: 'name' as SortField, label: t('list.colName'), width: COL_W.name },
                { field: 'version' as SortField, label: t('list.colVersion'), width: COL_W.version },
                { field: 'status' as SortField, label: t('list.colStatus'), width: COL_W.status },
                { field: 'ownerUsername' as SortField, label: t('list.colOwner'), width: COL_W.owner },
                { field: 'accessLevel' as SortField, label: t('list.colAccess'), width: COL_W.access },
                { field: 'updatedAt' as SortField, label: t('list.colUpdated'), width: COL_W.updated },
              ] as const).map(({ field, label, width }) => (
                <TableCell key={field} scope="col" sx={{ width }}>
                  <TableSortLabel
                    active={sortField === field}
                    direction={sortField === field ? sortDir : 'asc'}
                    onClick={() => handleSortClick(field)}
                  >
                    {label}
                  </TableSortLabel>
                </TableCell>
              ))}
              <TableCell scope="col" align="right" sx={{ width: COL_W.actions }}>
                {t('list.colActions')}
              </TableCell>
            </TableRow>
          </TableHead>
          {!isLoading && sortedLibraries.length > 0 && (
            <TableBody>
              {sortedLibraries.map((lib) => (
                <LibraryRow
                  key={lib.id}
                  library={lib}
                  onRowClick={onSelect}
                  onMenuOpen={handleMenuOpen}
                  formatDate={formatDate}
                  t={t}
                />
              ))}
            </TableBody>
          )}
        </Table>

        {/* Empty state */}
        {!isLoading && sortedLibraries.length === 0 && (
          <Alert severity="info" sx={{ mt: 2 }}>
            {debouncedSearch ? t('list.noResults') : t('list.emptyState')}
          </Alert>
        )}
      </Box>

      {/* Actions menu */}
      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={handleMenuClose}>
        <MenuItem onClick={handleEdit}>
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('list.editLibrary')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleShareClick}>
          <ListItemIcon>
            <ShareIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('list.shareLibrary')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleVersionClick}>
          <ListItemIcon>
            <VersionIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('list.createVersion')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleDeleteClick}>
          <ListItemIcon>
            <DeleteIcon fontSize="small" color="error" />
          </ListItemIcon>
          <ListItemText sx={{ color: 'error.main' }}>{t('list.deleteLibrary')}</ListItemText>
        </MenuItem>
      </Menu>

      {/* Delete confirmation */}
      <ConfirmDeleteDialog
        open={deleteTarget !== null}
        itemName={deleteTarget?.name || ''}
        message={t('list.deleteConfirmMessage', { name: deleteTarget?.name || '' })}
        title={t('list.deleteConfirm')}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirm}
        isPending={deleteMutation.isPending}
      />

      {/* Share dialog */}
      <LibraryShareDialog
        open={shareTarget !== null}
        onClose={() => setShareTarget(null)}
        library={shareTarget}
      />

      {/* Import dialog */}
      <ImportLibraryDialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={(lib) => {
          showNotification(t('import.importSuccess'), 'success')
          onSelect(lib.id)
        }}
      />

      {/* Version confirmation */}
      <Dialog open={versionTarget !== null} onClose={() => setVersionTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('list.versionConfirmTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('list.versionConfirm', { name: versionTarget?.name || '' })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionTarget(null)}>{t('common:actions.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleVersionConfirm}
            disabled={versionMutation.isPending}
          >
            {versionMutation.isPending ? t('create.creating') : t('list.createVersion')}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}

// ── Memoized row component ──────────────────────────────────────────

interface LibraryRowProps {
  library: CqlLibrary
  onRowClick: (id: string) => void
  onMenuOpen: (e: React.MouseEvent<HTMLElement>, lib: CqlLibrary) => void
  formatDate: (d?: string) => string
  t: (key: string, opts?: Record<string, unknown>) => string
}

const LibraryRow = React.memo(function LibraryRow({
  library: lib,
  onRowClick,
  onMenuOpen,
  formatDate,
  t,
}: LibraryRowProps) {
  const access = lib.accessLevel || 'private'

  return (
    <TableRow hover sx={{ cursor: 'pointer' }} onClick={() => onRowClick(lib.id)}>
      <TableCell sx={{ width: COL_W.name, overflow: 'hidden', textOverflow: 'ellipsis' }}>
        <Typography variant="body2" fontWeight={500} noWrap>
          {lib.name}
        </Typography>
        {lib.description && (
          <Typography variant="caption" color="text.secondary" noWrap display="block">
            {lib.description}
          </Typography>
        )}
      </TableCell>
      <TableCell sx={{ width: COL_W.version, fontSize: '0.85rem' }}>
        {lib.version}
      </TableCell>
      <TableCell sx={{ width: COL_W.status }}>
        <StatusChip status={lib.status || 'draft'} />
      </TableCell>
      <TableCell sx={{ width: COL_W.owner }}>
        <Typography variant="body2" noWrap>
          {lib.ownerUsername || '-'}
        </Typography>
      </TableCell>
      <TableCell sx={{ width: COL_W.access }}>
        <Tooltip title={t(ACCESS_LABEL_KEYS[access] || ACCESS_LABEL_KEYS.private)}>
          <Stack direction="row" spacing={0.5} alignItems="center">
            {ACCESS_ICONS[access] || ACCESS_ICONS.private}
            <Typography variant="caption">
              {t(ACCESS_LABEL_KEYS[access] || ACCESS_LABEL_KEYS.private)}
            </Typography>
          </Stack>
        </Tooltip>
      </TableCell>
      <TableCell sx={{ width: COL_W.updated, fontSize: '0.8rem' }}>
        {formatDate(lib.updatedAt)}
      </TableCell>
      <TableCell align="right" sx={{ width: COL_W.actions }}>
        <IconButton
          size="small"
          onClick={(e) => onMenuOpen(e, lib)}
          aria-label={t('list.colActions')}
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      </TableCell>
    </TableRow>
  )
})
