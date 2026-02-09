import { useState } from 'react'
import { Box, Grid, Typography, Tabs, Tab } from '@mui/material'
import MeasureLibrary from '../components/measure/MeasureLibrary'
import MeasureEditor from '../components/measure/MeasureEditor'
import MeasureComparison from '../components/measure/MeasureComparison'
import MeasureDashboardPage from './MeasureDashboardPage'
import { measureApi } from '../api'
import type { MeasureDefinition } from '../types'

export default function MeasuresPage() {
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
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2, display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          Quality Measures (eCQM)
        </Typography>
        <Box
          sx={{
            width: 48,
            height: 3,
            borderRadius: 2,
            background: 'linear-gradient(90deg, #0D7377, #14A3A8)',
            mb: 1,
          }}
        />
        <Typography variant="body2" color="text.secondary">
          Define, evaluate, and analyze electronic Clinical Quality Measures using CQL.
        </Typography>
      </Box>

      <Tabs value={topTab} onChange={(_, v) => setTopTab(v)} sx={{ mb: 2 }}>
        <Tab label="Dashboard" />
        <Tab label="Measures" />
        <Tab label="Comparison & Trends" />
      </Tabs>

      <Box sx={{ flex: 1, minHeight: 0 }}>
        {topTab === 0 && (
          <MeasureDashboardPage />
        )}

        {topTab === 1 && (
          <Grid container spacing={2} sx={{ height: '100%' }}>
            {/* Left panel: Measure Library */}
            <Grid item xs={12} md={selectedMeasure ? 4 : 12} sx={{ height: '100%' }}>
              <MeasureLibrary onSelectMeasure={handleSelectMeasure} />
            </Grid>

            {/* Right panel: Measure Editor */}
            {selectedMeasure && (
              <Grid item xs={12} md={8} sx={{ height: '100%' }}>
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
        )}

        {topTab === 2 && (
          <MeasureComparison />
        )}
      </Box>
    </Box>
  )
}
