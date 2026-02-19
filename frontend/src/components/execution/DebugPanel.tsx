import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  LinearProgress,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { DebugTrace } from '../../types'

interface DebugPanelProps {
  trace: DebugTrace
}

export default function DebugPanel({ trace }: DebugPanelProps) {
  const { t } = useTranslation('editor')
  const maxExprTime = Math.max(
    ...trace.expressionTraces.map((t) => t.evaluationTimeMs),
    1
  )
  const maxRetrieveTime = Math.max(
    ...trace.retrieveTraces.map((t) => t.retrieveTimeMs),
    1
  )

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'secondary.main' }}>
        {t('debug.expressionTrace')}
      </Typography>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col">{t('debug.index')}</TableCell>
              <TableCell scope="col">{t('debug.expression')}</TableCell>
              <TableCell scope="col">{t('debug.result')}</TableCell>
              <TableCell scope="col">{t('debug.type')}</TableCell>
              <TableCell scope="col" width={160}>{t('debug.time')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {trace.expressionTraces
              .sort((a, b) => a.order - b.order)
              .map((et) => (
                <TableRow key={et.name} hover>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {et.order + 1}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {et.name}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography
                      variant="body2"
                      sx={{
                        maxWidth: 200,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {et.resultDisplay}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={et.resultType}
                      size="small"
                      variant="outlined"
                      sx={{ borderColor: 'rgba(27,58,92,0.3)', color: 'secondary.main' }}
                    />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress
                        variant="determinate"
                        value={(et.evaluationTimeMs / maxExprTime) * 100}
                        sx={{
                          flexGrow: 1,
                          height: 6,
                          borderRadius: 3,
                          bgcolor: 'rgba(13,115,119,0.08)',
                          '& .MuiLinearProgress-bar': {
                            bgcolor: et.evaluationTimeMs > maxExprTime * 0.7
                              ? '#e65100'
                              : 'primary.main',
                            borderRadius: 3,
                          },
                        }}
                      />
                      <Typography variant="caption" sx={{ minWidth: 40, textAlign: 'right' }}>
                        {et.evaluationTimeMs}ms
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>
      </TableContainer>

      {trace.retrieveTraces.length > 0 && (
        <>
          <Typography
            variant="subtitle2"
            sx={{ mt: 2, mb: 1, fontWeight: 600, color: 'secondary.main' }}
          >
            {t('debug.fhirRetrieveTrace')}
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell scope="col">{t('debug.resourceType')}</TableCell>
                  <TableCell scope="col">{t('debug.count')}</TableCell>
                  <TableCell scope="col" width={160}>{t('debug.time')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {trace.retrieveTraces.map((rt, i) => (
                  <TableRow key={`${rt.resourceType}-${i}`} hover>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {rt.resourceType}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={rt.resourceCount}
                        size="small"
                        sx={{
                          bgcolor: 'rgba(13,115,119,0.08)',
                          color: 'primary.dark',
                          fontWeight: 600,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <LinearProgress
                          variant="determinate"
                          value={(rt.retrieveTimeMs / maxRetrieveTime) * 100}
                          sx={{
                            flexGrow: 1,
                            height: 6,
                            borderRadius: 3,
                            bgcolor: 'rgba(27,58,92,0.08)',
                            '& .MuiLinearProgress-bar': {
                              bgcolor: 'secondary.main',
                              borderRadius: 3,
                            },
                          }}
                        />
                        <Typography variant="caption" sx={{ minWidth: 40, textAlign: 'right' }}>
                          {rt.retrieveTimeMs}ms
                        </Typography>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
        {t('debug.totalTime', { ms: trace.totalTimeMs })}
      </Typography>
    </Box>
  )
}
