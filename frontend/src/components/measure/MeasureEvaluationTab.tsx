import { useState } from 'react'
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
import { measureApi } from '../../api'
import { helpContent } from '../../constants/helpContent'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'
import type { MeasureDefinition, MeasureEvaluationResult } from '../../types'
import { validateDateRange, validateFhirUrl } from '../../utils/validation'
import FhirServerUrlField from '../common/FhirServerUrlField'
import EvaluationResultCard from './EvaluationResultCard'
import MeasureScheduleManager from './MeasureScheduleManager'

interface MeasureEvaluationTabProps {
  measure: MeasureDefinition
}

export default function MeasureEvaluationTab({ measure }: MeasureEvaluationTabProps) {
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const [patientId, setPatientId] = useState('')
  const [periodStart, setPeriodStart] = useState('2024-01-01')
  const [periodEnd, setPeriodEnd] = useState('2024-12-31')
  const [fhirServer, setFhirServer] = useState('http://hapi-fhir:8080/fhir')
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
          Evaluate: {measure.title || measure.name}
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
            onChange={(e) => { setPeriodStart(e.target.value); setDateError(null) }}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
            error={!!dateError}
          />
          <TextField
            label="Period End"
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
            {evaluateMutation.isPending ? 'Evaluating...' : 'Evaluate Measure'}
          </GradientButton>
          <Button
            variant="outlined"
            size="small"
            startIcon={<ScheduleIcon />}
            onClick={() => setShowSchedules(true)}
          >
            Schedules
          </Button>
        </Stack>

        <Divider />

        {evaluateMutation.isError && (
          <Alert severity="error">
            Evaluation failed: {(evaluateMutation.error as Error).message}
          </Alert>
        )}

        {result && <EvaluationResultCard result={result} />}

        {!result && !evaluateMutation.isPending && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            Click "Evaluate Measure" to run the measure against patient data.
          </Typography>
        )}
      </Stack>
    </Box>
  )
}
