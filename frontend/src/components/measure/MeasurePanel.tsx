import { useState } from 'react'
import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  CircularProgress,
  Alert,
  Card,
  CardContent,
  Divider,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
} from '@mui/material'
import { Assessment as AssessmentIcon } from '@mui/icons-material'
import { useMutation } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureEvaluationResult, PopulationResult } from '../../types'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

export default function MeasurePanel() {
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const [measureId, setMeasureId] = useState('custom-measure')
  const [patientId, setPatientId] = useState('')
  const [periodStart, setPeriodStart] = useState('2024-01-01')
  const [periodEnd, setPeriodEnd] = useState('2024-12-31')
  const [fhirServer, setFhirServer] = useState('http://hapi.fhir.org/baseR4')
  const [result, setResult] = useState<MeasureEvaluationResult | null>(null)

  const evaluateMutation = useMutation({
    mutationFn: () =>
      measureApi.evaluate({
        measureId,
        measureCql: cqlContent,
        patientId: patientId || undefined,
        periodStart,
        periodEnd,
        fhirServerUrl: fhirServer,
      }),
    onSuccess: (data) => {
      setResult(data)
    },
  })

  const handleEvaluate = () => {
    evaluateMutation.mutate()
  }

  const getPopulationLabel = (type: string): string => {
    const labels: Record<string, string> = {
      'initial-population': 'Initial Population',
      denominator: 'Denominator',
      'denominator-exclusion': 'Denominator Exclusions',
      'denominator-exception': 'Denominator Exceptions',
      numerator: 'Numerator',
      'numerator-exclusion': 'Numerator Exclusions',
      'measure-population': 'Measure Population',
      'measure-population-exclusion': 'Measure Population Exclusions',
      'measure-observation': 'Measure Observation',
    }
    return labels[type] || type
  }

  const getScoreColor = (score: number): string => {
    if (score >= 80) return 'success'
    if (score >= 60) return 'warning'
    return 'error'
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Quality Measure Evaluation
      </Typography>

      <Stack spacing={2}>
        <TextField
          label="Measure ID"
          value={measureId}
          onChange={(e) => setMeasureId(e.target.value)}
          size="small"
          fullWidth
        />

        <TextField
          label="FHIR Server URL"
          value={fhirServer}
          onChange={(e) => setFhirServer(e.target.value)}
          size="small"
          fullWidth
        />

        <TextField
          label="Patient ID (optional for individual report)"
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          size="small"
          fullWidth
          placeholder="Leave empty for population report"
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
          />
          <TextField
            label="Period End"
            type="date"
            value={periodEnd}
            onChange={(e) => setPeriodEnd(e.target.value)}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
          />
        </Stack>

        <Button
          variant="contained"
          onClick={handleEvaluate}
          disabled={evaluateMutation.isPending || !cqlContent}
          startIcon={
            evaluateMutation.isPending ? <CircularProgress size={20} /> : <AssessmentIcon />
          }
        >
          {evaluateMutation.isPending ? 'Evaluating...' : 'Evaluate Measure'}
        </Button>

        <Divider />

        {evaluateMutation.isError && (
          <Alert severity="error">
            Evaluation failed: {(evaluateMutation.error as Error).message}
          </Alert>
        )}

        {result && (
          <Box>
            <Card variant="outlined" sx={{ mb: 2 }}>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                  <Box>
                    <Typography variant="h6">{result.measureName || result.measureId}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {result.periodStart} - {result.periodEnd}
                    </Typography>
                  </Box>
                  <Chip
                    label={result.status}
                    color={result.status === 'complete' ? 'success' : 'warning'}
                  />
                </Stack>

                {result.groups.map((group) => (
                  <Box key={group.groupId} mb={2}>
                    <Typography variant="subtitle1" gutterBottom>
                      {group.description || `Group: ${group.groupId}`}
                    </Typography>

                    {group.measureScore !== undefined && group.measureScore !== null && (
                      <Box mb={2}>
                        <Stack direction="row" justifyContent="space-between" mb={1}>
                          <Typography variant="body2">Measure Score</Typography>
                          <Typography
                            variant="body2"
                            fontWeight="bold"
                            color={`${getScoreColor(group.measureScore)}.main`}
                          >
                            {group.measureScore.toFixed(1)}%
                          </Typography>
                        </Stack>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(group.measureScore, 100)}
                          color={getScoreColor(group.measureScore) as 'success' | 'warning' | 'error'}
                          sx={{ height: 8, borderRadius: 4 }}
                        />
                      </Box>
                    )}

                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Population</TableCell>
                            <TableCell align="right">Count</TableCell>
                            <TableCell>Subjects</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {group.populations.map((pop: PopulationResult) => (
                            <TableRow key={pop.populationId}>
                              <TableCell>{getPopulationLabel(pop.populationType)}</TableCell>
                              <TableCell align="right">
                                <Chip
                                  label={pop.count ?? 'N/A'}
                                  size="small"
                                  color={pop.count && pop.count > 0 ? 'primary' : 'default'}
                                />
                              </TableCell>
                              <TableCell>
                                {pop.subjectIds && pop.subjectIds.length > 0 && (
                                  <Typography variant="caption">
                                    {pop.subjectIds.join(', ')}
                                  </Typography>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Box>
                ))}

                {result.supplementalData && Object.keys(result.supplementalData).length > 0 && (
                  <Box mt={2}>
                    <Typography variant="subtitle2" gutterBottom>
                      Supplemental Data
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        p: 2,
                        bgcolor: 'grey.100',
                        borderRadius: 1,
                        fontSize: '0.75rem',
                        overflow: 'auto',
                      }}
                    >
                      {JSON.stringify(result.supplementalData, null, 2)}
                    </Box>
                  </Box>
                )}
              </CardContent>
            </Card>
          </Box>
        )}

        {!result && !evaluateMutation.isPending && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            Enter measure details and click "Evaluate Measure" to see results.
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
