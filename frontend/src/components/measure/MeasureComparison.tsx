import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getScoreThemeColor } from '../../utils/scoreColors'
import { extractApiError } from '../../utils/errorUtils'
import {
  Autocomplete,
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
  MenuItem,
} from '@mui/material'
import {
  TrendingUp as TrendUpIcon,
  TrendingDown as TrendDownIcon,
  TrendingFlat as TrendFlatIcon,
  CompareArrows as CompareIcon,
  Timeline as TimelineIcon,
  PlayArrow as PlayIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation, useQuery } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureComparisonResult, MeasureTrendResult, TrendDataPoint } from '../../types'
import {
  getDefaultComparisonPeriods,
  computeTrendSlots,
  type TrendInterval,
} from '../../utils/dateDefaults'

interface MeasureOption {
  id: number
  label: string
}

export default function MeasureComparison() {
  const { t } = useTranslation('measures')
  const [selectedMeasure, setSelectedMeasure] = useState<MeasureOption | null>(null)

  const { data: measures = [] } = useQuery({
    queryKey: ['measures'],
    queryFn: () => measureApi.getMeasures(),
    staleTime: Infinity,
  })
  const measureOptions = useMemo<MeasureOption[]>(
    () => measures.map((m) => ({ id: m.id!, label: m.title || m.name })),
    [measures],
  )

  const defaults = getDefaultComparisonPeriods()
  const [p1Start, setP1Start] = useState(defaults.period1Start)
  const [p1End, setP1End] = useState(defaults.period1End)
  const [p2Start, setP2Start] = useState(defaults.period2Start)
  const [p2End, setP2End] = useState(defaults.period2End)
  const [periods, setPeriods] = useState(4)
  const [comparison, setComparison] = useState<MeasureComparisonResult | null>(null)
  const [trend, setTrend] = useState<MeasureTrendResult | null>(null)

  const [liveCompareStep, setLiveCompareStep] = useState<0 | 1 | 2 | 3>(0)
  const [liveCompareError, setLiveCompareError] = useState<string | null>(null)

  const today = new Date().toISOString().slice(0, 10)
  const [trendEndDate, setTrendEndDate] = useState(today)
  const [trendInterval, setTrendInterval] = useState<TrendInterval>('monthly')
  const [liveTrendStep, setLiveTrendStep] = useState<number>(0)
  const [liveTrendError, setLiveTrendError] = useState<string | null>(null)

  const compareMutation = useMutation({
    mutationFn: () => measureApi.comparePeriods(selectedMeasure!.id, selectedMeasure!.label, p1Start, p1End, p2Start, p2End),
    onSuccess: (data) => setComparison(data),
  })

  const trendMutation = useMutation({
    mutationFn: () => measureApi.getTrend(selectedMeasure!.id, selectedMeasure!.label, periods),
    onSuccess: (data) => setTrend(data),
  })

  const runLiveCompare = async () => {
    if (!selectedMeasure) return
    setLiveCompareError(null)
    setComparison(null)
    try {
      setLiveCompareStep(1)
      await measureApi.evaluateMeasure(String(selectedMeasure.id), undefined, p1Start, p1End)
      setLiveCompareStep(2)
      await measureApi.evaluateMeasure(String(selectedMeasure.id), undefined, p2Start, p2End)
      setLiveCompareStep(3)
      const result = await measureApi.comparePeriods(
        selectedMeasure.id,
        selectedMeasure.label,
        p1Start,
        p1End,
        p2Start,
        p2End,
      )
      setComparison(result)
    } catch (err) {
      setLiveCompareError(extractApiError(err))
    } finally {
      setLiveCompareStep(0)
    }
  }

  const runLiveTrend = async () => {
    if (!selectedMeasure) return
    setLiveTrendError(null)
    setTrend(null)
    const slots = computeTrendSlots(trendEndDate, Math.max(1, Math.min(periods, 12)), trendInterval)
    const dataPoints: TrendDataPoint[] = []
    try {
      for (let i = 0; i < slots.length; i++) {
        setLiveTrendStep(i + 1)
        const slot = slots[i]
        const result = await measureApi.evaluateMeasure(
          String(selectedMeasure.id),
          undefined,
          slot.periodStart,
          slot.periodEnd,
        )
        const score = result.groups?.[0]?.measureScore
        dataPoints.push({
          periodStart: slot.periodStart,
          periodEnd: slot.periodEnd,
          score: score != null ? score : undefined,
        })
      }
      setTrend({ measureName: selectedMeasure.label, dataPoints })
    } catch (err) {
      setLiveTrendError(
        t('comparison.slotFailed', { current: liveTrendStep, error: extractApiError(err) }),
      )
      if (dataPoints.length > 0) {
        setTrend({ measureName: selectedMeasure.label, dataPoints })
      }
    } finally {
      setLiveTrendStep(0)
    }
  }

  const getTrendIcon = (trendValue: string) => {
    if (trendValue === 'improving') return <TrendUpIcon sx={{ color: 'success.main' }} />
    if (trendValue === 'declining') return <TrendDownIcon sx={{ color: 'error.main' }} />
    return <TrendFlatIcon sx={{ color: 'text.secondary' }} />
  }

  const getTrendColor = (trendValue: string) => {
    if (trendValue === 'improving') return 'success.main'
    if (trendValue === 'declining') return 'error.main'
    return 'text.secondary'
  }

  const liveCompareRunning = liveCompareStep > 0
  const liveTrendRunning = liveTrendStep > 0
  const slotCount = Math.max(1, Math.min(periods, 12))

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>{t('comparison.title')}</Typography>

      <Autocomplete
        options={measureOptions}
        getOptionLabel={(opt) => opt.label}
        value={selectedMeasure}
        onChange={(_e, value) => setSelectedMeasure(value)}
        isOptionEqualToValue={(opt, val) => opt.id === val.id}
        renderInput={(params) => (
          <TextField {...params} label={t('comparison.measureName')} size="small" />
        )}
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
      <Stack direction="row" spacing={1} mb={1} flexWrap="wrap" useFlexGap>
        <Button
          variant="outlined"
          startIcon={<CompareIcon />}
          onClick={() => compareMutation.mutate()}
          disabled={!selectedMeasure || compareMutation.isPending || liveCompareRunning}
          size="small"
        >
          {compareMutation.isPending ? t('comparison.comparing') : t('comparison.compare')}
        </Button>
        <GradientButton
          startIcon={<PlayIcon />}
          onClick={runLiveCompare}
          disabled={!selectedMeasure || liveCompareRunning || compareMutation.isPending}
        >
          {liveCompareRunning
            ? liveCompareStep === 3
              ? t('comparison.comparingLive')
              : t('comparison.evaluatingPeriod', { current: liveCompareStep, total: 2 })
            : t('comparison.compareLive')}
        </GradientButton>
      </Stack>
      <Stack spacing={0.5} mb={2}>
        <Typography variant="caption" color="text.secondary">{t('comparison.compareHint')}</Typography>
        <Typography variant="caption" color="text.secondary">{t('comparison.compareLiveHint')}</Typography>
      </Stack>

      {liveCompareRunning && (
        <LinearProgress sx={{ mb: 2 }} />
      )}

      {liveCompareError && (
        <Alert severity="error" sx={{ mb: 2 }}>{liveCompareError}</Alert>
      )}

      {compareMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{extractApiError(compareMutation.error)}</Alert>
      )}

      {comparison && comparison.period1.measureScore == null && comparison.period2.measureScore == null && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('comparison.noReportData')}
        </Alert>
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
                <Typography variant="h5" sx={{ color: getScoreThemeColor(comparison.period1.measureScore), fontWeight: 700 }}>
                  {comparison.period1.measureScore != null ? `${comparison.period1.measureScore.toFixed(1)}%` : t('comparison.na')}
                </Typography>
              </Card>
              <Card variant="outlined" sx={{ flex: 1, p: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{t('comparison.period2')}</Typography>
                <Typography variant="body2">{comparison.period2.periodStart} - {comparison.period2.periodEnd}</Typography>
                <Typography variant="h5" sx={{ color: getScoreThemeColor(comparison.period2.measureScore), fontWeight: 700 }}>
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

      {/* Trend — historical */}
      <Typography variant="subtitle2" gutterBottom>{t('comparison.scoreTrend')}</Typography>
      <Stack direction="row" spacing={2} mb={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <TextField label={t('comparison.periods')} type="number" size="small" sx={{ width: 110 }}
          value={periods}
          onChange={(e) => setPeriods(parseInt(e.target.value) || 4)}
          inputProps={{ min: 1, max: 12 }} />
        <Button
          variant="outlined"
          startIcon={<TimelineIcon />}
          onClick={() => trendMutation.mutate()}
          disabled={!selectedMeasure || trendMutation.isPending || liveTrendRunning}
          size="small"
        >
          {trendMutation.isPending ? t('comparison.loading') : t('comparison.loadTrend')}
        </Button>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
        {t('comparison.loadTrendHint')}
      </Typography>

      {/* Trend — live */}
      <Typography variant="subtitle2" gutterBottom>{t('comparison.trendLiveSection')}</Typography>
      <Stack direction="row" spacing={2} mb={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <TextField
          label={t('comparison.trendEndDate')}
          type="date"
          size="small"
          sx={{ width: 170 }}
          value={trendEndDate}
          onChange={(e) => setTrendEndDate(e.target.value)}
          InputLabelProps={{ shrink: true }}
        />
        <TextField
          select
          label={t('comparison.trendInterval')}
          size="small"
          sx={{ width: 140 }}
          value={trendInterval}
          onChange={(e) => setTrendInterval(e.target.value as TrendInterval)}
        >
          <MenuItem value="monthly">{t('comparison.trendIntervals.monthly')}</MenuItem>
          <MenuItem value="quarterly">{t('comparison.trendIntervals.quarterly')}</MenuItem>
          <MenuItem value="yearly">{t('comparison.trendIntervals.yearly')}</MenuItem>
        </TextField>
        <GradientButton
          startIcon={<PlayIcon />}
          onClick={runLiveTrend}
          disabled={!selectedMeasure || liveTrendRunning || trendMutation.isPending}
        >
          {liveTrendRunning
            ? t('comparison.runningLiveTrend', { current: liveTrendStep, total: slotCount })
            : t('comparison.runLiveTrend')}
        </GradientButton>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        {t('comparison.trendLiveHint')}
      </Typography>
      <Typography variant="caption" color="warning.main" sx={{ display: 'block', mb: 2 }}>
        {t('comparison.liveTrendNote', { count: slotCount })}
      </Typography>

      {liveTrendRunning && (
        <LinearProgress
          variant="determinate"
          value={((liveTrendStep - 1) / slotCount) * 100}
          sx={{ mb: 2 }}
        />
      )}

      {liveTrendError && (
        <Alert severity="error" sx={{ mb: 2 }}>{liveTrendError}</Alert>
      )}

      {trendMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{extractApiError(trendMutation.error)}</Alert>
      )}

      {trend && trend.dataPoints.length > 0 && (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle2" gutterBottom>{t('comparison.scoreProgression', { name: trend.measureName })}</Typography>
            <Stack spacing={1.5}>
              {(() => {
                const maxScore = Math.max(...trend.dataPoints.filter(d => d.score != null).map(d => d.score!), 100)
                return trend.dataPoints.map((dp, idx) => (
                  <Box key={idx}>
                    <Stack direction="row" justifyContent="space-between" mb={0.5}>
                      <Typography variant="caption">{dp.periodStart} - {dp.periodEnd}</Typography>
                      <Typography variant="caption" fontWeight={600}
                        sx={{ color: getScoreThemeColor(dp.score) }}>
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
                          bgcolor: getScoreThemeColor(dp.score),
                          borderRadius: 4,
                        },
                      }}
                    />
                  </Box>
                ))
              })()}
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
