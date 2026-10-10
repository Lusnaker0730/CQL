import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import GradientButton from '../common/GradientButton'
import { extractApiError } from '../../utils/errorUtils'
import type { MeasureDefinition, TestCase, TestCaseCopyResult } from '../../types'

interface Props {
  open: boolean
  onClose: () => void
  measure: MeasureDefinition
  testCases: TestCase[]
}

/**
 * PAT-246 — copy test cases to another measure (typically another version of this one). The
 * target list puts the same-named measures first; an expectation that does not fit the target's
 * groups is dropped on that copy, and the result says which.
 */
export default function TestCaseCopyDialog({ open, onClose, measure, testCases }: Props) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const [targetId, setTargetId] = useState<number | ''>('')
  const [selected, setSelected] = useState<Set<number>>(() => new Set(testCases.map((tc) => tc.id!).filter(Boolean)))
  const [result, setResult] = useState<TestCaseCopyResult | null>(null)

  const { data: measures = [], isLoading } = useQuery({
    queryKey: ['measures', 'copy-targets'],
    queryFn: () => measureApi.getMeasures(),
    enabled: open,
  })

  const targets = useMemo(() => {
    const others = measures.filter((m) => m.id != null && m.id !== measure.id)
    return others.sort((a, b) => {
      const sameA = a.name === measure.name ? 0 : 1
      const sameB = b.name === measure.name ? 0 : 1
      if (sameA !== sameB) return sameA - sameB
      return `${a.name} ${a.version}`.localeCompare(`${b.name} ${b.version}`)
    })
  }, [measures, measure.id, measure.name])

  const copyMutation = useMutation({
    mutationFn: () => measureApi.copyTestCasesTo(measure.id!, targetId as number, [...selected]),
    onSuccess: (data) => {
      setResult(data)
      queryClient.invalidateQueries({ queryKey: ['test-cases', targetId] })
    },
  })

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleClose = () => {
    setResult(null)
    copyMutation.reset()
    onClose()
  }

  const target = targets.find((m) => m.id === targetId)

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('testCases.copyDialog.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {result ? (
            <>
              <Alert severity="success" data-testid="copy-result">
                {t('testCases.copyDialog.copied', { count: result.copied.length, target: `${target?.name ?? ''} ${target?.version ?? ''}`.trim() })}
              </Alert>
              {result.warnings.length > 0 && (
                <Alert severity="warning">
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{t('testCases.copyDialog.warningsTitle')}</Typography>
                  {result.warnings.map((w, i) => (
                    <Typography key={i} variant="caption" sx={{ display: 'block' }}>{w}</Typography>
                  ))}
                </Alert>
              )}
            </>
          ) : (
            <>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('testCases.copyDialog.hint')}</Typography>
              <TextField
                select
                size="small"
                fullWidth
                label={t('testCases.copyDialog.target')}
                value={targetId}
                onChange={(e) => setTargetId(e.target.value === '' ? '' : Number(e.target.value))}
                disabled={isLoading}
                slotProps={{ htmlInput: { 'data-testid': 'copy-target-select' } }}
              >
                {targets.map((m) => (
                  <MenuItem key={m.id} value={m.id!}>
                    {m.name} v{m.version}
                    {m.name === measure.name ? ` · ${t('testCases.copyDialog.sameMeasure')}` : ''}
                    {m.status ? ` (${m.status})` : ''}
                  </MenuItem>
                ))}
              </TextField>
              {!isLoading && targets.length === 0 && (
                <Alert severity="info">{t('testCases.copyDialog.noTargets')}</Alert>
              )}
              <FormControlLabel
                control={
                  <Checkbox
                    size="small"
                    checked={selected.size === testCases.length && testCases.length > 0}
                    indeterminate={selected.size > 0 && selected.size < testCases.length}
                    onChange={(e) => setSelected(e.target.checked ? new Set(testCases.map((tc) => tc.id!)) : new Set())}
                  />
                }
                label={t('testCases.copyDialog.selectAll', { selected: selected.size, total: testCases.length })}
              />
              <List dense sx={{ maxHeight: 280, overflow: 'auto', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                {testCases.map((tc) => (
                  <ListItem key={tc.id} disablePadding>
                    <ListItemButton onClick={() => toggle(tc.id!)} dense>
                      <ListItemIcon sx={{ minWidth: 32 }}>
                        <Checkbox edge="start" size="small" checked={selected.has(tc.id!)} tabIndex={-1} disableRipple
                          slotProps={{ input: { 'aria-label': tc.title } }} />
                      </ListItemIcon>
                      <ListItemText primary={tc.title} secondary={tc.series} />
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
              {copyMutation.isError && <Alert severity="error">{extractApiError(copyMutation.error)}</Alert>}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{result ? t('testCases.copyDialog.done') : t('testCases.copyDialog.cancel')}</Button>
        {!result && (
          <GradientButton
            onClick={() => copyMutation.mutate()}
            disabled={targetId === '' || selected.size === 0 || copyMutation.isPending}
          >
            {copyMutation.isPending ? t('testCases.copyDialog.copying') : t('testCases.copyDialog.copy', { count: selected.size })}
          </GradientButton>
        )}
      </DialogActions>
    </Dialog>
  )
}
