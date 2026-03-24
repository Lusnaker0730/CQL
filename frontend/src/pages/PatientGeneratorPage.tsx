import { useState } from 'react'
import {
  Box,
  Container,
  Typography,
  Tabs,
  Tab,
  Stack,
} from '@mui/material'
import {
  GroupAdd as BatchIcon,
  Tune as CustomIcon,
  AutoAwesome as ScenarioIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { usePatientGenerator } from '../hooks/usePatientGenerator'
import BatchGeneratorPanel from '../components/patient-generator/BatchGeneratorPanel'
import CustomGeneratorPanel from '../components/patient-generator/CustomGeneratorPanel'
import ScenarioSelector from '../components/patient-generator/ScenarioSelector'
import GenerationResultPanel from '../components/patient-generator/GenerationResultPanel'

const TAB_CONFIG = [
  { key: 'batch', Icon: BatchIcon },
  { key: 'custom', Icon: CustomIcon },
  { key: 'scenarios', Icon: ScenarioIcon },
] as const

export default function PatientGeneratorPage() {
  const { t } = useTranslation('patientGenerator')
  const [tab, setTab] = useState(0)
  const {
    results,
    isGenerating,
    generateBatchPatients,
    generateCustom,
    download,
    clearResults,
  } = usePatientGenerator()

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>
          {t('title')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('subtitle')}
        </Typography>
      </Box>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minHeight: 48 } }}
        >
          {TAB_CONFIG.map(({ key, Icon }) => (
            <Tab
              key={key}
              icon={<Icon sx={{ fontSize: 20 }} />}
              iconPosition="start"
              label={t(`tabs.${key}`)}
            />
          ))}
        </Tabs>
      </Box>

      <Stack spacing={3}>
        {tab === 0 && (
          <BatchGeneratorPanel
            isGenerating={isGenerating}
            onGenerate={generateBatchPatients}
          />
        )}
        {tab === 1 && (
          <CustomGeneratorPanel
            isGenerating={isGenerating}
            onGenerate={generateCustom}
          />
        )}
        {tab === 2 && (
          <ScenarioSelector
            isGenerating={isGenerating}
            onGenerate={generateCustom}
          />
        )}

        <GenerationResultPanel
          results={results}
          onDownload={download}
          onClear={clearResults}
        />
      </Stack>
    </Container>
  )
}
