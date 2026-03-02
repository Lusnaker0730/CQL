import React, { useState, useMemo, useRef, useEffect } from 'react'
import { downloadBlob } from '../../utils/download'
import {
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  Box,
  Table,
  TableBody,
  TableCell,
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
import DepartmentSelector from '../common/DepartmentSelector'
import GradientButton from '../common/GradientButton'
import StatusChip from '../common/StatusChip'
import TableSkeleton from '../common/TableSkeleton'
import BatchEvaluationDialog from './BatchEvaluationDialog'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
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
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [filterTab, setFilterTab] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editMeasure, setEditMeasure] = useState<MeasureDefinition | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importJson, setImportJson] = useState('')
  const [importType, setImportType] = useState<'measure' | 'bundle'>('measure')
  const importFileRef = useRef<HTMLInputElement>(null)
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

  const [debouncedSearch, setDebouncedSearch] = useState(search)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const { data: allMeasures = [], isLoading } = useQuery({
    queryKey: ['measures', debouncedSearch, departmentFilter],
    queryFn: () => measureApi.getMeasures(debouncedSearch || undefined, departmentFilter || undefined),
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

  const { t: tCommon } = useTranslation('common')

  const createMutation = useMutation({
    mutationFn: (def: MeasureDefinition) => measureApi.createMeasure(def),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setCreateOpen(false)
      setNewMeasure({ name: '', version: '1.0.0', title: '', description: '', status: DEFAULT_MEASURE_STATUS, scoringType: DEFAULT_SCORING_TYPE })
    },
    onError: (err) => showNotification(tCommon('mutationErrors.createFailed', { error: extractApiError(err) }), 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => measureApi.deleteMeasure(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
    onError: (err) => showNotification(tCommon('mutationErrors.deleteFailed', { error: extractApiError(err) }), 'error'),
  })

  const importMutation = useMutation({
    mutationFn: (json: unknown) => measureApi.importFhirMeasure(json),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setImportOpen(false)
      setImportJson('')
    },
    onError: (err) => showNotification(tCommon('mutationErrors.importFailed', { error: extractApiError(err) }), 'error'),
  })

  const importBundleMutation = useMutation({
    mutationFn: (json: unknown) => measureApi.importBundle(json),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setImportOpen(false)
      setImportJson('')
    },
    onError: (err) => showNotification(tCommon('mutationErrors.importFailed', { error: extractApiError(err) }), 'error'),
  })

  const updateMutation = useMutation({
    mutationFn: (def: MeasureDefinition) => measureApi.updateMeasure(def.id!, def),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      setEditOpen(false)
      setEditMeasure(null)
    },
    onError: (err) => showNotification(tCommon('mutationErrors.updateFailed', { error: extractApiError(err) }), 'error'),
  })

  const handleEdit = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      const full = await measureApi.getMeasure(id)
      setEditMeasure(full)
      setEditOpen(true)
    } catch (err) {
      showNotification(t('library.importDialog.loadError', { error: extractApiError(err) }), 'error')
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
      downloadBlob(blob, `measure-${id}.json`)
    } catch (err) {
      showNotification(t('library.importDialog.exportError', { error: extractApiError(err) }), 'error')
    }
  }

  return (
    <Paper sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2} flexWrap="wrap" gap={1}>
        <Typography variant="h6">{t('library.title')}</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
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

      <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
        <TextField
          size="small"
          fullWidth
          placeholder={t('library.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          InputProps={{ startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} /> }}
        />
        <DepartmentSelector
          value={departmentFilter}
          onChange={setDepartmentFilter}
          label={t('library.tableHeaders.department')}
          size="small"
          fullWidth={false}
        />
      </Stack>

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

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        <Table size="small" sx={{ minWidth: TABLE_MIN_WIDTH, ...TABLE_LAYOUT }}>
          <TableHead>
            <TableRow>
              <TableCell scope="col" padding="checkbox" sx={{ width: COL_W.checkbox }}>
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
              <TableCell scope="col" sx={{ width: COL_W.name }}>{t('library.tableHeaders.name')}</TableCell>
              <TableCell scope="col" sx={{ width: COL_W.status }}>{t('library.tableHeaders.status')}</TableCell>
              <TableCell scope="col" sx={{ width: COL_W.scoring }}>{t('library.tableHeaders.scoring')}</TableCell>
              <TableCell scope="col" sx={{ width: COL_W.department }}>{t('library.tableHeaders.department')}</TableCell>
              <TableCell scope="col" align="right" sx={{ width: COL_W.actions }}>{t('library.tableHeaders.actions')}</TableCell>
            </TableRow>
          </TableHead>
          {!isLoading && measures.length > 0 && (
            <TableBody>
              {measures.map((m) => (
                <MeasureRow
                  key={m.id}
                  measure={m}
                  selected={selectedMeasureIds.has(m.id!)}
                  onSelect={onSelectMeasure}
                  onToggleSelect={(checked) => {
                    const next = new Set(selectedMeasureIds)
                    if (checked) next.add(m.id!)
                    else next.delete(m.id!)
                    setSelectedMeasureIds(next)
                  }}
                  onEdit={handleEdit}
                  onExport={handleExport}
                  onDelete={(id) => deleteMutation.mutate(id)}
                  t={t}
                />
              ))}
            </TableBody>
          )}
        </Table>
        {!isLoading && measures.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
            {t('library.emptyState')}
          </Typography>
        )}
      </Box>

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
              <Alert severity="error">{extractApiError(createMutation.error)}</Alert>
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
                  <Alert severity="error">{extractApiError(updateMutation.error)}</Alert>
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
          <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
            <Button
              size="small"
              startIcon={<UploadIcon />}
              variant="outlined"
              onClick={() => importFileRef.current?.click()}
            >
              {t('library.importDialog.uploadFile')}
            </Button>
            <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
              {t('library.importDialog.orPaste')}
            </Typography>
            <input
              ref={importFileRef}
              type="file"
              accept=".json,.xml"
              style={{ display: 'none' }}
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) {
                  file.text().then((text) => setImportJson(text))
                }
                if (importFileRef.current) importFileRef.current.value = ''
              }}
            />
          </Stack>
          <TextField
            label={importType === 'bundle' ? t('library.importDialog.bundleJson') : t('library.importDialog.measureJson')}
            fullWidth
            multiline
            rows={10}
            value={importJson}
            onChange={(e) => setImportJson(e.target.value)}
            placeholder={importType === 'bundle'
              ? t('library.importDialog.bundlePlaceholder')
              : t('library.importDialog.measurePlaceholder')}
          />
          {importMutation.isError && (
            <Alert severity="error" sx={{ mt: 1 }}>{extractApiError(importMutation.error)}</Alert>
          )}
          {importBundleMutation.isError && (
            <Alert severity="error" sx={{ mt: 1 }}>{extractApiError(importBundleMutation.error)}</Alert>
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

const TABLE_MIN_WIDTH = 560
const TABLE_LAYOUT = { tableLayout: 'fixed' as const }
const COL_W = {
  checkbox: '5%',
  name: '40%',
  status: '13%',
  scoring: '16%',
  department: '14%',
  actions: '12%',
}

interface MeasureRowProps {
  measure: MeasureDefinition
  selected: boolean
  onSelect?: (measure: MeasureDefinition) => void
  onToggleSelect: (checked: boolean) => void
  onEdit: (id: number, e: React.MouseEvent) => void
  onExport: (id: number) => void
  onDelete: (id: number) => void
  t: (key: string, opts?: Record<string, unknown>) => string
}

const MeasureRow = React.memo(function MeasureRow({
  measure: m,
  selected,
  onSelect,
  onToggleSelect,
  onEdit,
  onExport,
  onDelete,
  t,
}: MeasureRowProps) {
  return (
    <TableRow hover sx={{ cursor: 'pointer' }} onClick={() => onSelect?.(m)}>
      <TableCell padding="checkbox" sx={{ width: COL_W.checkbox }}>
        <Checkbox
          size="small"
          checked={selected}
          onClick={(e) => e.stopPropagation()}
          onChange={(e) => onToggleSelect(e.target.checked)}
        />
      </TableCell>
      <TableCell sx={{ width: COL_W.name, overflow: 'hidden', textOverflow: 'ellipsis' }}>
        <Stack direction="row" spacing={0.5} alignItems="center" sx={{ minWidth: 0 }}>
          <Tooltip title={m.accessLevel || 'private'}>
            {ACCESS_ICONS[m.accessLevel || 'private'] || ACCESS_ICONS.private}
          </Tooltip>
          <Box sx={{ minWidth: 0, overflow: 'hidden' }}>
            <Stack direction="row" spacing={0.5} alignItems="center">
              <Typography variant="body2" fontWeight={500} noWrap>{m.title || m.name}</Typography>
              {m.lockedBy && (
                <Tooltip title={t('library.lockedBy', { user: m.lockedBy })}>
                  <LockClockIcon sx={{ fontSize: 14, color: 'warning.main' }} />
                </Tooltip>
              )}
            </Stack>
            <Typography variant="caption" color="text.secondary" noWrap display="block">
              {m.name}{m.version ? ` v${m.version}` : ''}{m.ownerUsername ? ` · ${m.ownerUsername}` : ''}
            </Typography>
          </Box>
        </Stack>
      </TableCell>
      <TableCell sx={{ width: COL_W.status }}>
        <StatusChip status={m.status || 'draft'} />
      </TableCell>
      <TableCell sx={{ width: COL_W.scoring, fontSize: '0.8rem' }}>{m.scoringType}</TableCell>
      <TableCell sx={{ width: COL_W.department }}>
        {m.department ? (
          <Chip label={m.department} size="small" variant="outlined" sx={{ fontSize: '0.7rem', height: 20 }} />
        ) : (
          <Typography variant="caption" color="text.secondary">{t('library.noValue')}</Typography>
        )}
      </TableCell>
      <TableCell align="right" sx={{ width: COL_W.actions }}>
        <Stack direction="row" spacing={0} justifyContent="flex-end" flexWrap="nowrap">
          <IconButton size="small" aria-label={t('library.editMeasure')} onClick={(e) => onEdit(m.id!, e)}>
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" aria-label={t('library.exportMeasure')} onClick={(e) => { e.stopPropagation(); onExport(m.id!) }}>
            <DownloadIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" aria-label={t('library.deleteMeasure')} color="error" onClick={(e) => { e.stopPropagation(); onDelete(m.id!) }}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Stack>
      </TableCell>
    </TableRow>
  )
})
