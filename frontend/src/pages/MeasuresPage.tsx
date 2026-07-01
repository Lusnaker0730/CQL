import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Grid, Typography, Tabs, Tab } from '@mui/material'
import { APP_BAR_HEIGHT } from '../constants/layout'
import MeasureLibrary from '../components/measure/MeasureLibrary'
import MeasureEditor from '../components/measure/MeasureEditor'
import MeasureComparison from '../components/measure/MeasureComparison'
import MeasureDashboardPage from './MeasureDashboardPage'
import { measureApi } from '../api'
import type { MeasureDefinition } from '../types'

export default function MeasuresPage() {
  const { t } = useTranslation('measures')
  const [topTab, setTopTab] = useState(0)
  const [selectedMeasure, setSelectedMeasure] = useState<MeasureDefinition | null>(null)

  const handleSelectMeasure = async (measure: MeasureDefinition) => {
    // Load full measure data if we only have summary
    if (measure.id && !measure.cqlContent) {
      try {
        const full = await measureApi.getMeasure(measure.id)
        setSelectedMeasure(full)
      } catch {
        setSelectedMeasure(measure)
      }
    } else {
      setSelectedMeasure(measure)
    }
    setTopTab(1) // Switch to Measures tab
  }

  const handleMeasureUpdate = (updated: MeasureDefinition) => {
    setSelectedMeasure(updated)
  }

  return (
    <Box sx={{ height: `calc(100vh - ${APP_BAR_HEIGHT}px)`, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ px: 2, pt: 1.5, pb: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5 }}>
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {t('page.title')}
          </Typography>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('page.subtitle')}
          </Typography>
        </Box>
        <Tabs value={topTab} onChange={(_, v) => setTopTab(v)} sx={{ minHeight: 36, '& .MuiTab-root': { minHeight: 36, py: 0 } }}>
          <Tab label={t('page.tabs.dashboard')} />
          <Tab label={t('page.tabs.measures')} />
          <Tab label={t('page.tabs.comparison')} />
        </Tabs>
      </Box>
      {topTab === 0 && (
        <Box sx={{ flex: 1, minHeight: 0, p: 2, pt: 1, overflow: 'auto' }}>
          <MeasureDashboardPage />
        </Box>
      )}
      {topTab === 1 && (
        <Box sx={{ flex: 1, minHeight: 0, p: 2, pt: 1 }}>
          <Grid container spacing={2} sx={{ height: '100%' }}>
            {/* Left panel: Measure Library */}
            <Grid size={{ xs: 12, md: selectedMeasure ? 4 : 12 }} sx={{ height: '100%' }}>
              <MeasureLibrary onSelectMeasure={handleSelectMeasure} />
            </Grid>

            {/* Right panel: Measure Editor */}
            {selectedMeasure && (
              <Grid size={{ xs: 12, md: 8 }} sx={{ height: '100%' }}>
                <MeasureEditor
                  measure={selectedMeasure}
                  onMeasureUpdate={handleMeasureUpdate}
                />
              </Grid>
            )}

            {!selectedMeasure && (
              <Box sx={{ display: 'none' }}>
                {/* Placeholder — library takes full width when no measure selected */}
              </Box>
            )}
          </Grid>
        </Box>
      )}
      {topTab === 2 && (
        <Box sx={{ flex: 1, minHeight: 0, p: 2, pt: 1, overflow: 'auto' }}>
          <MeasureComparison />
        </Box>
      )}
    </Box>
  );
}
