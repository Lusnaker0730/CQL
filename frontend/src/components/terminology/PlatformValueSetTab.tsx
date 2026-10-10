import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import UploadIcon from '@mui/icons-material/UploadFile'
import MoreIcon from '@mui/icons-material/MoreVert'
import PlatformValueSetDialog from './PlatformValueSetDialog'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import { usePlatformValueSets, usePlatformValueSetMutations } from '../../hooks/usePlatformValueSets'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import { valueSetApi } from '../../api'
import { extractApiError, extractApiErrorDetails } from '../../utils/errorUtils'
import { cqlDeclaration, suggestNextVersion } from '../../utils/valueSetCodes'
import { downloadBlob } from '../../utils/download'
import type { PlatformValueSet } from '../../types'

const STATUS_COLOR = { draft: 'default', active: 'success', retired: 'warning' } as const

/**
 * PAT-230 — the value sets this installation owns: one row per version. A draft can be edited and
 * deleted; activating freezes the codes; changing an active value set means a new version.
 */
export default function PlatformValueSetTab() {
  const { t } = useTranslation('terminology')
  const [search, setSearch] = useState('')
  const { data: valueSets = [], isLoading, error: loadError } = usePlatformValueSets(search.trim() || undefined)
  const { activate, retire, remove, createVersion, importFhir } = usePlatformValueSetMutations()
  const copy = useCopyToClipboard()

  const [dialog, setDialog] = useState<{ open: boolean; id: number | null }>({ open: false, id: null })
  const [menu, setMenu] = useState<{ anchor: HTMLElement; valueSet: PlatformValueSet } | null>(null)
  const [versionOf, setVersionOf] = useState<PlatformValueSet | null>(null)
  const [newVersion, setNewVersion] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<PlatformValueSet | null>(null)
  const [message, setMessage] = useState<{ severity: 'success' | 'error'; text: string; details?: string[] } | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const fail = (err: unknown) =>
    setMessage({ severity: 'error', text: extractApiError(err), details: extractApiErrorDetails(err) })
  const closeMenu = () => setMenu(null)

  const handleImportFile = async (file: File | undefined) => {
    if (!file) return
    try {
      const resource: unknown = JSON.parse(await file.text())
      importFhir.mutate(resource, {
        onSuccess: (vs) => setMessage({ severity: 'success', text: t('platform.imported', { name: vs.title || vs.name, count: vs.conceptCount }) }),
        onError: fail,
      })
    } catch {
      setMessage({ severity: 'error', text: t('platform.notJson') })
    } finally {
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  const handleExport = async (vs: PlatformValueSet) => {
    closeMenu()
    try {
      const resource = await valueSetApi.exportFhir(vs.id)
      downloadBlob(new Blob([JSON.stringify(resource, null, 2)], { type: 'application/fhir+json' }),
        `${vs.name}-${vs.version}.ValueSet.json`)
    } catch (err) {
      fail(err)
    }
  }

  const handleCopyCql = (vs: PlatformValueSet, pin: boolean) => {
    closeMenu()
    void copy(cqlDeclaration(vs, pin)) // the hook shows its own "copied" notification
  }

  return (
    <Stack spacing={2}>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('platform.intro')}</Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
        <TextField label={t('platform.search')} value={search} onChange={(e) => setSearch(e.target.value)}
          size="small" sx={{ flexGrow: 1 }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, id: null })}>
          {t('platform.create')}
        </Button>
        <Button variant="outlined" startIcon={<UploadIcon />} onClick={() => fileInput.current?.click()}
          disabled={importFhir.isPending}>
          {t('platform.importFhir')}
        </Button>
        <input ref={fileInput} type="file" accept=".json,application/json,application/fhir+json" hidden
          aria-label={t('platform.importFhir')} onChange={(e) => void handleImportFile(e.target.files?.[0])} />
      </Stack>

      {message && (
        <Alert severity={message.severity} onClose={() => setMessage(null)}>
          {message.text}
          {message.details && message.details.length > 0 && (
            <Box component="ul" sx={{ m: 0, pl: 2 }}>
              {message.details.map((d) => <li key={d}>{d}</li>)}
            </Box>
          )}
        </Alert>
      )}
      {loadError != null && <Alert severity="error">{extractApiError(loadError)}</Alert>}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={28} aria-label={t('platform.loading')} />
        </Box>
      ) : valueSets.length === 0 ? (
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {search.trim() ? t('platform.noMatch') : t('platform.empty')}
        </Typography>
      ) : (
        <Table size="small" aria-label={t('platform.tableLabel')}>
          <TableHead>
            <TableRow>
              <TableCell scope="col">{t('platform.columns.name')}</TableCell>
              <TableCell scope="col">{t('platform.columns.version')}</TableCell>
              <TableCell scope="col">{t('platform.columns.status')}</TableCell>
              <TableCell scope="col" align="right">{t('platform.columns.codes')}</TableCell>
              <TableCell scope="col" />
            </TableRow>
          </TableHead>
          <TableBody>
            {valueSets.map((vs) => (
              <TableRow key={vs.id} hover>
                <TableCell>
                  <Button variant="text" size="small" sx={{ textTransform: 'none', p: 0, minWidth: 0, textAlign: 'left' }}
                    onClick={() => setDialog({ open: true, id: vs.id })}>
                    {vs.title || vs.name}
                  </Button>
                  <Typography variant="caption" component="div" sx={{ color: 'text.secondary', wordBreak: 'break-all' }}>
                    {vs.url}
                  </Typography>
                </TableCell>
                <TableCell>{vs.version}</TableCell>
                <TableCell>
                  <Chip size="small" color={STATUS_COLOR[vs.status]} label={t(`platform.status.${vs.status}`)} />
                  {vs.origin === 'imported' && (
                    <Chip size="small" variant="outlined" label={t('platform.originImported')} sx={{ ml: 0.5 }} />
                  )}
                </TableCell>
                <TableCell align="right">{vs.conceptCount}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" aria-label={t('platform.actionsFor', { name: vs.title || vs.name, version: vs.version })}
                    onClick={(e) => setMenu({ anchor: e.currentTarget, valueSet: vs })}>
                    <MoreIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Menu anchorEl={menu?.anchor} open={menu != null} onClose={closeMenu}>
        {menu?.valueSet.status === 'draft' && (
          <MenuItem onClick={() => { const vs = menu.valueSet; closeMenu(); activate.mutate(vs.id, { onError: fail }) }}>
            {t('platform.actions.activate')}
          </MenuItem>
        )}
        {menu?.valueSet.status === 'active' && (
          <MenuItem onClick={() => { const vs = menu.valueSet; closeMenu(); retire.mutate(vs.id, { onError: fail }) }}>
            {t('platform.actions.retire')}
          </MenuItem>
        )}
        <MenuItem onClick={() => {
          if (!menu) return
          setVersionOf(menu.valueSet)
          setNewVersion(suggestNextVersion(menu.valueSet.version))
          closeMenu()
        }}>
          {t('platform.actions.newVersion')}
        </MenuItem>
        <MenuItem onClick={() => menu && handleCopyCql(menu.valueSet, false)}>{t('platform.actions.copyCql')}</MenuItem>
        <MenuItem onClick={() => menu && handleCopyCql(menu.valueSet, true)}>{t('platform.actions.copyCqlPinned')}</MenuItem>
        <MenuItem onClick={() => menu && void handleExport(menu.valueSet)}>{t('platform.actions.exportFhir')}</MenuItem>
        {menu?.valueSet.status === 'draft' && (
          <MenuItem onClick={() => { setDeleteTarget(menu.valueSet); closeMenu() }}>{t('platform.actions.delete')}</MenuItem>
        )}
      </Menu>

      <PlatformValueSetDialog open={dialog.open} valueSetId={dialog.id} onClose={() => setDialog({ open: false, id: null })} />

      <Dialog open={versionOf != null} onClose={() => setVersionOf(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('platform.newVersion.title')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>
            {t('platform.newVersion.explain', { version: versionOf?.version ?? '' })}
          </Typography>
          <TextField label={t('platform.fields.version')} value={newVersion} onChange={(e) => setNewVersion(e.target.value)}
            size="small" fullWidth autoFocus />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionOf(null)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button variant="contained" disabled={!newVersion.trim() || createVersion.isPending}
            onClick={() => {
              if (!versionOf) return
              createVersion.mutate({ id: versionOf.id, version: newVersion.trim() }, {
                onSuccess: (created) => { setVersionOf(null); setDialog({ open: true, id: created.id }) },
                onError: (err) => { setVersionOf(null); fail(err) },
              })
            }}>
            {t('platform.newVersion.confirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDeleteDialog
        open={deleteTarget != null}
        title={t('platform.delete.title')}
        itemName={deleteTarget?.title || deleteTarget?.name || ''}
        isPending={remove.isPending}
        message={t('platform.delete.message', { name: deleteTarget?.title || deleteTarget?.name || '', version: deleteTarget?.version ?? '' })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (!deleteTarget) return
          remove.mutate(deleteTarget.id, { onError: fail, onSettled: () => setDeleteTarget(null) })
        }}
      />
    </Stack>
  )
}
