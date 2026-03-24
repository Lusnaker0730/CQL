import { useState, useMemo, useCallback } from 'react'
import {
  Typography,
  Button,
  Stack,
  Paper,
  CircularProgress,
  Box,
} from '@mui/material'
import { PlayArrow as GenerateIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import {
  conditionsConfig,
  observationsConfig,
  medicationsConfig,
  allergiesConfig,
} from '../../config/twcore'
import type { CustomGenerationConfig } from '../../config/twcore'
import type { ConfigFile } from '../../config/twcore/types'
import ResourceSelector from './ResourceSelector'
import SliderField from './SliderField'
import DateRangeFields from './DateRangeFields'

interface CustomGeneratorPanelProps {
  isGenerating: boolean
  onGenerate: (config: CustomGenerationConfig) => void
}

/** Extract { name, items } from a categorized config, keyed by category */
function buildCategoryMap<T>(
  config: ConfigFile<T>,
  itemKey: 'conditions' | 'observations' | 'medications' | 'allergies',
): Record<string, { name: string; items: T[] }> {
  return Object.fromEntries(
    Object.entries(config.categories).map(([key, cat]) => [
      key,
      { name: cat.name, items: (cat[itemKey] as T[] | undefined) ?? [] },
    ]),
  )
}

export default function CustomGeneratorPanel({ isGenerating, onGenerate }: CustomGeneratorPanelProps) {
  const { t } = useTranslation('patientGenerator')
  const [selectedConditions, setSelectedConditions] = useState<string[]>([])
  const [selectedObservations, setSelectedObservations] = useState<string[]>([])
  const [selectedMedications, setSelectedMedications] = useState<string[]>([])
  const [selectedAllergies, setSelectedAllergies] = useState<string[]>([])
  const [numEncounters, setNumEncounters] = useState(2)
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  const conditionCategories = useMemo(() => buildCategoryMap(conditionsConfig, 'conditions'), [])
  const observationCategories = useMemo(() => buildCategoryMap(observationsConfig, 'observations'), [])
  const medicationCategories = useMemo(() => buildCategoryMap(medicationsConfig, 'medications'), [])
  const allergyCategories = useMemo(() => buildCategoryMap(allergiesConfig, 'allergies'), [])

  const handleGenerate = useCallback(() => {
    onGenerate({
      selectedConditions,
      selectedObservations,
      selectedMedications,
      selectedAllergies,
      numEncounters,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
    })
  }, [selectedConditions, selectedObservations, selectedMedications, selectedAllergies, numEncounters, dateFrom, dateTo, onGenerate])

  const hasSelection =
    selectedConditions.length > 0 ||
    selectedObservations.length > 0 ||
    selectedMedications.length > 0 ||
    selectedAllergies.length > 0

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {t('custom.title')}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
        {t('custom.description')}
      </Typography>

      <Stack spacing={3}>
        <ResourceSelector
          title={t('custom.selectConditions')}
          categories={conditionCategories}
          selectedCodes={selectedConditions}
          onSelectionChange={setSelectedConditions}
        />
        <ResourceSelector
          title={t('custom.selectObservations')}
          categories={observationCategories}
          selectedCodes={selectedObservations}
          onSelectionChange={setSelectedObservations}
        />
        <ResourceSelector
          title={t('custom.selectMedications')}
          categories={medicationCategories}
          selectedCodes={selectedMedications}
          onSelectionChange={setSelectedMedications}
        />
        <ResourceSelector
          title={t('custom.selectAllergies')}
          categories={allergyCategories}
          selectedCodes={selectedAllergies}
          onSelectionChange={setSelectedAllergies}
        />

        <SliderField
          label={t('custom.numEncounters')}
          value={numEncounters}
          min={0}
          max={10}
          onChange={setNumEncounters}
        />

        <Box>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {t('batch.dateRange')}
          </Typography>
          <DateRangeFields
            dateFrom={dateFrom}
            dateTo={dateTo}
            onDateFromChange={setDateFrom}
            onDateToChange={setDateTo}
          />
        </Box>

        <Button
          variant="contained"
          size="large"
          startIcon={isGenerating ? <CircularProgress size={20} color="inherit" /> : <GenerateIcon />}
          onClick={handleGenerate}
          disabled={isGenerating || !hasSelection}
          sx={{ mt: 1 }}
        >
          {isGenerating ? t('custom.generating') : t('custom.generate')}
        </Button>
      </Stack>
    </Paper>
  )
}
