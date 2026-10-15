import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Typography,
  CircularProgress,
  Alert,
  Tabs,
  Tab,
  Chip,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@mui/material'
import {
  ChangeCircle as ChangeIcon,
  Download as DownloadIcon,
} from '@mui/icons-material'
import ReactDiffViewer, { DiffMethod } from 'react-diff-viewer-continued'
import GradientButton from '../common/GradientButton'
import { usePreferences } from '../../hooks/usePreferences'
import type { MeasureVersionDiff, VersionDiffFieldChange, VersionDiffGroupChange, VersionDiffItemChange } from '../../types'

interface VersionOption {
  id: string | number
  version: string
}

/**
 * What a comparison returns. The CQL library compare (EditorPage) only has the four text fields and
 * the sentence lists; the measure compare (PAT-263) also brings the structured `metadata` / `groups`
 * / `summary`, which the dialog renders as tables when present.
 */
export type DiffResult = Partial<MeasureVersionDiff> & {
  oldCql: string
  newCql: string
  oldVersion: string
  newVersion: string
  metadataChanges?: string[]
  populationChanges?: string[]
}

interface VersionDiffDialogProps {
  open: boolean
  onClose: () => void
  versions: VersionOption[]
  onCompare: (
    oldId: string | number,
    newId: string | number,
  ) => Promise<DiffResult>
  /** PAT-263: download the comparison as a human-readable HTML report (measures only). */
  onExportHtml?: (oldId: string | number, newId: string | number) => Promise<void>
}

const KIND_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  added: 'success',
  removed: 'error',
  changed: 'warning',
}

