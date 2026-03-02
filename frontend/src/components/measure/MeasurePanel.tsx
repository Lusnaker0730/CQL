import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { getScoreChipColor, getScoreThemeColor } from '../../utils/scoreColors'
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
import { getDefaultMeasurePeriod } from '../../utils/dateDefaults'
import { DEFAULT_FHIR_SERVER_URL } from '../../config/env'
import { useNotification } from '../../hooks/useNotification'
import FhirServerUrlField from '../common/FhirServerUrlField'
import { extractApiError } from '../../utils/errorUtils'

interface MeasurePanelProps {
  selectedMeasure?: MeasureDefinition | null
}

export default function MeasurePanel({ selectedMeasure }: MeasurePanelProps) {
  const { t } = useTranslation('measures')
  const dispatch = useDispatch()
  const { showNotification } = useNotification()
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const [measureId, setMeasureId] = useState('custom-measure')
  const [patientId, setPatientId] = useState('')
  const { periodStart: defaultStart, periodEnd: defaultEnd } = getDefaultMeasurePeriod()
  const [periodStart, setPeriodStart] = useState(defaultStart)
  const [periodEnd, setPeriodEnd] = useState(defaultEnd)
  const [fhirServer, setFhirServer] = useState(DEFAULT_FHIR_SERVER_URL)
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
        }).catch((err) => {
          showNotification(t('panel.loadError', { error: extractApiError(err) }), 'error')
        })
      }
    }
  }, [selectedMeasure, dispatch, showNotification])

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
    const key = `evaluationResult.populationLabels.${type}`
    const translated = t(key)
    return translated === key ? type : translated
  }


  if (showSchedules && selectedDef) {
    return <MeasureScheduleManager measure={selectedDef} onClose={() => setShowSchedules(false)} />
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('panel.title')}
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
            <TextField {...params} label={t('panel.selectMeasure')} />
          )}
          isOptionEqualToValue={(option, value) => option.id === value.id}
        />

        {!selectedDef && (
          <TextField
            label={t('panel.measureId')}
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
          label={t('panel.patientId')}
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          size="small"
          fullWidth
          placeholder={t('panel.patientIdPlaceholder')}
        />

        <Stack direction="row" spacing={2}>
          <TextField
            label={t('panel.periodStart')}
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
            label={t('panel.periodEnd')}
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
            {evaluateMutation.isPending ? t('panel.evaluating') : t('panel.evaluateMeasure')}
          </GradientButton>
          {selectedDef && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<ScheduleIcon />}
              onClick={() => setShowSchedules(true)}
            >
              {t('panel.schedules')}
            </Button>
          )}
        </Stack>

        <Divider />

        {evaluateMutation.isError && (
          <Alert severity="error">
            {t('panel.evaluationFailed', { error: extractApiError(evaluateMutation.error) })}
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
                      {group.description || t('evaluationResult.groupLabel', { groupId: group.groupId })}
                    </Typography>

                    {group.measureScore !== undefined && group.measureScore !== null && (
                      <Box mb={3} sx={{ textAlign: 'center' }}>
                        <Typography
                          sx={{
                            fontSize: '3rem',
                            fontWeight: 700,
                            color: getScoreThemeColor(group.measureScore),
                            lineHeight: 1.1,
                          }}
                        >
                          {group.measureScore.toFixed(1)}%
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ mb: 1.5, display: 'block' }}>
                          {t('evaluationResult.measureScore')}
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(group.measureScore, 100)}
                          color={getScoreChipColor(group.measureScore) as 'success' | 'warning' | 'error'}
                          sx={{
                            height: 10,
                            borderRadius: 5,
                            bgcolor: (theme) => `${theme.palette.primary.main}14`,
                          }}
                        />
                      </Box>
                    )}

                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell scope="col">{t('evaluationResult.tableHeaders.population')}</TableCell>
                            <TableCell scope="col" align="right">{t('evaluationResult.tableHeaders.count')}</TableCell>
                            <TableCell scope="col">{t('evaluationResult.tableHeaders.subjects')}</TableCell>
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
                                  label={pop.count ?? t('evaluationResult.na')}
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
                          <IconButton size="small" aria-label={t('evaluationResult.toggleStratification')}>
                            {stratExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                          </IconButton>
                          <Typography variant="subtitle2">
                            {t('evaluationResult.stratification', { count: group.stratifiers.length })}
                          </Typography>
                        </Stack>
                        <Collapse in={stratExpanded}>
                          <TableContainer sx={{ mt: 1 }}>
                            <Table size="small">
                              <TableHead>
                                <TableRow>
                                  <TableCell scope="col">{t('evaluationResult.stratificationHeaders.stratum')}</TableCell>
                                  <TableCell scope="col">{t('evaluationResult.stratificationHeaders.value')}</TableCell>
                                  <TableCell scope="col" align="right">{t('evaluationResult.stratificationHeaders.score')}</TableCell>
                                  <TableCell scope="col">{t('evaluationResult.stratificationHeaders.populations')}</TableCell>
                                </TableRow>
                              </TableHead>
                              <TableBody>
                                {group.stratifiers.map((strat: StratifierResult) => (
                                  <TableRow key={`${strat.strataId}-${strat.strataValue}`}>
                                    <TableCell>
                                      <Typography variant="body2" fontWeight={500}>{strat.strataId}</Typography>
                                    </TableCell>
                                    <TableCell>
                                      <Chip label={strat.strataValue} size="small" />
                                    </TableCell>
                                    <TableCell align="right">
                                      {strat.measureScore != null && (
                                        <Typography variant="body2" sx={{
                                          color: getScoreThemeColor(strat.measureScore),
                                          fontWeight: 600,
                                        }}>
                                          {strat.measureScore.toFixed(1)}%
                                        </Typography>
                                      )}
                                    </TableCell>
                                    <TableCell>
                                      <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                        {strat.populations?.map((pop) => (
                                          <Chip
                                            key={pop.populationType}
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
                      {t('evaluationResult.supplementalData')}
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
            {t('panel.emptyState')}
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
