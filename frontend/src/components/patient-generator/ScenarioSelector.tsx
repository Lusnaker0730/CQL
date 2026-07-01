import { useCallback } from 'react'
import {
  Box,
  Typography,
  Button,
  Stack,
  Paper,
  Card,
  CardContent,
  CardActions,
  Chip,
  Grid,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { scenariosConfig } from '../../config/twcore'
import type { Scenario, CustomGenerationConfig } from '../../config/twcore'

interface ScenarioSelectorProps {
  isGenerating: boolean
  onGenerate: (config: CustomGenerationConfig) => void
}

type ChipColor = 'error' | 'info' | 'success'

const CHIP_SECTIONS: Array<{
  labelKey: string
  namesField: keyof Pick<Scenario, 'condition_names' | 'observation_names' | 'medication_names'>
  color: ChipColor
}> = [
  { labelKey: 'scenarios.conditions', namesField: 'condition_names', color: 'error' },
  { labelKey: 'scenarios.observations', namesField: 'observation_names', color: 'info' },
  { labelKey: 'scenarios.medications', namesField: 'medication_names', color: 'success' },
]

export default function ScenarioSelector({ isGenerating, onGenerate }: ScenarioSelectorProps) {
  const { t } = useTranslation('patientGenerator')

  const handleUseScenario = useCallback(
    (scenario: Scenario) => {
      onGenerate({
        selectedConditions: scenario.conditions,
        selectedObservations: scenario.observations,
        selectedMedications: scenario.medications,
        selectedAllergies: [],
        numEncounters: scenario.num_encounters,
        numPatients: scenario.recommended_patient_count,
      })
    },
    [onGenerate],
  )

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {t('scenarios.title')}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
        {t('scenarios.description')}
      </Typography>
      <Grid container spacing={2}>
        {scenariosConfig.scenarios.map((scenario) => (
          <Grid key={scenario.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card
              variant="outlined"
              sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                transition: 'box-shadow 0.2s',
                '&:hover': { boxShadow: 3 },
              }}
            >
              <CardContent sx={{ flex: 1 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                  <Typography variant="h5" component="span">
                    {scenario.icon}
                  </Typography>
                  <Typography variant="subtitle1" sx={{
                    fontWeight: 600
                  }}>
                    {scenario.name}
                  </Typography>
                </Stack>

                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1.5 }}>
                  {scenario.description}
                </Typography>

                <Stack spacing={0.5}>
                  <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                    <Chip
                      label={t('scenarios.patientCount', { count: scenario.recommended_patient_count })}
                      size="small"
                      color="primary"
                      variant="outlined"
                    />
                    <Chip
                      label={t('scenarios.encounters', { count: scenario.num_encounters })}
                      size="small"
                      variant="outlined"
                    />
                  </Stack>

                  {CHIP_SECTIONS.map(({ labelKey, namesField, color }) => {
                    const names = scenario[namesField]
                    if (names.length === 0) return null
                    return (
                      <Box key={labelKey}>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {t(labelKey)}:
                        </Typography>
                        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5, mt: 0.25 }}>
                          {names.map((name) => (
                            <Chip key={name} label={name} size="small" color={color} variant="outlined" />
                          ))}
                        </Stack>
                      </Box>
                    )
                  })}
                </Stack>
              </CardContent>

              <CardActions sx={{ px: 2, pb: 2 }}>
                <Button
                  variant="contained"
                  size="small"
                  fullWidth
                  onClick={() => handleUseScenario(scenario)}
                  disabled={isGenerating}
                >
                  {t('scenarios.useScenario')}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Paper>
  );
}
