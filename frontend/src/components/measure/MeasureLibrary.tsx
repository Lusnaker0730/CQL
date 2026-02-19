import { useState, useMemo } from 'react'
import {
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  Alert,
  Tabs,
  Tab,
  Tooltip,
  Chip,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  Upload as UploadIcon,
  Download as DownloadIcon,
  Search as SearchIcon,
  LibraryBooks as LibraryBooksIcon,
  Lock as PrivateIcon,
  LockClock as LockClockIcon,
  Group as SharedIcon,
  Public as PublicIcon,
  PlaylistPlay as BatchIcon,
} from '@mui/icons-material'
import { Checkbox } from '@mui/material'
import { useTranslation } from 'react-i18next'
import LibraryPicker from '../common/LibraryPicker'
import GradientButton from '../common/GradientButton'
import StatusChip from '../common/StatusChip'
import TableSkeleton from '../common/TableSkeleton'
import BatchEvaluationDialog from './BatchEvaluationDialog'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useNotification } from '../../hooks/useNotification'
import type { MeasureDefinition } from '../../types'
import { getStoredUsername } from '../../utils/validation'
import {
  DEFAULT_MEASURE_STATUS,
  DEFAULT_SCORING_TYPE,
  SCORING_TYPE_OPTIONS,
  MEASURE_STATUS_OPTIONS,
} from '../../constants/measureConstants'

const ACCESS_ICONS: Record<string, React.ReactElement> = {
  private: <PrivateIcon sx={{ fontSize: 14 }} />,
  shared: <SharedIcon sx={{ fontSize: 14 }} />,
  public: <PublicIcon sx={{ fontSize: 14 }} />,
}

interface MeasureLibraryProps {
  onSelectMeasure?: (measure: MeasureDefinition) => void
}

