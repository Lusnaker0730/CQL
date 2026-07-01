import { Box, Typography, Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { DebugTrace } from '../../types'
import ExpressionTraceTable from '../debug/ExpressionTraceTable'
import RetrieveTraceTable from '../debug/RetrieveTraceTable'

interface DebugPanelProps {
  trace: DebugTrace
}

/**
 * Debug panel for the CQL editor execute flow. Thin orchestrator over the
 * shared trace tables — the heavy table rendering lives in components/debug/
 * (extracted PAT-099) so CDS and editor paths don't duplicate.
 */
export default function DebugPanel({ trace }: DebugPanelProps) {
  const { t } = useTranslation('common')

  return (
    <Stack spacing={2} sx={{ mt: 2 }}>
      <ExpressionTraceTable traces={trace.expressionTraces} />
      <RetrieveTraceTable traces={trace.retrieveTraces} />
      <Box>
        <Typography variant="caption" sx={{
          color: "text.secondary"
        }}>
          {t('debug.totalTime', { ms: trace.totalTimeMs })}
        </Typography>
      </Box>
    </Stack>
  );
}
