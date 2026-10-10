import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Stack, Typography, TextField, Alert, CircularProgress,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import GradientButton from '../common/GradientButton'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import type { MeasureDefinition, TestCase } from '../../types'

/** Server-side bound (TestCaseService.MAX_SHIFT_YEARS); anything larger is a typo, not a plan. */
export const MAX_SHIFT_YEARS = 100

interface TestCaseShiftDatesDialogProps {
  open: boolean
  onClose: () => void
  measure: MeasureDefinition
  /** The one test case to shift, or `'all'` for every test case of the measure. */
  target: TestCase | 'all' | null
  /** How many test cases "all" covers (for the title). */
  count: number
}

/**
 * PAT-248 — shift the dates of stored test cases by whole years (MADiE's "shift test case dates"),
 * so a suite written for one Measurement Period can be reused for the next. The expectation stays;
 * the last run is forgotten and the bundle is validated again.
 */
export default function TestCaseShiftDatesDialog({ open, onClose, measure, target, count }: TestCaseShiftDatesDialogProps) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const { showNotification } = useNotification()
  const [years, setYears] = useState<number>(1)

  useEffect(() => {
    if (open) setYears(1)
  }, [open])

  const one = target && target !== 'all' ? target : null
  const valid = Number.isInteger(years) && years !== 0 && Math.abs(years) <= MAX_SHIFT_YEARS

  const shiftMutation = useMutation({
    mutationFn: async () => {
      if (one) return measureApi.shiftTestCaseDates(measure.id!, one.id!, years)
      return measureApi.shiftAllTestCaseDates(measure.id!, years)
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      const shifted = one ? 1 : (result as { shifted: number }).shifted
      showNotification(t('testCases.shiftDates.done', { count: shifted, years }), 'success')
      onClose()
    },
    onError: (err) => showNotification(t('testCases.shiftDates.failed', { error: extractApiError(err) }), 'error'),
  })

  const period = measure.measurementPeriodStart && measure.measurementPeriodEnd
    ? t('testCases.shiftDates.period', { start: measure.measurementPeriodStart, end: measure.measurementPeriodEnd })
    : null

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {one ? t('testCases.shiftDates.titleOne', { title: one.title }) : t('testCases.shiftDates.titleAll', { count })}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">{t('testCases.shiftDates.hint')}</Typography>
          {period && <Alert severity="info">{period}</Alert>}
          <TextField
            type="number"
            size="small"
            label={t('testCases.shiftDates.years')}
            value={years}
            onChange={(e) => setYears(parseInt(e.target.value, 10) || 0)}
            slotProps={{ htmlInput: { min: -MAX_SHIFT_YEARS, max: MAX_SHIFT_YEARS, step: 1, 'aria-label': t('testCases.shiftDates.years') } }}
            helperText={t('testCases.shiftDates.yearsHelp', { max: MAX_SHIFT_YEARS })}
            error={!valid}
            sx={{ width: 220 }}
            autoFocus
          />
          <Alert severity="warning">{t('testCases.shiftDates.resetsRuns')}</Alert>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={shiftMutation.isPending}>{t('testCases.shiftDates.cancel')}</Button>
        <GradientButton
          onClick={() => shiftMutation.mutate()}
          disabled={!valid || shiftMutation.isPending}
          startIcon={shiftMutation.isPending ? <CircularProgress size={16} /> : undefined}
        >
          {t('testCases.shiftDates.confirm', { years })}
        </GradientButton>
      </DialogActions>
    </Dialog>
  )
}
