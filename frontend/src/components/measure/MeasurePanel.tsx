import { useState, useEffect } from 'react'
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
  Autocomplete,
  Collapse,
  IconButton,
} from '@mui/material'
import {
  Assessment as AssessmentIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Schedule as ScheduleIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation, useQuery } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureEvaluationResult, PopulationResult, MeasureDefinition, StratifierResult } from '../../types'
import { useSelector, useDispatch } from 'react-redux'
import type { RootState } from '../../store'
import { setCqlContent } from '../../store/editorSlice'
import MeasureScheduleManager from './MeasureScheduleManager'
import { validateDateRange, validateFhirUrl } from '../../utils/validation'
import FhirServerUrlField from '../common/FhirServerUrlField'

interface MeasurePanelProps {
  selectedMeasure?: MeasureDefinition | null
}

export default function MeasurePanel({ selectedMeasure }: MeasurePanelProps) {
  const dispatch = useDispatch()
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const [measureId, setMeasureId] = useState('custom-measure')
  const [patientId, setPatientId] = useState('')
  const [periodStart, setPeriodStart] = useState('2024-01-01')
  const [periodEnd, setPeriodEnd] = useState('2024-12-31')
  const [fhirServer, setFhirServer] = useState('http://localhost:8090/fhir')
  const [result, setResult] = useState<MeasureEvaluationResult | null>(null)
  const [selectedDef, setSelectedDef] = useState<MeasureDefinition | null>(null)
  const [stratExpanded, setStratExpanded] = useState(false)
  const [showSchedules, setShowSchedules] = useState(false)
  const [dateError, setDateError] = useState<string | null>(null)
  const [fhirError, setFhirError] = useState<string | null>(null)

  const { data: measures = [] } = useQuery({
    queryKey: ['measures'],
    queryFn: () => measureApi.getMeasures(),
  })

  useEffect(() => {
    if (selectedMeasure) {
      setSelectedDef(selectedMeasure)
      setMeasureId(selectedMeasure.id?.toString() || selectedMeasure.name)
      if (selectedMeasure.cqlContent) {
        dispatch(setCqlContent(selectedMeasure.cqlContent))
      } else if (selectedMeasure.id) {
        measureApi.getMeasure(selectedMeasure.id).then((full) => {
          setSelectedDef(full)
          if (full.cqlContent) {
            dispatch(setCqlContent(full.cqlContent))
          }
        })
      }
    }
  }, [selectedMeasure, dispatch])

  const evaluateMutation = useMutation({
    mutationFn: () => {
      if (selectedDef?.id) {
        return measureApi.evaluateMeasure(
          selectedDef.id.toString(),
          patientId || undefined,
          periodStart,
          periodEnd,
          fhirServer
        )
      }
      return measureApi.evaluate({
        measureId,
        measureCql: cqlContent,
        patientId: patientId || undefined,
        periodStart,
        periodEnd,
        fhirServerUrl: fhirServer,
      })
    },
    onSuccess: (data) => {
      setResult(data)
    },
  })

  const handleEvaluate = () => {
    const dateErr = validateDateRange(periodStart, periodEnd)
    const fhirErr = validateFhirUrl(fhirServer)
    setDateError(dateErr)
    setFhirError(fhirErr)
    if (dateErr || fhirErr) return
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

  const getScoreHex = (score: number): string => {
    if (score >= 80) return '#2E7D32'
    if (score >= 60) return '#ED6C02'
    return '#D32F2F'
  }

  if (showSchedules && selectedDef) {
    return <MeasureScheduleManager measure={selectedDef} onClose={() => setShowSchedules(false)} />
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Quality Measure Evaluation
      </Typography>

      <Stack spacing={2}>
        <Autocomplete
          size="small"
          options={measures}
          getOptionLabel={(m) => `${m.title || m.name} v${m.version}`}
          value={selectedDef}
          onChange={async (_, value) => {
            setSelectedDef(value)
            if (value) {
              setMeasureId(value.id?.toString() || value.name)
              if (value.cqlContent) {
                dispatch(setCqlContent(value.cqlContent))
              } else if (value.id) {
                const full = await measureApi.getMeasure(value.id)
                setSelectedDef(full)
                if (full.cqlContent) {
                  dispatch(setCqlContent(full.cqlContent))
                }
              }
            }
          }}
          renderInput={(params) => (
            <TextField {...params} label="Select Saved Measure (optional)" />
          )}
          isOptionEqualToValue={(option, value) => option.id === value.id}
        />

        {!selectedDef && (
          <TextField
            label="Measure ID"
            value={measureId}
            onChange={(e) => setMeasureId(e.target.value)}
            size="small"
            fullWidth
          />
        )}

        <FhirServerUrlField
          value={fhirServer}
          onChange={(value) => {
            setFhirServer(value)
            setFhirError(null)
          }}
          error={!!fhirError}
          helperText={fhirError}
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
            onChange={(e) => {
              setPeriodStart(e.target.value)
              setDateError(null)
            }}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
            error={!!dateError}
          />
          <TextField
            label="Period End"
            type="date"
            value={periodEnd}
            onChange={(e) => {
              setPeriodEnd(e.target.value)
              setDateError(null)
            }}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
            error={!!dateError}
            helperText={dateError}
          />
        </Stack>

        <Stack direction="row" spacing={1}>
          <GradientButton
            onClick={handleEvaluate}
            disabled={evaluateMutation.isPending || (!cqlContent && !selectedDef)}
            startIcon={
              evaluateMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <AssessmentIcon />
            }
            sx={{
              flex: 1,
              '&.Mui-disabled': {
                background: 'rgba(0,0,0,0.12)',
              },
            }}
          >
            {evaluateMutation.isPending ? 'Evaluating...' : 'Evaluate Measure'}
          </GradientButton>
          {selectedDef && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<ScheduleIcon />}
              onClick={() => setShowSchedules(true)}
            >
              Schedules
            </Button>
          )}
        </Stack>

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
                    <Typography variant="h6" sx={{ color: 'secondary.main' }}>
                      {result.measureName || result.measureId}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {result.periodStart} - {result.periodEnd}
                    </Typography>
                  </Box>
                  <Chip
                    label={result.status}
                    color={result.status === 'complete' ? 'success' : result.status === 'error' ? 'error' : 'warning'}
                  />
                </Stack>

                {result.status === 'error' && result.errorMessage && (
                  <Alert severity="error" sx={{ mb: 2 }}>
                    {result.errorMessage}
                  </Alert>
                )}

                {result.groups?.map((group) => (
                  <Box key={group.groupId} mb={2}>
                    <Typography variant="subtitle1" gutterBottom sx={{ color: 'text.primary' }}>
                      {group.description || `Group: ${group.groupId}`}
                    </Typography>

                    {group.measureScore !== undefined && group.measureScore !== null && (
                      <Box mb={3} sx={{ textAlign: 'center' }}>
                        <Typography
                          sx={{
                            fontSize: '3rem',
                            fontWeight: 700,
                            color: getScoreHex(group.measureScore),
                            lineHeight: 1.1,
                          }}
                        >
                          {group.measureScore.toFixed(1)}%
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ mb: 1.5, display: 'block' }}>
                          Measure Score
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(group.measureScore, 100)}
                          color={getScoreColor(group.measureScore) as 'success' | 'warning' | 'error'}
                          sx={{
                            height: 10,
                            borderRadius: 5,
                            bgcolor: 'rgba(13,115,119,0.08)',
                          }}
                        />
                      </Box>
                    )}

                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell scope="col">Population</TableCell>
                            <TableCell scope="col" align="right">Count</TableCell>
                            <TableCell scope="col">Subjects</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {group.populations?.map((pop: PopulationResult) => (
                            <TableRow key={pop.populationId}>
                              <TableCell>
                                <Typography variant="body2" sx={{ color: 'text.primary', fontWeight: 500 }}>
                                  {getPopulationLabel(pop.populationType)}
                                </Typography>
                              </TableCell>
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

                    {/* Stratification Results */}
                    {group.stratifiers && group.stratifiers.length > 0 && (
                      <Box mt={2}>
                        <Stack direction="row" alignItems="center" spacing={1}
                          onClick={() => setStratExpanded(!stratExpanded)}
                          sx={{ cursor: 'pointer' }}>
                          <IconButton size="small" aria-label="Toggle stratification details">
                            {stratExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                          </IconButton>
                          <Typography variant="subtitle2">
                            Stratification ({group.stratifiers.length} strata)
                          </Typography>
                        </Stack>
                        <Collapse in={stratExpanded}>
                          <TableContainer sx={{ mt: 1 }}>
                            <Table size="small">
                              <TableHead>
                                <TableRow>
                                  <TableCell scope="col">Stratum</TableCell>
                                  <TableCell scope="col">Value</TableCell>
                                  <TableCell scope="col" align="right">Score</TableCell>
                                  <TableCell scope="col">Populations</TableCell>
                                </TableRow>
                              </TableHead>
                              <TableBody>
                                {group.stratifiers.map((strat: StratifierResult, idx: number) => (
                                  <TableRow key={idx}>
                                    <TableCell>
                                      <Typography variant="body2" fontWeight={500}>{strat.strataId}</Typography>
                                    </TableCell>
                                    <TableCell>
                                      <Chip label={strat.strataValue} size="small" />
                                    </TableCell>
                                    <TableCell align="right">
                                      {strat.measureScore != null && (
                                        <Typography variant="body2" sx={{
                                          color: getScoreHex(strat.measureScore),
                                          fontWeight: 600,
                                        }}>
                                          {strat.measureScore.toFixed(1)}%
                                        </Typography>
                                      )}
                                    </TableCell>
                                    <TableCell>
                                      <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                        {strat.populations?.map((pop, pidx) => (
                                          <Chip
                                            key={pidx}
                                            label={`${getPopulationLabel(pop.populationType)}: ${pop.count ?? 0}`}
                                            size="small"
                                            variant="outlined"
                                          />
                                        ))}
                                      </Stack>
                                    </TableCell>
                                  </TableRow>
                                ))}
                              </TableBody>
                            </Table>
                          </TableContainer>
                        </Collapse>
                      </Box>
                    )}
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
                        bgcolor: '#F8FAFB',
                        borderRadius: '8px',
                        border: '1px solid rgba(13,115,119,0.1)',
                        fontSize: '0.75rem',
                        overflow: 'auto',
                        fontFamily: '"Consolas", monospace',
                        color: 'text.primary',
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
