import { useState } from 'react'
import { Box, Tabs, Tab, Paper, Stack, Typography } from '@mui/material'
import type { MeasureDefinition } from '../../types'
import MeasureDetailsTab from './MeasureDetailsTab'
import MeasureCqlTab from './MeasureCqlTab'
import PopulationCriteriaTab from './PopulationCriteriaTab'
import MeasureEvaluationTab from './MeasureEvaluationTab'
import MeasureReportHistory from './MeasureReportHistory'
import TestCasesTab from './TestCasesTab'
import WorkflowIndicator from './WorkflowIndicator'
import StatusChip from '../common/StatusChip'

interface MeasureEditorProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureEditor({ measure, onMeasureUpdate }: MeasureEditorProps) {
  const [tab, setTab] = useState(0)

  return (
    <Paper sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ px: 2, pt: 1, pb: 0.5 }}
      >
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="subtitle1" noWrap sx={{ maxWidth: 240 }}>
            {measure.title || measure.name}
          </Typography>
          <StatusChip status={measure.status || 'draft'} />
        </Stack>
        <WorkflowIndicator measure={measure} />
      </Stack>
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            '& .MuiTab-root': { textTransform: 'none', minHeight: 42, fontSize: '0.85rem' },
          }}
        >
          <Tab label="Details" />
          <Tab label="CQL" />
          <Tab label="Population Criteria" />
          <Tab label="Evaluate" />
          <Tab label="Test Cases" />
          <Tab label="Reports" />
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {tab === 0 && (
          <MeasureDetailsTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 1 && (
          <MeasureCqlTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 2 && (
          <PopulationCriteriaTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 3 && (
          <MeasureEvaluationTab measure={measure} />
        )}
        {tab === 4 && (
          <TestCasesTab measure={measure} />
        )}
        {tab === 5 && (
          <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
            <MeasureReportHistory />
          </Box>
        )}
      </Box>
    </Paper>
  )
}
