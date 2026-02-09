import { Stack, Typography, Box, Tooltip } from '@mui/material'
import {
  CheckCircle as CompleteIcon,
  RadioButtonUnchecked as IncompleteIcon,
} from '@mui/icons-material'
import type { MeasureDefinition } from '../../types'

interface WorkflowStep {
  label: string
  tooltip: string
  check: (m: MeasureDefinition) => boolean
}

const WORKFLOW_STEPS: WorkflowStep[] = [
  {
    label: 'Details',
    tooltip: 'Name, version, scoring type, and key metadata (steward, rationale) defined',
    check: (m) => !!(m.name && m.version && m.scoringType && (m.steward || m.rationale || m.title)),
  },
  {
    label: 'CQL',
    tooltip: 'CQL logic written and saved',
    check: (m) => !!(m.cqlContent && m.cqlContent.trim().length > 0),
  },
  {
    label: 'Populations',
    tooltip: 'At least one population criteria defined',
    check: (m) => !!(m.groupDefinitions && m.groupDefinitions.length > 0 &&
      m.groupDefinitions.some(g => g.populations && g.populations.length > 0)),
  },
  {
    label: 'Review',
    tooltip: 'Measure submitted for review and approved',
    check: (m) => m.status === 'in-review' || m.status === 'active',
  },
  {
    label: 'Active',
    tooltip: 'Measure status set to active',
    check: (m) => m.status === 'active',
  },
]

interface WorkflowIndicatorProps {
  measure: MeasureDefinition
}

export default function WorkflowIndicator({ measure }: WorkflowIndicatorProps) {
  const completedCount = WORKFLOW_STEPS.filter(s => s.check(measure)).length

  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
        {completedCount}/{WORKFLOW_STEPS.length}
      </Typography>
      {WORKFLOW_STEPS.map((step, i) => {
        const done = step.check(measure)
        return (
          <Tooltip key={step.label} title={`${step.label}: ${step.tooltip}`} arrow>
            <Stack direction="row" alignItems="center" spacing={0.25}>
              {done ? (
                <CompleteIcon sx={{ fontSize: 14, color: 'success.main' }} />
              ) : (
                <IncompleteIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
              )}
              <Typography
                variant="caption"
                sx={{
                  fontSize: '0.65rem',
                  color: done ? 'success.main' : 'text.disabled',
                  fontWeight: done ? 600 : 400,
                }}
              >
                {step.label}
              </Typography>
              {i < WORKFLOW_STEPS.length - 1 && (
                <Box
                  sx={{
                    width: 12,
                    height: 1,
                    bgcolor: done ? 'success.main' : 'divider',
                    mx: 0.25,
                  }}
                />
              )}
            </Stack>
          </Tooltip>
        )
      })}
    </Stack>
  )
}
