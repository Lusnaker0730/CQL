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
// PAT-151: replaced the local formatScore with the shared dashboardFormat
// helpers so threshold and score columns no longer hard-code "%" — the
// continuous-variable + cohort cases now match what ScoreTrendChart does.
import { formatScoreValue, formatThresholdValue } from '../../utils/dashboardFormat'

interface QualityReportPanelProps {
  report: QualityReportData | null
}

const MEASURE_TABLE_PREVIEW_LIMIT = 10

export default function QualityReportPanel({ report }: QualityReportPanelProps) {
  const { t } = useTranslation('measures')
  const { t: tCommon } = useTranslation('common')

  if (!report) {
    return (
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2">{t('dashboard.qualityReport')}</Typography>
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
            py: 2,
            textAlign: 'center'
          }}>
          {t('dashboard.noReportData')}
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2
        }}>
        <Typography variant="subtitle2">{t('dashboard.qualityReport')}</Typography>
        <Typography variant="caption" sx={{
          color: "text.secondary"
        }}>
          {report.periodLabel}
        </Typography>
      </Stack>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" color="primary">{report.averageScore.toFixed(1)}%</Typography>
          <Typography variant="caption">{t('dashboard.averageScore')}</Typography>
        </Box>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" sx={{
            color: "success.main"
          }}>{report.measuresAboveTarget}</Typography>
          <Typography variant="caption">{t('dashboard.aboveTarget')}</Typography>
        </Box>
        <Box sx={{ textAlign: 'center', flex: 1 }}>
          <Typography variant="h5" sx={{
            color: "error.main"
          }}>{report.measuresBelowTarget}</Typography>
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
            {report.measureScores.slice(0, MEASURE_TABLE_PREVIEW_LIMIT).map((ms) => (
              <TableRow key={ms.measureId}>
                <TableCell>{ms.measureName}</TableCell>
                <TableCell align="right">
                  {formatScoreValue(ms.score, {
                    scoringType: ms.scoringType,
                    naLabel: tCommon('notAvailable'),
                  })}
                </TableCell>
                <TableCell align="right">
                  {/* PAT-151: was hard-coded `${targetThreshold}%`. Continuous-variable
                      thresholds (HbA1c 7.0 mmol/L) and cohort thresholds (raw counts)
                      were rendered as percentages, which is wrong. */}
                  {formatThresholdValue(ms.targetThreshold, { scoringType: ms.scoringType })}
                </TableCell>
                <TableCell>
                  <Chip
                    label={t(`dashboard.status.${ms.status}`, { defaultValue: ms.status })}
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
      {/* PAT-151: previously the table silently truncated to 10 rows; users with
          11+ measures had no idea more existed. Show a truncation footer. */}
      {report.measureScores.length > MEASURE_TABLE_PREVIEW_LIMIT && (
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
            display: 'block',
            textAlign: 'center',
            mt: 1
          }}>
          {t('dashboard.tablePreviewTruncated', {
            shown: MEASURE_TABLE_PREVIEW_LIMIT,
            total: report.measureScores.length,
          })}
        </Typography>
      )}
    </Paper>
  );
}
