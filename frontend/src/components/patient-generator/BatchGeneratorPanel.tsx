import { useReducer, useCallback } from 'react'
import {
  Typography,
  Button,
  Stack,
  Paper,
  Box,
  CircularProgress,
} from '@mui/material'
import { PlayArrow as GenerateIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { BatchGenerationConfig } from '../../config/twcore'
import SliderField from './SliderField'
import DateRangeFields from './DateRangeFields'

interface BatchGeneratorPanelProps {
  isGenerating: boolean
  onGenerate: (config: BatchGenerationConfig) => void
}

type FormState = BatchGenerationConfig

type FormAction =
  | { field: keyof BatchGenerationConfig; value: number | string | undefined }

function formReducer(state: FormState, action: FormAction): FormState {
  return { ...state, [action.field]: action.value }
}

const INITIAL_STATE: FormState = {
  numPatients: 5,
  numConditions: 3,
  numObservations: 5,
  numMedications: 2,
  numEncounters: 2,
  numAllergies: 1,
  dateFrom: undefined,
  dateTo: undefined,
}

const SLIDER_FIELDS = [
  { field: 'numPatients', labelKey: 'batch.numPatients', min: 1, max: 100 },
  { field: 'numConditions', labelKey: 'batch.numConditions', min: 0, max: 20 },
  { field: 'numObservations', labelKey: 'batch.numObservations', min: 0, max: 50 },
  { field: 'numMedications', labelKey: 'batch.numMedications', min: 0, max: 20 },
  { field: 'numEncounters', labelKey: 'batch.numEncounters', min: 0, max: 10 },
  { field: 'numAllergies', labelKey: 'batch.numAllergies', min: 0, max: 20 },
] as const

export default function BatchGeneratorPanel({ isGenerating, onGenerate }: BatchGeneratorPanelProps) {
  const { t } = useTranslation('patientGenerator')
  const [form, dispatch] = useReducer(formReducer, INITIAL_STATE)

  const handleGenerate = useCallback(() => {
    onGenerate(form)
  }, [form, onGenerate])

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {t('batch.title')}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
        {t('batch.description')}
      </Typography>

      <Stack spacing={2.5}>
        {SLIDER_FIELDS.map(({ field, labelKey, min, max }) => (
          <SliderField
            key={field}
            label={t(labelKey)}
            value={form[field] as number}
            min={min}
            max={max}
            onChange={(v) => dispatch({ field, value: v })}
          />
        ))}

        <Box>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {t('batch.dateRange')}
          </Typography>
          <DateRangeFields
            dateFrom={form.dateFrom ?? ''}
            dateTo={form.dateTo ?? ''}
            onDateFromChange={(v) => dispatch({ field: 'dateFrom', value: v || undefined })}
            onDateToChange={(v) => dispatch({ field: 'dateTo', value: v || undefined })}
          />
        </Box>

        <Button
          variant="contained"
          size="large"
          startIcon={isGenerating ? <CircularProgress size={20} color="inherit" /> : <GenerateIcon />}
          onClick={handleGenerate}
          disabled={isGenerating}
          sx={{ mt: 1 }}
        >
          {isGenerating ? t('batch.generating') : t('batch.generate')}
        </Button>
      </Stack>
    </Paper>
  )
}
