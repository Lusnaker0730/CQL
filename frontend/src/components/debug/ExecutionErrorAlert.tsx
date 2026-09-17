import { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  Alert,
  AlertTitle,
  Chip,
  Collapse,
  IconButton,
  Paper,
} from '@mui/material'
import {
  KeyboardArrowDown as ExpandIcon,
  KeyboardArrowUp as CollapseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { ExecutionErrorInfo } from '../../types'

interface ExecutionErrorAlertProps {
  errorInfo: ExecutionErrorInfo
}

function phaseColor(phase: string): 'error' | 'warning' | 'info' {
  if (phase === 'cql_execution' || phase === 'cql_translation' || phase === 'card_generation') return 'error'
  if (
    phase === 'prefetch_resolution' ||
    phase === 'prefetch_provider_build' ||
    phase === 'fhir_retrieval' ||
    phase === 'population_evaluation'
  ) return 'warning'
  return 'info'
}

/**
 * Shared structured error alert. Renders phase chip + errorType + message +
 * optional collapsible stack trace. Used by the CDS debug panel (via CDS
 * invocation error path) and the editor debug panel (via CqlExecutionException
 * errorInfo on the 500 response).
 *
 * <p>Phase→severity mapping is centralised here: CDS previously had its own
 * inline mapping that didn't cover the newer FHIR_RETRIEVAL / POPULATION_EVALUATION
 * phases introduced with the unified ErrorPhase enum (PAT-098).
 */
export default function ExecutionErrorAlert({ errorInfo }: ExecutionErrorAlertProps) {
  const { t } = useTranslation('common')
  const [stackOpen, setStackOpen] = useState(false)

  return (
    <Alert severity={phaseColor(errorInfo.phase)} icon={false}>
      <AlertTitle>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Chip
            size="small"
            color={phaseColor(errorInfo.phase)}
            label={t(`debug.phase.${errorInfo.phase}`, { defaultValue: errorInfo.phase })}
          />
          <Typography component="span" variant="body2" sx={{ fontFamily: 'monospace' }}>
            {errorInfo.errorType}
          </Typography>
        </Stack>
      </AlertTitle>
      <Typography variant="body2" sx={{ mt: 1 }}>
        {errorInfo.message}
      </Typography>
      {errorInfo.stackTraceSummary && errorInfo.stackTraceSummary.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Stack direction="row" spacing={0.5} sx={{
            alignItems: "center"
          }}>
            <IconButton size="small" onClick={() => setStackOpen(!stackOpen)}>
              {stackOpen ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
            </IconButton>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>
              {t('debug.stackTrace')}
            </Typography>
          </Stack>
          <Collapse in={stackOpen} timeout="auto" unmountOnExit>
            <Paper variant="outlined" sx={{ p: 1, mt: 0.5, bgcolor: 'background.default' }}>
              <Box component="pre" sx={{ m: 0, fontSize: '0.75rem', overflowX: 'auto' }}>
                {errorInfo.stackTraceSummary.join('\n')}
              </Box>
            </Paper>
          </Collapse>
        </Box>
      )}
    </Alert>
  );
}
