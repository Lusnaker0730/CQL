import {
  Box,
  Typography,
  Paper,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Chip,
  Stack,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { QualityReportData } from '../../types'

interface QualityReportPanelProps {
  report: QualityReportData | null
}

export default function QualityReportPanel({ report }: QualityReportPanelProps) {
  const { t } = useTranslation('measures')

  if (!report) {
    return (
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2">{t('dashboard.qualityReport')}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          {t('dashboard.noReportData')}
        </Typography>
      </Paper>
    )
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="subtitle2">{t('dashboard.qualityReport')}</Typography>
        <Typography variant="caption" color="text.secondary">
          {report.periodLabel}
        </Typography>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" color="primary">{report.averageScore.toFixed(1)}%</Typography>
          <Typography variant="caption">{t('dashboard.averageScore')}</Typography>
        </Box>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" color="success.main">{report.measuresAboveTarget}</Typography>
          <Typography variant="caption">{t('dashboard.aboveTarget')}</Typography>
        </Box>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" color="error.main">{report.measuresBelowTarget}</Typography>
          <Typography variant="caption">{t('dashboard.belowTarget')}</Typography>
        </Box>
      </Stack>

      {report.measureScores.length > 0 && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('dashboard.tableHeaders.measure')}</TableCell>
              <TableCell align="right">{t('dashboard.tableHeaders.score')}</TableCell>
              <TableCell align="right">{t('dashboard.tableHeaders.target')}</TableCell>
              <TableCell>{t('dashboard.tableHeaders.status')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {report.measureScores.slice(0, 10).map((ms) => (
              <TableRow key={ms.measureId}>
                <TableCell>{ms.measureName}</TableCell>
                <TableCell align="right">
                  {ms.score != null ? `${ms.score.toFixed(1)}%` : 'N/A'}
                </TableCell>
                <TableCell align="right">
                  {ms.targetThreshold != null ? `${ms.targetThreshold}%` : '-'}
                </TableCell>
                <TableCell>
                  <Chip
                    label={ms.status.replace('_', ' ')}
                    size="small"
                    color={
                      ms.status === 'above_target'
                        ? 'success'
                        : ms.status === 'below_target'
                          ? 'error'
                          : 'default'
                    }
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Paper>
  )
}
