import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  Card,
  CardContent,
  LinearProgress,
  Divider,
  Alert,
} from '@mui/material'
import {
  TrendingUp as TrendUpIcon,
  TrendingDown as TrendDownIcon,
  TrendingFlat as TrendFlatIcon,
  CompareArrows as CompareIcon,
  Timeline as TimelineIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureComparisonResult, MeasureTrendResult } from '../../types'
import { getDefaultComparisonPeriods } from '../../utils/dateDefaults'

export default function MeasureComparison() {
  const { t } = useTranslation('measures')
  const [measureName, setMeasureName] = useState('')
  const defaults = getDefaultComparisonPeriods()
  const [p1Start, setP1Start] = useState(defaults.period1Start)
  const [p1End, setP1End] = useState(defaults.period1End)
  const [p2Start, setP2Start] = useState(defaults.period2Start)
  const [p2End, setP2End] = useState(defaults.period2End)
  const [periods, setPeriods] = useState(4)
  const [comparison, setComparison] = useState<MeasureComparisonResult | null>(null)
  const [trend, setTrend] = useState<MeasureTrendResult | null>(null)

  const compareMutation = useMutation({
    mutationFn: () => measureApi.comparePeriods(measureName, p1Start, p1End, p2Start, p2End),
    onSuccess: (data) => setComparison(data),
  })

  const trendMutation = useMutation({
    mutationFn: () => measureApi.getTrend(measureName, periods),
    onSuccess: (data) => setTrend(data),
  })

  const getTrendIcon = (trend: string) => {
    if (trend === 'improving') return <TrendUpIcon sx={{ color: 'success.main' }} />
    if (trend === 'declining') return <TrendDownIcon sx={{ color: 'error.main' }} />
    return <TrendFlatIcon sx={{ color: 'text.secondary' }} />
  }

  const getTrendColor = (trend: string) => {
    if (trend === 'improving') return 'success.main'
    if (trend === 'declining') return 'error.main'
    return 'text.secondary'
  }

  const getScoreColor = (score: number | undefined): string => {
    if (score == null) return 'text.disabled'
    if (score >= 80) return 'success.main'
    if (score >= 60) return 'warning.main'
    return 'error.main'
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>{t('comparison.title')}</Typography>

      <TextField
        label={t('comparison.measureName')}
        size="small"
        fullWidth
        value={measureName}
        onChange={(e) => setMeasureName(e.target.value)}
        sx={{ mb: 2 }}
      />

      {/* Period Comparison */}
      <Typography variant="subtitle2" gutterBottom>{t('comparison.compareTwoPeriods')}</Typography>
      <Stack direction="row" spacing={2} mb={1}>
        <TextField label={t('comparison.period1Start')} type="date" size="small" fullWidth
          value={p1Start} onChange={(e) => setP1Start(e.target.value)}
          InputLabelProps={{ shrink: true }} />
        <TextField label={t('comparison.period1End')} type="date" size="small" fullWidth
          value={p1End} onChange={(e) => setP1End(e.target.value)}
          InputLabelProps={{ shrink: true }} />
      </Stack>
      <Stack direction="row" spacing={2} mb={2}>
        <TextField label={t('comparison.period2Start')} type="date" size="small" fullWidth
          value={p2Start} onChange={(e) => setP2Start(e.target.value)}
          InputLabelProps={{ shrink: true }} />
        <TextField label={t('comparison.period2End')} type="date" size="small" fullWidth
          value={p2End} onChange={(e) => setP2End(e.target.value)}
          InputLabelProps={{ shrink: true }} />
      </Stack>
      <GradientButton
        startIcon={<CompareIcon />}
        onClick={() => compareMutation.mutate()}
        disabled={!measureName || compareMutation.isPending}
        sx={{ mb: 2 }}
      >
        {compareMutation.isPending ? t('comparison.comparing') : t('comparison.compare')}
      </GradientButton>

      {compareMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{(compareMutation.error as Error).message}</Alert>
      )}

      {comparison && (
        <Card variant="outlined" sx={{ mb: 3 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="subtitle1" fontWeight={600}>{comparison.measureName}</Typography>
              <Stack direction="row" alignItems="center" spacing={1}>
                {getTrendIcon(comparison.trend)}
                <Typography variant="body2" sx={{ color: getTrendColor(comparison.trend), fontWeight: 600 }}>
                  {comparison.trend.charAt(0).toUpperCase() + comparison.trend.slice(1)}
                </Typography>
              </Stack>
            </Stack>

            <Stack direction="row" spacing={2} mb={2}>
              <Card variant="outlined" sx={{ flex: 1, p: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{t('comparison.period1')}</Typography>
                <Typography variant="body2">{comparison.period1.periodStart} - {comparison.period1.periodEnd}</Typography>
                <Typography variant="h5" sx={{ color: getScoreColor(comparison.period1.measureScore), fontWeight: 700 }}>
                  {comparison.period1.measureScore != null ? `${comparison.period1.measureScore.toFixed(1)}%` : t('comparison.na')}
                </Typography>
              </Card>
              <Card variant="outlined" sx={{ flex: 1, p: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{t('comparison.period2')}</Typography>
                <Typography variant="body2">{comparison.period2.periodStart} - {comparison.period2.periodEnd}</Typography>
                <Typography variant="h5" sx={{ color: getScoreColor(comparison.period2.measureScore), fontWeight: 700 }}>
                  {comparison.period2.measureScore != null ? `${comparison.period2.measureScore.toFixed(1)}%` : t('comparison.na')}
                </Typography>
              </Card>
            </Stack>

            {comparison.scoreDelta != null && (
              <Stack direction="row" spacing={3} justifyContent="center">
                <Box textAlign="center">
                  <Typography variant="caption" color="text.secondary">{t('comparison.delta')}</Typography>
                  <Typography variant="h6" sx={{
                    color: comparison.scoreDelta >= 0 ? 'success.main' : 'error.main',
                    fontWeight: 600,
                  }}>
                    {comparison.scoreDelta >= 0 ? '+' : ''}{comparison.scoreDelta.toFixed(1)}%
                  </Typography>
                </Box>
                {comparison.scorePercentChange != null && (
                  <Box textAlign="center">
                    <Typography variant="caption" color="text.secondary">{t('comparison.change')}</Typography>
                    <Typography variant="h6" sx={{
                      color: comparison.scorePercentChange >= 0 ? 'success.main' : 'error.main',
                      fontWeight: 600,
                    }}>
                      {comparison.scorePercentChange >= 0 ? '+' : ''}{comparison.scorePercentChange.toFixed(1)}%
                    </Typography>
                  </Box>
                )}
              </Stack>
            )}
          </CardContent>
        </Card>
      )}

      <Divider sx={{ my: 2 }} />

      {/* Trend */}
      <Typography variant="subtitle2" gutterBottom>{t('comparison.scoreTrend')}</Typography>
      <Stack direction="row" spacing={2} mb={2} alignItems="center">
        <TextField label={t('comparison.periods')} type="number" size="small" sx={{ width: 100 }}
          value={periods} onChange={(e) => setPeriods(parseInt(e.target.value) || 4)} />
        <Button
          variant="outlined"
          startIcon={<TimelineIcon />}
          onClick={() => trendMutation.mutate()}
          disabled={!measureName || trendMutation.isPending}
          size="small"
        >
          {trendMutation.isPending ? t('comparison.loading') : t('comparison.loadTrend')}
        </Button>
      </Stack>

      {trendMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{(trendMutation.error as Error).message}</Alert>
      )}

      {trend && trend.dataPoints.length > 0 && (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle2" gutterBottom>{t('comparison.scoreProgression', { name: trend.measureName })}</Typography>
            <Stack spacing={1.5}>
              {trend.dataPoints.map((dp, idx) => {
                const maxScore = Math.max(...trend.dataPoints.filter(d => d.score != null).map(d => d.score!), 100)
                return (
                  <Box key={idx}>
                    <Stack direction="row" justifyContent="space-between" mb={0.5}>
                      <Typography variant="caption">{dp.periodStart} - {dp.periodEnd}</Typography>
                      <Typography variant="caption" fontWeight={600}
                        sx={{ color: getScoreColor(dp.score) }}>
                        {dp.score != null ? `${dp.score.toFixed(1)}%` : t('comparison.na')}
                      </Typography>
                    </Stack>
                    <LinearProgress
                      variant="determinate"
                      value={dp.score != null ? (dp.score / maxScore) * 100 : 0}
                      sx={{
                        height: 8,
                        borderRadius: 4,
                        bgcolor: (theme) => `${theme.palette.primary.main}14`,
                        '& .MuiLinearProgress-bar': {
                          bgcolor: getScoreColor(dp.score),
                          borderRadius: 4,
                        },
                      }}
                    />
                  </Box>
                )
              })}
            </Stack>
          </CardContent>
        </Card>
      )}

      {trend && trend.dataPoints.length === 0 && (
        <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ py: 2 }}>
          {t('comparison.noReportData')}
        </Typography>
      )}
    </Paper>
  )
}
