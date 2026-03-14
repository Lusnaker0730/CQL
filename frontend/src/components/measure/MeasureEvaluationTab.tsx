import { useState } from 'react'
import { extractApiError } from '../../utils/errorUtils'
import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  CircularProgress,
  Alert,
  Divider,
} from '@mui/material'
import {
  Assessment as AssessmentIcon,
  Schedule as ScheduleIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { measureApi } from '../../api'
import { helpContent } from '../../constants/helpContent'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'
import type { MeasureDefinition, MeasureEvaluationResult } from '../../types'
import { validateDateRange, validateFhirUrl } from '../../utils/validation'
import { getDefaultMeasurePeriod } from '../../utils/dateDefaults'
import { DEFAULT_FHIR_SERVER_URL } from '../../config/env'
import FhirServerUrlField from '../common/FhirServerUrlField'
import EvaluationResultCard from './EvaluationResultCard'
import MeasureScheduleManager from './MeasureScheduleManager'

interface MeasureEvaluationTabProps {
  measure: MeasureDefinition
}

export default function MeasureEvaluationTab({ measure }: MeasureEvaluationTabProps) {
  const { t } = useTranslation('measures')
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const [patientId, setPatientId] = useState('')
  const { periodStart: defaultStart, periodEnd: defaultEnd } = getDefaultMeasurePeriod()
  const [periodStart, setPeriodStart] = useState(defaultStart)
  const [periodEnd, setPeriodEnd] = useState(defaultEnd)
  const [fhirServer, setFhirServer] = useState(DEFAULT_FHIR_SERVER_URL)
  const [result, setResult] = useState<MeasureEvaluationResult | null>(null)
  const [showSchedules, setShowSchedules] = useState(false)
  const [dateError, setDateError] = useState<string | null>(null)
  const [fhirError, setFhirError] = useState<string | null>(null)

  const evaluateMutation = useMutation({
    mutationFn: () => {
      if (measure.id) {
        return measureApi.evaluateMeasure(
          measure.id.toString(),
          patientId || undefined,
          periodStart,
          periodEnd,
          fhirServer
        )
      }
      return measureApi.evaluate({
        measureId: measure.name,
        measureCql: cqlContent,
        patientId: patientId || undefined,
        periodStart,
        periodEnd,
        fhirServerUrl: fhirServer,
      })
    },
    onSuccess: (data) => setResult(data),
  })

  const handleEvaluate = () => {
    const dateErr = validateDateRange(periodStart, periodEnd)
    const fhirErr = validateFhirUrl(fhirServer)
    setDateError(dateErr)
    setFhirError(fhirErr)
    if (dateErr || fhirErr) return
    evaluateMutation.mutate()
  }

  if (showSchedules) {
    return <MeasureScheduleManager measure={measure} onClose={() => setShowSchedules(false)} />
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <Stack direction="row" spacing={0.5} alignItems="center" mb={1}>
        <Typography variant="h6">
          {t('evaluation.title', { name: measure.title || measure.name })}
        </Typography>
        <HelpTooltip text={helpContent.measures.evaluate} />
      </Stack>

      <Stack spacing={2}>
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
          label={t('evaluation.patientId')}
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          size="small"
          fullWidth
          placeholder={t('evaluation.patientIdPlaceholder')}
        />

        <Stack direction="row" spacing={2}>
          <TextField
            label={t('evaluation.periodStart')}
            type="date"
            value={periodStart}
            onChange={(e) => { setPeriodStart(e.target.value); setDateError(null) }}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
            error={!!dateError}
          />
          <TextField
            label={t('evaluation.periodEnd')}
            type="date"
            value={periodEnd}
            onChange={(e) => { setPeriodEnd(e.target.value); setDateError(null) }}
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
            disabled={evaluateMutation.isPending || (!cqlContent && !measure.cqlContent)}
            startIcon={
              evaluateMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <AssessmentIcon />
            }
            sx={{
              flex: 1,
              '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' },
            }}
          >
            {evaluateMutation.isPending ? t('evaluation.evaluating') : t('evaluation.evaluateMeasure')}
          </GradientButton>
          <Button
            variant="outlined"
            size="small"
            startIcon={<ScheduleIcon />}
            onClick={() => setShowSchedules(true)}
          >
            {t('evaluation.schedules')}
          </Button>
        </Stack>

        <Divider />

        {evaluateMutation.isError && (
          <Alert severity="error">
            {t('evaluation.evaluationFailed', { error: extractApiError(evaluateMutation.error) })}
          </Alert>
        )}

        {result && <EvaluationResultCard result={result} />}

        {!result && !evaluateMutation.isPending && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            {t('evaluation.emptyState')}
          </Typography>
        )}
      </Stack>
    </Box>
  )
}