export default function MeasureLibrary({ onSelectMeasure }: MeasureLibraryProps) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [filterTab, setFilterTab] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editMeasure, setEditMeasure] = useState<MeasureDefinition | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importJson, setImportJson] = useState('')
  const [importType, setImportType] = useState<'measure' | 'bundle'>('measure')
  const [selectedMeasureIds, setSelectedMeasureIds] = useState<Set<number>>(new Set())
  const [batchDialogOpen, setBatchDialogOpen] = useState(false)
  const [libraryPickerOpen, setLibraryPickerOpen] = useState(false)
  const [libraryPickerTarget, setLibraryPickerTarget] = useState<'create' | 'edit'>('create')
  const [newMeasure, setNewMeasure] = useState<Partial<MeasureDefinition>>({
    name: '',
    version: '1.0.0',
    title: '',
    description: '',
    status: DEFAULT_MEASURE_STATUS,
    scoringType: DEFAULT_SCORING_TYPE,
  })

  const { showNotification } = useNotification()
  const currentUser = getStoredUsername()

  const { data: allMeasures = [], isLoading } = useQuery({
    queryKey: ['measures', search],
    queryFn: () => measureApi.getMeasures(search || undefined),
  })

  const measures = useMemo(
    () =>
      allMeasures.filter((m) => {
        switch (filterTab) {
          case 1: // My Measures
            return m.ownerUsername === currentUser
          case 2: // Shared with me
            return m.sharedWith?.includes(currentUser) && m.ownerUsername !== currentUser
          case 3: // Public
            return m.accessLevel === 'public'
          default: // All
            return true
        }
      }),
    [allMeasures, filterTab, currentUser]
  )

  const createMutation = useMutation({
    mutationFn: (def: MeasureDefinition) => measureApi.createMeasure(def),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setCreateOpen(false)
      setNewMeasure({ name: '', version: '1.0.0', title: '', description: '', status: DEFAULT_MEASURE_STATUS, scoringType: DEFAULT_SCORING_TYPE })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => measureApi.deleteMeasure(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })

  const importMutation = useMutation({
    mutationFn: (json: unknown) => measureApi.importFhirMeasure(json),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setImportOpen(false)
      setImportJson('')
    },
  })

  const importBundleMutation = useMutation({
    mutationFn: (json: unknown) => measureApi.importBundle(json),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setImportOpen(false)
      setImportJson('')
    },
  })

  const updateMutation = useMutation({
    mutationFn: (def: MeasureDefinition) => measureApi.updateMeasure(def.id!, def),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setEditOpen(false)
      setEditMeasure(null)
    },
  })

  const handleEdit = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      const full = await measureApi.getMeasure(id)
      setEditMeasure(full)
      setEditOpen(true)
    } catch (err) {
      showNotification(t('library.importDialog.loadError', { error: (err as Error).message }), 'error')
    }
  }

  const handleUpdate = () => {
    if (editMeasure) updateMutation.mutate(editMeasure)
  }

  const handleCreate = () => {
    createMutation.mutate(newMeasure as MeasureDefinition)
  }

  const handleImport = () => {
    try {
      const parsed = JSON.parse(importJson)
      // Auto-detect Bundle vs single Measure
      if (parsed.resourceType === 'Bundle' || importType === 'bundle') {
        importBundleMutation.mutate(parsed)
      } else {
        importMutation.mutate(parsed)
      }
    } catch {
      showNotification(t('library.importDialog.invalidJson'), 'error')
    }
  }

  const handleExport = async (id: number) => {
    try {
      const data = await measureApi.exportFhirMeasure(id)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `measure-${id}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showNotification(t('library.importDialog.exportError', { error: (err as Error).message }), 'error')
    }
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('library.title')}</Typography>
        <Stack direction="row" spacing={1}>
          {selectedMeasureIds.size > 0 && (
            <Button
              size="small"
              startIcon={<BatchIcon />}
              onClick={() => setBatchDialogOpen(true)}
              color="info"
              variant="outlined"
            >
              {t('library.batchEvaluate', { count: selectedMeasureIds.size })}
            </Button>
          )}
          <Button size="small" startIcon={<UploadIcon />} onClick={() => setImportOpen(true)}>
            {t('library.importFhir')}
          </Button>
          <GradientButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
            {t('library.newMeasure')}
          </GradientButton>
        </Stack>
      </Stack>

      <TextField
        size="small"
        fullWidth
        placeholder={t('library.searchPlaceholder')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        InputProps={{ startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} /> }}
        sx={{ mb: 1 }}
      />

      <Tabs
        value={filterTab}
        onChange={(_, v) => setFilterTab(v)}
        sx={{ mb: 1, minHeight: 32, '& .MuiTab-root': { minHeight: 32, py: 0, textTransform: 'none', fontSize: '0.8rem' } }}
      >
        <Tab label={t('library.tabs.all', { count: allMeasures.length })} />
        <Tab label={t('library.tabs.myMeasures')} />
        <Tab label={t('library.tabs.sharedWithMe')} />
        <Tab label={t('library.tabs.public')} />
      </Tabs>

      {isLoading && <TableSkeleton columns={6} hasCheckbox />}

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col" padding="checkbox" width={40}>
                <Checkbox
                  size="small"
                  checked={measures.length > 0 && selectedMeasureIds.size === measures.length}
                  indeterminate={selectedMeasureIds.size > 0 && selectedMeasureIds.size < measures.length}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setSelectedMeasureIds(new Set(measures.map((m) => m.id!)))
                    } else {
                      setSelectedMeasureIds(new Set())
                    }
                  }}
                />
              </TableCell>
              <TableCell scope="col">{t('library.tableHeaders.name')}</TableCell>
              <TableCell scope="col">{t('library.tableHeaders.version')}</TableCell>
              <TableCell scope="col">{t('library.tableHeaders.status')}</TableCell>
              <TableCell scope="col">{t('library.tableHeaders.scoring')}</TableCell>
              <TableCell scope="col">{t('library.tableHeaders.setting')}</TableCell>
              <TableCell scope="col">{t('library.tableHeaders.owner')}</TableCell>
              <TableCell scope="col" align="right">{t('library.tableHeaders.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {measures.map((m) => (
              <TableRow
                key={m.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => onSelectMeasure?.(m)}
              >
                <TableCell padding="checkbox">
                  <Checkbox
                    size="small"
                    checked={selectedMeasureIds.has(m.id!)}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => {
                      const next = new Set(selectedMeasureIds)
                      if (e.target.checked) {
                        next.add(m.id!)
                      } else {
                        next.delete(m.id!)
                      }
                      setSelectedMeasureIds(next)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Tooltip title={m.accessLevel || 'private'}>
                      {ACCESS_ICONS[m.accessLevel || 'private'] || ACCESS_ICONS.private}
                    </Tooltip>
                    <div>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Typography variant="body2" fontWeight={500}>{m.title || m.name}</Typography>
                        {m.lockedBy && (
                          <Tooltip title={t('library.lockedBy', { user: m.lockedBy })}>
                            <LockClockIcon sx={{ fontSize: 14, color: 'warning.main' }} />
                          </Tooltip>
                        )}
                      </Stack>
                      {m.title && (
                        <Typography variant="caption" color="text.secondary">{m.name}</Typography>
                      )}
                    </div>
                  </Stack>
                </TableCell>
                <TableCell>{m.version}</TableCell>
                <TableCell>
                  <StatusChip status={m.status || 'draft'} />
                </TableCell>
                <TableCell>{m.scoringType}</TableCell>
                <TableCell>
                  {m.setting ? (
                    <Chip label={m.setting} size="small" variant="outlined" sx={{ fontSize: '0.7rem', height: 20, textTransform: 'capitalize' }} />
                  ) : (
                    <Typography variant="caption" color="text.secondary">{t('library.noValue')}</Typography>
                  )}
                </TableCell>
                <TableCell>
                  {m.ownerUsername ? (
                    <Chip label={m.ownerUsername} size="small" variant="outlined" sx={{ fontSize: '0.7rem', height: 20 }} />
                  ) : (
                    <Typography variant="caption" color="text.secondary">{t('library.noValue')}</Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" aria-label={t('library.editMeasure')} onClick={(e) => handleEdit(m.id!, e)}>
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" aria-label={t('library.exportMeasure')} onClick={(e) => { e.stopPropagation(); handleExport(m.id!) }}>
                    <DownloadIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" aria-label={t('library.deleteMeasure')} color="error" onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(m.id!) }}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {!isLoading && measures.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography variant="body2" color="text.secondary">
                    {t('library.emptyState')}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('library.createDialog.title')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label={t('library.createDialog.name')} required size="small" fullWidth
              value={newMeasure.name} onChange={(e) => setNewMeasure({ ...newMeasure, name: e.target.value })} />
            <TextField label={t('library.createDialog.version')} size="small" fullWidth
              value={newMeasure.version} onChange={(e) => setNewMeasure({ ...newMeasure, version: e.target.value })} />
            <TextField label={t('library.createDialog.titleField')} size="small" fullWidth
              value={newMeasure.title} onChange={(e) => setNewMeasure({ ...newMeasure, title: e.target.value })} />
            <TextField label={t('library.createDialog.description')} size="small" fullWidth multiline rows={2}
              value={newMeasure.description} onChange={(e) => setNewMeasure({ ...newMeasure, description: e.target.value })} />
            <TextField label={t('library.createDialog.scoringType')} select size="small" fullWidth
              value={newMeasure.scoringType} onChange={(e) => setNewMeasure({ ...newMeasure, scoringType: e.target.value })}>
              {SCORING_TYPE_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>
            <Stack direction="row" justifyContent="flex-end">
              <Button size="small" startIcon={<LibraryBooksIcon />}
                onClick={() => { setLibraryPickerTarget('create'); setLibraryPickerOpen(true) }}
                sx={{ color: 'primary.main' }}>
                {t('library.createDialog.loadFromLibrary')}
              </Button>
            </Stack>
            <TextField label={t('library.createDialog.cqlContent')} size="small" fullWidth multiline rows={4}
              value={newMeasure.cqlContent || ''} onChange={(e) => setNewMeasure({ ...newMeasure, cqlContent: e.target.value })} />
            {createMutation.isError && (
              <Alert severity="error">{(createMutation.error as Error).message}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button onClick={handleCreate} variant="contained" disabled={!newMeasure.name || createMutation.isPending}>
            {createMutation.isPending ? t('library.createDialog.creating') : t('library.createDialog.create')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={editOpen} onClose={() => setEditOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{t('library.editDialog.title')}</DialogTitle>
        {editMeasure && (
          <>
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                <TextField label={t('library.editDialog.name')} required size="small" fullWidth
                  value={editMeasure.name} onChange={(e) => setEditMeasure({ ...editMeasure, name: e.target.value })} />
                <Stack direction="row" spacing={2}>
                  <TextField label={t('library.editDialog.version')} size="small" fullWidth
                    value={editMeasure.version} onChange={(e) => setEditMeasure({ ...editMeasure, version: e.target.value })} />
                  <TextField label={t('library.editDialog.status')} select size="small" fullWidth
                    value={editMeasure.status} onChange={(e) => setEditMeasure({ ...editMeasure, status: e.target.value })}>
                    {MEASURE_STATUS_OPTIONS.map((opt) => (
                      <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                    ))}
                  </TextField>
                </Stack>
                <TextField label={t('library.editDialog.titleField')} size="small" fullWidth
                  value={editMeasure.title || ''} onChange={(e) => setEditMeasure({ ...editMeasure, title: e.target.value })} />
                <TextField label={t('library.editDialog.description')} size="small" fullWidth multiline rows={2}
                  value={editMeasure.description || ''} onChange={(e) => setEditMeasure({ ...editMeasure, description: e.target.value })} />
                <TextField label={t('library.editDialog.scoringType')} select size="small" fullWidth
                  value={editMeasure.scoringType} onChange={(e) => setEditMeasure({ ...editMeasure, scoringType: e.target.value })}>
                  {SCORING_TYPE_OPTIONS.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
                </TextField>
                <Stack direction="row" justifyContent="flex-end">
                  <Button size="small" startIcon={<LibraryBooksIcon />}
                    onClick={() => { setLibraryPickerTarget('edit'); setLibraryPickerOpen(true) }}
                    sx={{ color: 'primary.main' }}>
                    {t('library.editDialog.loadFromLibrary')}
                  </Button>
                </Stack>
                <TextField label={t('library.editDialog.cqlContent')} size="small" fullWidth multiline rows={12}
                  value={editMeasure.cqlContent || ''} onChange={(e) => setEditMeasure({ ...editMeasure, cqlContent: e.target.value })}
                  InputProps={{ sx: { fontFamily: '"Consolas", "Monaco", monospace', fontSize: '0.85rem' } }} />
                {updateMutation.isError && (
                  <Alert severity="error">{(updateMutation.error as Error).message}</Alert>
                )}
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setEditOpen(false)}>{t('actions.cancel', { ns: 'common' })}</Button>
              <Button onClick={handleUpdate} variant="contained" disabled={!editMeasure.name || updateMutation.isPending}>
                {updateMutation.isPending ? t('library.editDialog.saving') : t('library.editDialog.save')}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>

      {/* Import Dialog */}
      <Dialog open={importOpen} onClose={() => setImportOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{t('library.importDialog.title')}</DialogTitle>
        <DialogContent>
          <TextField
            select
            label={t('library.importDialog.importType')}
            size="small"
            fullWidth
            value={importType}
            onChange={(e) => setImportType(e.target.value as 'measure' | 'bundle')}
            sx={{ mt: 1, mb: 2 }}
          >
            <MenuItem value="measure">{t('library.importDialog.measureSingle')}</MenuItem>
            <MenuItem value="bundle">{t('library.importDialog.measureBundle')}</MenuItem>
          </TextField>
          <TextField
            label={importType === 'bundle' ? t('library.importDialog.bundleJson') : t('library.importDialog.measureJson')}
            fullWidth
            multiline
            rows={12}
            value={importJson}
            onChange={(e) => setImportJson(e.target.value)}
            placeholder={importType === 'bundle'
              ? t('library.importDialog.bundlePlaceholder')
              : t('library.importDialog.measurePlaceholder')}
          />
          {importMutation.isError && (
            <Alert severity="error" sx={{ mt: 1 }}>{(importMutation.error as Error).message}</Alert>
          )}
          {importBundleMutation.isError && (
            <Alert severity="error" sx={{ mt: 1 }}>{(importBundleMutation.error as Error).message}</Alert>
          )}
          {importBundleMutation.isSuccess && (
            <Alert severity="success" sx={{ mt: 1 }}>
              {t('library.importDialog.bundleSuccess', {
                libraries: importBundleMutation.data.librariesImported,
                skipped: importBundleMutation.data.librariesSkipped,
                valueSets: importBundleMutation.data.valueSetsFound,
              })}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportOpen(false)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button onClick={handleImport} variant="contained"
            disabled={!importJson || importMutation.isPending || importBundleMutation.isPending}>
            {(importMutation.isPending || importBundleMutation.isPending) ? t('library.importDialog.importing') : t('library.importDialog.import')}
          </Button>
        </DialogActions>
      </Dialog>

      <LibraryPicker
        open={libraryPickerOpen}
        onClose={() => setLibraryPickerOpen(false)}
        onSelect={(cql) => {
          if (libraryPickerTarget === 'create') {
            setNewMeasure({ ...newMeasure, cqlContent: cql })
          } else if (editMeasure) {
            setEditMeasure({ ...editMeasure, cqlContent: cql })
          }
        }}
      />

      <BatchEvaluationDialog
        open={batchDialogOpen}
        onClose={() => setBatchDialogOpen(false)}
        measureIds={Array.from(selectedMeasureIds)}
      />
    </Paper>
  )
}
