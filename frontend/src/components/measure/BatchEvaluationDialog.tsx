import { useState } from 'react'
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
import GradientButton from '../common/GradientButton'

interface BatchEvaluationDialogProps {
  open: boolean
  onClose: () => void
  measureIds: number[]
}

function getScoreColor(score: number): 'success' | 'warning' | 'error' {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'error'
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
  const [fhirServerUrl, setFhirServerUrl] = useState('')
  const [periodStart, setPeriodStart] = useState('2024-01-01')
  const [periodEnd, setPeriodEnd] = useState('2024-12-31')
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
      <DialogTitle>Batch Evaluate Measures</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Evaluate {measureIds.length} selected measure{measureIds.length !== 1 ? 's' : ''} in a single batch operation.
          </Typography>

          <TextField
            label="FHIR Server URL (optional)"
            value={fhirServerUrl}
            onChange={(e) => setFhirServerUrl(e.target.value)}
            size="small"
            fullWidth
            placeholder="http://hapi.fhir.org/baseR4"
            disabled={batchMutation.isPending}
          />

          <Stack direction="row" spacing={2}>
            <TextField
              label="Period Start"
              type="date"
              value={periodStart}
              onChange={(e) => setPeriodStart(e.target.value)}
              size="small"
              fullWidth
              InputLabelProps={{ shrink: true }}
              disabled={batchMutation.isPending}
            />
            <TextField
              label="Period End"
              type="date"
              value={periodEnd}
              onChange={(e) => setPeriodEnd(e.target.value)}
              size="small"
              fullWidth
              InputLabelProps={{ shrink: true }}
              disabled={batchMutation.isPending}
            />
          </Stack>

          {batchMutation.isPending && (
            <Box>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <CircularProgress size={20} />
                <Typography variant="body2">
                  Evaluating measures...
                </Typography>
              </Stack>
              <LinearProgress />
            </Box>
          )}

          {batchMutation.isError && (
            <Alert severity="error">
              Batch evaluation failed: {(batchMutation.error as Error).message}
            </Alert>
          )}

          {result && (
            <Box>
              <Alert
                severity={result.errorCount === 0 ? 'success' : 'warning'}
                sx={{ mb: 2 }}
              >
                {result.successCount} of {result.totalMeasures} measures evaluated
                successfully
                {result.errorCount > 0 && `, ${result.errorCount} error${result.errorCount !== 1 ? 's' : ''}`}
                {' '}&mdash; total duration: {formatDuration(result.totalDurationMs)}
              </Alert>

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell scope="col">Measure Name</TableCell>
                      <TableCell scope="col">Status</TableCell>
                      <TableCell scope="col">Score</TableCell>
                      <TableCell scope="col">Error Message</TableCell>
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
                                color={getScoreColor(score * 100)}
                              />
                            ) : (
                              <Typography variant="body2" color="text.secondary">
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
                              <Typography variant="body2" color="text.secondary">
                                &mdash;
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      )
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
          {result ? 'Close' : 'Cancel'}
        </Button>
        {!result && (
          <GradientButton
            onClick={handleRun}
            disabled={batchMutation.isPending || measureIds.length === 0}
          >
            {batchMutation.isPending ? 'Running...' : 'Run Batch Evaluation'}
          </GradientButton>
        )}
      </DialogActions>
    </Dialog>
  )
}
