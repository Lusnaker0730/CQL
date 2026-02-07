import { useState } from 'react'
import { Box, Grid, Typography, Tabs, Tab } from '@mui/material'
import MeasurePanel from '../components/measure/MeasurePanel'
import MeasureLibrary from '../components/measure/MeasureLibrary'
import MeasureReportHistory from '../components/measure/MeasureReportHistory'
import MeasureComparison from '../components/measure/MeasureComparison'
import CqlEditor from '../components/editor/CqlEditor'
import type { MeasureDefinition } from '../types'

export default function MeasuresPage() {
  const [tab, setTab] = useState(0)
  const [selectedMeasure, setSelectedMeasure] = useState<MeasureDefinition | null>(null)

  const handleSelectMeasure = (measure: MeasureDefinition) => {
    setSelectedMeasure(measure)
    setTab(1) // Switch to Evaluate tab
  }

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
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
          Evaluate electronic Clinical Quality Measures using CQL. Define your measure logic in the
          editor and evaluate against patient data.
        </Typography>
      </Box>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Library" />
        <Tab label="Evaluate" />
        <Tab label="Reports" />
        <Tab label="Comparison" />
      </Tabs>

      <Box sx={{ height: 'calc(100% - 140px)' }}>
        {tab === 0 && (
          <MeasureLibrary onSelectMeasure={handleSelectMeasure} />
        )}

        {tab === 1 && (
          <Grid container spacing={2} sx={{ height: '100%' }}>
            <Grid item xs={12} md={6}>
              <CqlEditor height="100%" />
            </Grid>
            <Grid item xs={12} md={6}>
              <MeasurePanel selectedMeasure={selectedMeasure} />
            </Grid>
          </Grid>
        )}

        {tab === 2 && (
          <MeasureReportHistory />
        )}

        {tab === 3 && (
          <MeasureComparison />
        )}
      </Box>
    </Box>
  )
}
