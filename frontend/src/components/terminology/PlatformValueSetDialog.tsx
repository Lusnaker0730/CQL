import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { usePlatformValueSet, usePlatformValueSetMutations } from '../../hooks/usePlatformValueSets'
import { extractApiError, extractApiErrorDetails } from '../../utils/errorUtils'
import { mergeConcepts, parsePastedCodes } from '../../utils/valueSetCodes'
import { EDITOR_HEIGHT_SMALL } from '../../constants/layout'
import type { ValueSetConcept } from '../../types'

interface PlatformValueSetDialogProps {
  open: boolean
  /** null = create a new value set. */
  valueSetId: number | null
  onClose: () => void
}

/**
 * PAT-230 — create a value set, edit a draft, or look at a frozen (active / retired) version.
 * URL and version identify the value set in CQL, so they can only be set on create; changing the
 * codes of an active version is done through "new version" on the list, not here.
 */
export default function PlatformValueSetDialog({ open, valueSetId, onClose }: PlatformValueSetDialogProps) {
  const { t } = useTranslation('terminology')
  const isNew = valueSetId == null
  const { data: loaded, isLoading } = usePlatformValueSet(open ? valueSetId : null)
  const { create, update } = usePlatformValueSetMutations()

  const [name, setName] = useState('')
  const [title, setTitle] = useState('')
  const [url, setUrl] = useState('')
  const [version, setVersion] = useState('1.0.0')
  const [description, setDescription] = useState('')
  const [publisher, setPublisher] = useState('')
  const [concepts, setConcepts] = useState<ValueSetConcept[]>([])
  const [defaultSystem, setDefaultSystem] = useState('')
  const [pasted, setPasted] = useState('')
  const [skippedLines, setSkippedLines] = useState<number[]>([])
  const [error, setError] = useState<{ message: string; details?: string[] } | null>(null)

  useEffect(() => {
    if (!open) return
    setError(null)
    setPasted('')
    setSkippedLines([])
    if (isNew) {
      setName(''); setTitle(''); setUrl(''); setVersion('1.0.0'); setDescription(''); setPublisher('')
      setConcepts([]); setDefaultSystem('')
    } else if (loaded) {
      setName(loaded.name); setTitle(loaded.title ?? ''); setUrl(loaded.url); setVersion(loaded.version)
      setDescription(loaded.description ?? ''); setPublisher(loaded.publisher ?? '')
      setConcepts(loaded.concepts ?? [])
      setDefaultSystem(loaded.concepts?.[0]?.system ?? '')
    }
  }, [open, isNew, loaded])

  const readOnly = !isNew && loaded != null && loaded.status !== 'draft'
  const saving = create.isPending || update.isPending
  const systems = useMemo(() => new Set(concepts.map((c) => c.system)).size, [concepts])

  const handleAddPasted = () => {
    const parsed = parsePastedCodes(pasted, defaultSystem)
    setConcepts((current) => mergeConcepts(current, parsed.concepts))
    setSkippedLines(parsed.skippedLines)
    if (parsed.skippedLines.length === 0) setPasted('')
  }

  const handleSave = () => {
    setError(null)
    const body = {
      name: name.trim(),
      title: title.trim() || undefined,
      description: description.trim() || undefined,
      publisher: publisher.trim() || undefined,
      concepts,
      ...(isNew ? { url: url.trim() || undefined, version: version.trim() || undefined } : {}),
    }
    const onError = (err: unknown) => setError({ message: extractApiError(err), details: extractApiErrorDetails(err) })
    if (isNew) {
      create.mutate(body, { onSuccess: onClose, onError })
    } else {
      update.mutate({ id: valueSetId, body }, { onSuccess: onClose, onError })
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isNew ? t('platform.dialog.createTitle') : readOnly ? t('platform.dialog.viewTitle') : t('platform.dialog.editTitle')}
      </DialogTitle>
      <DialogContent dividers>
        {!isNew && isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} aria-label={t('platform.loading')} />
          </Box>
        ) : (
          <Stack spacing={2}>
            {readOnly && <Alert severity="info">{t('platform.dialog.frozen', { status: t(`platform.status.${loaded?.status}`) })}</Alert>}
            {error && (
              <Alert severity="error">
                {error.message}
                {error.details && error.details.length > 0 && (
                  <Box component="ul" sx={{ m: 0, pl: 2 }}>
                    {error.details.map((d) => <li key={d}>{d}</li>)}
                  </Box>
                )}
              </Alert>
            )}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label={t('platform.fields.name')} value={name} onChange={(e) => setName(e.target.value)}
                size="small" fullWidth required disabled={readOnly} helperText={t('platform.fields.nameHelp')} />
              <TextField label={t('platform.fields.title')} value={title} onChange={(e) => setTitle(e.target.value)}
                size="small" fullWidth disabled={readOnly} />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label={t('platform.fields.url')} value={url} onChange={(e) => setUrl(e.target.value)}
                size="small" fullWidth disabled={!isNew}
                helperText={isNew ? t('platform.fields.urlHelp') : t('platform.fields.urlFixed')} />
              <TextField label={t('platform.fields.version')} value={version} onChange={(e) => setVersion(e.target.value)}
                size="small" sx={{ minWidth: 140 }} disabled={!isNew} />
            </Stack>
            <TextField label={t('platform.fields.description')} value={description} onChange={(e) => setDescription(e.target.value)}
              size="small" fullWidth multiline minRows={2} disabled={readOnly} />
            <TextField label={t('platform.fields.publisher')} value={publisher} onChange={(e) => setPublisher(e.target.value)}
              size="small" fullWidth disabled={readOnly} />

            <Typography variant="subtitle2">
              {t('platform.codes.heading', { count: concepts.length, systems })}
            </Typography>

            {!readOnly && (
              <Stack spacing={1}>
                <TextField label={t('platform.codes.defaultSystem')} value={defaultSystem}
                  onChange={(e) => setDefaultSystem(e.target.value)} size="small" fullWidth
                  helperText={t('platform.codes.defaultSystemHelp')} />
                <TextField label={t('platform.codes.paste')} value={pasted} onChange={(e) => setPasted(e.target.value)}
                  size="small" fullWidth multiline minRows={3} placeholder={t('platform.codes.pastePlaceholder')} />
                {skippedLines.length > 0 && (
                  <Alert severity="warning">{t('platform.codes.skipped', { lines: skippedLines.join(', ') })}</Alert>
                )}
                <Box>
                  <Button variant="outlined" size="small" onClick={handleAddPasted} disabled={!pasted.trim()}>
                    {t('platform.codes.add')}
                  </Button>
                </Box>
              </Stack>
            )}

            {concepts.length === 0 ? (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('platform.codes.empty')}</Typography>
            ) : (
              <TableContainer sx={{ maxHeight: EDITOR_HEIGHT_SMALL }}>
                <Table size="small" stickyHeader aria-label={t('platform.codes.tableLabel')}>
                  <TableHead>
                    <TableRow>
                      <TableCell scope="col">{t('platform.codes.system')}</TableCell>
                      <TableCell scope="col">{t('platform.codes.code')}</TableCell>
                      <TableCell scope="col">{t('platform.codes.display')}</TableCell>
                      {!readOnly && <TableCell scope="col" />}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {concepts.map((c, index) => (
                      <TableRow key={`${c.system}|${c.version ?? ''}|${c.code}`}>
                        <TableCell sx={{ wordBreak: 'break-all', color: 'text.secondary' }}>{c.system}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{c.code}</TableCell>
                        <TableCell>{c.display ?? ''}</TableCell>
                        {!readOnly && (
                          <TableCell align="right">
                            <IconButton size="small" aria-label={t('platform.codes.remove', { code: c.code })}
                              onClick={() => setConcepts((current) => current.filter((_, i) => i !== index))}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{readOnly ? t('actions.close', { ns: 'common' }) : t('actions.cancel', { ns: 'common' })}</Button>
        {!readOnly && (
          <Button variant="contained" onClick={handleSave} disabled={saving || !name.trim()}>
            {t('actions.save', { ns: 'common' })}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
