import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getScoreChipColor } from '../../utils/scoreColors'
import { extractApiError } from '../../utils/errorUtils'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Alert,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Typography,
  LinearProgress,
  Box,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { BatchEvaluationRequest, BatchEvaluationResult } from '../../types'
import { getDefaultMeasurePeriod } from '../../utils/dateDefaults'
import GradientButton from '../common/GradientButton'

interface BatchEvaluationDialogProps {
  open: boolean
  onClose: () => void
  measureIds: number[]
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function BatchEvaluationDialog({
  open,
  onClose,
  measureIds,
}: BatchEvaluationDialogProps) {
  const { t } = useTranslation('measures')
  const [fhirServerUrl, setFhirServerUrl] = useState('')
  const { periodStart: defaultStart, periodEnd: defaultEnd } = getDefaultMeasurePeriod()
  const [periodStart, setPeriodStart] = useState(defaultStart)
  const [periodEnd, setPeriodEnd] = useState(defaultEnd)
  const [result, setResult] = useState<BatchEvaluationResult | null>(null)

  const batchMutation = useMutation({
    mutationFn: (request: BatchEvaluationRequest) =>
      measureApi.batchEvaluate(request),
    onSuccess: (data) => setResult(data),
  })

  const handleRun = () => {
    setResult(null)
    const request: BatchEvaluationRequest = {
      measureIds,
      periodStart: periodStart || undefined,
      periodEnd: periodEnd || undefined,
      fhirServerUrl: fhirServerUrl || undefined,
    }
    batchMutation.mutate(request)
  }

  const handleClose = () => {
    if (!batchMutation.isPending) {
      setResult(null)
      batchMutation.reset()
      onClose()
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('batch.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('batch.description', { count: measureIds.length })}
          </Typography>

          <TextField
            label={t('batch.fhirServerUrl')}
            value={fhirServerUrl}
            onChange={(e) => setFhirServerUrl(e.target.value)}
            size="small"
            fullWidth
            placeholder={t('batch.fhirServerPlaceholder')}
            disabled={batchMutation.isPending}
          />

          <Stack direction="row" spacing={2}>
            <TextField
              label={t('batch.periodStart')}
              type="date"
              value={periodStart}
              onChange={(e) => setPeriodStart(e.target.value)}
              size="small"
              fullWidth
              disabled={batchMutation.isPending}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
            <TextField
              label={t('batch.periodEnd')}
              type="date"
              value={periodEnd}
              onChange={(e) => setPeriodEnd(e.target.value)}
              size="small"
              fullWidth
              disabled={batchMutation.isPending}
              slotProps={{
                inputLabel: { shrink: true }
              }}
            />
          </Stack>

          {batchMutation.isPending && (
            <Box>
              <Stack
                direction="row"
                spacing={1}
                sx={{
                  alignItems: "center",
                  mb: 1
                }}>
                <CircularProgress size={20} />
                <Typography variant="body2">
                  {t('batch.evaluating')}
                </Typography>
              </Stack>
              <LinearProgress />
            </Box>
          )}

          {batchMutation.isError && (
            <Alert severity="error">
              {t('batch.evaluationFailed', { error: extractApiError(batchMutation.error) })}
            </Alert>
          )}

          {result && (
            <Box>
              <Alert
                severity={result.errorCount === 0 ? 'success' : 'warning'}
                sx={{ mb: 2 }}
              >
                {t('batch.result', { success: result.successCount, total: result.totalMeasures })}
                {result.errorCount > 0 && `, ${result.errorCount} error${result.errorCount !== 1 ? 's' : ''}`}
                {' '}&mdash; total duration: {formatDuration(result.totalDurationMs)}
              </Alert>

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell scope="col">{t('batch.tableHeaders.measureName')}</TableCell>
                      <TableCell scope="col">{t('batch.tableHeaders.status')}</TableCell>
                      <TableCell scope="col">{t('batch.tableHeaders.score')}</TableCell>
                      <TableCell scope="col">{t('batch.tableHeaders.errorMessage')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {result.results.map((r, idx) => {
                      const score =
                        r.groups?.[0]?.measureScore != null
                          ? r.groups[0].measureScore
                          : null
                      const hasError = r.status === 'error' || !!r.errorMessage

                      return (
                        <TableRow key={idx}>
                          <TableCell>
                            <Typography variant="body2">
                              {r.measureName || r.measureId}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={r.status}
                              size="small"
                              color={hasError ? 'error' : 'success'}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>
                            {score != null ? (
                              <Chip
                                label={`${(score * 100).toFixed(1)}%`}
                                size="small"
                                color={getScoreChipColor(score * 100)}
                              />
                            ) : (
                              <Typography variant="body2" sx={{
                                color: "text.secondary"
                              }}>
                                &mdash;
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>
                            {r.errorMessage ? (
                              <Typography variant="body2" color="error">
                                {r.errorMessage}
                              </Typography>
                            ) : (
                              <Typography variant="body2" sx={{
                                color: "text.secondary"
                              }}>
                                &mdash;
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={batchMutation.isPending}>
          {result ? t('actions.close', { ns: 'common' }) : t('actions.cancel', { ns: 'common' })}
        </Button>
        {!result && (
          <GradientButton
            onClick={handleRun}
            disabled={batchMutation.isPending || measureIds.length === 0}
          >
            {batchMutation.isPending ? t('batch.running') : t('batch.runBatch')}
          </GradientButton>
        )}
      </DialogActions>
    </Dialog>
  );
}