export default function VersionDiffDialog({
  open,
  onClose,
  versions,
  onCompare,
  onExportHtml,
}: VersionDiffDialogProps) {
  const { t } = useTranslation('editor')
  const [oldVersionId, setOldVersionId] = useState<string | number>('')
  const [newVersionId, setNewVersionId] = useState<string | number>('')
  const [diffResult, setDiffResult] = useState<DiffResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [diffTab, setDiffTab] = useState(0)
  const { preferences } = usePreferences()

  const handleCompare = async () => {
    if (!oldVersionId || !newVersionId) return
    setLoading(true)
    setError(null)
    setDiffResult(null)
    try {
      const result = await onCompare(oldVersionId, newVersionId)
      setDiffResult(result)
      setDiffTab(0)
    } catch (err) {
      setError(
        err instanceof Error ? err.message : t('diff.loadFailed'),
      )
    } finally {
      setLoading(false)
    }
  }

  const handleExport = async () => {
    if (!onExportHtml || !oldVersionId || !newVersionId) return
    setExporting(true)
    setError(null)
    try {
      await onExportHtml(oldVersionId, newVersionId)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('diff.loadFailed'))
    } finally {
      setExporting(false)
    }
  }

  const handleClose = () => {
    setOldVersionId('')
    setNewVersionId('')
    setDiffResult(null)
    setError(null)
    setDiffTab(0)
    onClose()
  }

  const canCompare =
    oldVersionId !== '' &&
    newVersionId !== '' &&
    oldVersionId !== newVersionId &&
    !loading

  const structured = !!diffResult?.summary
  const metadataCount = structured ? diffResult!.metadata!.length : (diffResult?.metadataChanges?.length || 0)
  const groupChanges: VersionDiffGroupChange[] = structured
    ? (diffResult!.groups ?? []).filter((g) => g.kind !== 'unchanged')
    : []
  const populationCount = structured
    ? groupChanges.reduce((n, g) => n + g.fields.length + g.populations.length + g.stratifiers.length + g.observations.length
        + (g.kind === 'added' || g.kind === 'removed' ? 1 : 0), 0)
    : (diffResult?.populationChanges?.length || 0)
  const totalChanges = structured ? diffResult!.summary!.total : metadataCount + populationCount

  const kindChip = (kind: string) => (
    <Chip size="small" color={KIND_COLOR[kind] ?? 'default'} label={t(`diff.kind.${kind}`, { defaultValue: kind })} sx={{ height: 20, fontSize: '0.7rem' }} />
  )

  const renderFieldTable = (changes: VersionDiffFieldChange[], withSection: boolean) => (
    <Table size="small" sx={{ '& td, & th': { verticalAlign: 'top' } }}>
      <TableHead>
        <TableRow>
          {withSection && <TableCell>{t('diff.section')}</TableCell>}
          <TableCell>{t('diff.field')}</TableCell>
          <TableCell>{t('diff.change')}</TableCell>
          <TableCell>v{diffResult?.oldVersion}</TableCell>
          <TableCell>v{diffResult?.newVersion}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {changes.map((c, i) => (
          <TableRow key={`${c.section}-${c.field}-${i}`} data-testid={`diff-field-${c.field}`}>
            {withSection && <TableCell>{t(`diff.sections.${c.section}`, { defaultValue: c.section })}</TableCell>}
            <TableCell>{c.field}</TableCell>
            <TableCell>{kindChip(c.kind)}</TableCell>
            <TableCell sx={{ color: 'error.dark', whiteSpace: 'pre-wrap' }}>{c.oldValue ?? '—'}</TableCell>
            <TableCell sx={{ color: 'success.dark', whiteSpace: 'pre-wrap' }}>{c.newValue ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  const renderItemTable = (label: string, items: VersionDiffItemChange[]) => (
    items.length === 0 ? null : (
      <Box sx={{ mt: 1 }} key={label}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Table size="small" sx={{ '& td, & th': { verticalAlign: 'top' } }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('diff.item')}</TableCell>
              <TableCell>{t('diff.change')}</TableCell>
              <TableCell>v{diffResult?.oldVersion}</TableCell>
              <TableCell>v{diffResult?.newVersion}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {items.map((c) => (
              <TableRow key={c.key} data-testid={`diff-item-${c.key}`}>
                <TableCell>{c.key}</TableCell>
                <TableCell>{kindChip(c.kind)}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'error.dark', whiteSpace: 'pre-wrap' }}>{c.oldValue ?? '—'}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'success.dark', whiteSpace: 'pre-wrap' }}>{c.newValue ?? '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
    )
  )

  const renderSentences = (sentences: string[] | undefined, emptyKey: string, color: 'info' | 'warning') => (
    !sentences || sentences.length === 0 ? (
      <Typography variant="body2" sx={{ color: 'text.secondary', py: 4, textAlign: 'center' }}>{t(emptyKey)}</Typography>
    ) : (
      <List dense>
        {sentences.map((change, i) => (
          <ListItem key={i}>
            <ListItemIcon sx={{ minWidth: 32 }}>
              <ChangeIcon fontSize="small" color={color} />
            </ListItemIcon>
            <ListItemText primary={change} slotProps={{ primary: { variant: 'body2' } }} />
          </ListItem>
        ))}
      </List>
    )
  )

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xl" fullWidth>
      <DialogTitle>{t('diff.title')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="diff-old-version">{t('diff.oldVersion')}</InputLabel>
            <Select
              labelId="diff-old-version"
              value={oldVersionId}
              label={t('diff.oldVersion')}
              onChange={(e) => {
                setOldVersionId(e.target.value)
                setDiffResult(null)
              }}
            >
              {versions.map((v) => (
                <MenuItem key={v.id} value={v.id}>
                  {v.version}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('diff.vs')}
          </Typography>

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="diff-new-version">{t('diff.newVersion')}</InputLabel>
            <Select
              labelId="diff-new-version"
              value={newVersionId}
              label={t('diff.newVersion')}
              onChange={(e) => {
                setNewVersionId(e.target.value)
                setDiffResult(null)
              }}
            >
              {versions.map((v) => (
                <MenuItem key={v.id} value={v.id}>
                  {v.version}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <GradientButton
            onClick={handleCompare}
            disabled={!canCompare}
            startIcon={
              loading ? (
                <CircularProgress size={16} color="inherit" />
              ) : undefined
            }
          >
            {loading ? t('diff.comparing') : t('diff.compare')}
          </GradientButton>

          {diffResult && (
            <Chip
              label={totalChanges > 0 ? t('diff.changes', { count: totalChanges }) : t('diff.identical')}
              size="small"
              color={totalChanges > 0 ? 'info' : 'success'}
            />
          )}
          {diffResult && structured && diffResult.cqlChanged && (
            <Chip
              size="small"
              variant="outlined"
              label={t('diff.cqlLines', { added: diffResult.summary!.cqlAdded, removed: diffResult.summary!.cqlRemoved })}
            />
          )}
        </Box>

        {oldVersionId !== '' &&
          newVersionId !== '' &&
          oldVersionId === newVersionId && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {t('diff.selectVersions')}
            </Alert>
          )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {diffResult && (
          <>
            <Tabs
              value={diffTab}
              onChange={(_, v) => setDiffTab(v)}
              sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none' } }}
            >
              <Tab label={t('diff.cqlDiff')} />
              <Tab
                label={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    {t('diff.metadataDiff')}
                    {metadataCount > 0 && (
                      <Chip label={metadataCount} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
                    )}
                  </Box>
                }
              />
              <Tab
                label={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    {t('diff.populationDiff')}
                    {populationCount > 0 && (
                      <Chip label={populationCount} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
                    )}
                  </Box>
                }
              />
            </Tabs>

            {diffTab === 0 && (
              <Box
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  overflow: 'auto',
                  maxHeight: '55vh',
                  '& pre': { fontSize: '13px !important' },
                }}
              >
                <ReactDiffViewer
                  oldValue={diffResult.oldCql}
                  newValue={diffResult.newCql}
                  splitView
                  leftTitle={`v${diffResult.oldVersion}`}
                  rightTitle={`v${diffResult.newVersion}`}
                  compareMethod={DiffMethod.WORDS}
                  useDarkTheme={preferences.themeMode === 'dark'}
                />
              </Box>
            )}

            {diffTab === 1 && (
              <Box sx={{ maxHeight: '55vh', overflow: 'auto' }}>
                {structured
                  ? (diffResult.metadata!.length === 0
                    ? <Typography variant="body2" sx={{ color: 'text.secondary', py: 4, textAlign: 'center' }}>{t('diff.noMetadataChanges')}</Typography>
                    : renderFieldTable(diffResult.metadata!, true))
                  : renderSentences(diffResult.metadataChanges, 'diff.noMetadataChanges', 'info')}
              </Box>
            )}

            {diffTab === 2 && (
              <Box sx={{ maxHeight: '55vh', overflow: 'auto' }}>
                {structured ? (
                  groupChanges.length === 0 ? (
                    <Typography variant="body2" sx={{ color: 'text.secondary', py: 4, textAlign: 'center' }}>{t('diff.noPopulationChanges')}</Typography>
                  ) : (
                    <Stack spacing={2}>
                      {groupChanges.map((g) => (
                        <Box key={g.groupId} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }} data-testid={`diff-group-${g.groupId}`}>
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                            <Typography variant="subtitle2">{t('diff.group', { id: g.groupId })}</Typography>
                            {kindChip(g.kind)}
                          </Stack>
                          {g.fields.length > 0 && renderFieldTable(g.fields, false)}
                          {renderItemTable(t('diff.populations'), g.populations)}
                          {renderItemTable(t('diff.stratifiers'), g.stratifiers)}
                          {renderItemTable(t('diff.observations'), g.observations)}
                        </Box>
                      ))}
                    </Stack>
                  )
                ) : renderSentences(diffResult.populationChanges, 'diff.noPopulationChanges', 'warning')}
              </Box>
            )}
          </>
        )}

        {!diffResult && !loading && !error && (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              py: 8,
              color: 'text.secondary',
            }}
          >
            <Typography variant="body2">
              {t('diff.selectAndCompare')}
            </Typography>
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {onExportHtml && (
          <Button
            onClick={handleExport}
            size="small"
            startIcon={exporting ? <CircularProgress size={14} color="inherit" /> : <DownloadIcon />}
            disabled={!diffResult || exporting}
          >
            {t('diff.downloadHtml')}
          </Button>
        )}
        <Button onClick={handleClose} size="small">
          {t('common:actions.close')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
