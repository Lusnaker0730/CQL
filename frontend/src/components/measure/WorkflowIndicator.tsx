import { Stack, Typography, Box, Tooltip } from '@mui/material'
import {
  CheckCircle as CompleteIcon,
  RadioButtonUnchecked as IncompleteIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { MeasureDefinition } from '../../types'
import { MEASURE_STATUS } from '../../constants/measureConstants'

interface WorkflowStepDef {
  key: string
  check: (m: MeasureDefinition) => boolean
}

const WORKFLOW_STEP_DEFS: WorkflowStepDef[] = [
  {
    key: 'details',
    check: (m) => !!(m.name && m.version && m.scoringType && (m.steward || m.rationale || m.title)),
  },
  {
    key: 'cql',
    check: (m) => !!(m.cqlContent && m.cqlContent.trim().length > 0),
  },
  {
    key: 'populations',
    check: (m) => !!(m.groupDefinitions && m.groupDefinitions.length > 0 &&
      m.groupDefinitions.some(g => g.populations && g.populations.length > 0)),
  },
  {
    key: 'review',
    check: (m) => m.status === MEASURE_STATUS.IN_REVIEW || m.status === MEASURE_STATUS.ACTIVE,
  },
  {
    key: 'active',
    check: (m) => m.status === MEASURE_STATUS.ACTIVE,
  },
]

interface WorkflowIndicatorProps {
  measure: MeasureDefinition
}

export default function WorkflowIndicator({ measure }: WorkflowIndicatorProps) {
  const { t } = useTranslation('measures')
  const completedCount = WORKFLOW_STEP_DEFS.filter(s => s.check(measure)).length

  return (
    <Stack direction="row" spacing={0.5} sx={{
      alignItems: "center"
    }}>
      <Typography
        variant="caption"
        sx={{
          color: "text.secondary",
          mr: 0.5
        }}>
        {completedCount}/{WORKFLOW_STEP_DEFS.length}
      </Typography>
      {WORKFLOW_STEP_DEFS.map((step, i) => {
        const done = step.check(measure)
        const label = t(`workflow.steps.${step.key}.label`)
        const tooltip = t(`workflow.steps.${step.key}.tooltip`)
        return (
          <Tooltip key={step.key} title={`${label}: ${tooltip}`} arrow>
            <Stack direction="row" spacing={0.25} sx={{
              alignItems: "center"
            }}>
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
                {label}
              </Typography>
              {i < WORKFLOW_STEP_DEFS.length - 1 && (
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
        );
      })}
    </Stack>
  );
}
