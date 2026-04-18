import { useMemo, useState } from 'react'
import {
  Box,
  Typography,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Chip,
} from '@mui/material'
import ViewModuleIcon from '@mui/icons-material/ViewModule'
import TimelineIcon from '@mui/icons-material/Timeline'
import { useTheme } from '@mui/material/styles'
import { CHART_HEIGHT } from '../../constants/layout'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { TrendSeriesPoint, MeasureThreshold } from '../../types'
import { CHART_COLORS } from '../../constants/chartColors'

type ChartMode = 'multiples' | 'overlay'

interface ScoreTrendChartProps {
  data: TrendSeriesPoint[]
  thresholds?: MeasureThreshold[]
  title?: string
}

interface ChartDataPoint {
  period: string
  [measureName: string]: string | number | null
}

const SMALL_CHART_HEIGHT = 160

export default function ScoreTrendChart({ data, thresholds = [], title }: ScoreTrendChartProps) {
  const { t } = useTranslation('measures')
  const theme = useTheme()

  const [mode, setMode] = useState<ChartMode>('multiples')

  const { measureNames, chartData } = useMemo(() => {
    const names = [...new Set(data.map((d) => d.measureName))]
    const periods = [...new Set(data.map((d) => d.period))]

    const rows: ChartDataPoint[] = periods.map((period) => {
      const point: ChartDataPoint = { period }
      for (const name of names) {
        const match = data.find((d) => d.period === period && d.measureName === name)
        point[name] = match?.score ?? null
      }
      return point
    })

    return { measureNames: names, chartData: rows }
  }, [data])

  const [selected, setSelected] = useState<Set<string>>(() => new Set(measureNames))

  const effectiveSelected = useMemo(() => {
    const filtered = measureNames.filter((n) => selected.has(n))
    return filtered.length > 0 ? filtered : measureNames
  }, [measureNames, selected])

  const handleModeChange = (_: React.MouseEvent<HTMLElement>, next: ChartMode | null) => {
    if (next) setMode(next)
  }

  const toggleMeasure = (name: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  const targetLines = thresholds.filter((th) => th.thresholdType === 'target')
  const warningLines = thresholds.filter((th) => th.thresholdType === 'warning')

  const colorFor = (name: string) =>
    CHART_COLORS[measureNames.indexOf(name) % CHART_COLORS.length]

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="subtitle2">{title || t('dashboard.scoreTrend')}</Typography>
        <ToggleButtonGroup
          size="small"
          value={mode}
          exclusive
          onChange={handleModeChange}
          aria-label={t('dashboard.chartMode.label')}
        >
          <ToggleButton value="multiples" aria-label={t('dashboard.chartMode.multiples')}>
            <ViewModuleIcon fontSize="small" sx={{ mr: 0.5 }} />
            {t('dashboard.chartMode.multiples')}
          </ToggleButton>
          <ToggleButton value="overlay" aria-label={t('dashboard.chartMode.overlay')}>
            <TimelineIcon fontSize="small" sx={{ mr: 0.5 }} />
            {t('dashboard.chartMode.overlay')}
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {mode === 'multiples' ? (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: {
              xs: '1fr',
              sm: 'repeat(2, 1fr)',
              md: 'repeat(3, 1fr)',
            },
            gap: 2,
          }}
        >
          {measureNames.map((name) => (
            <Box
              key={name}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                p: 1,
              }}
            >
              <Typography
                variant="caption"
                sx={{ fontWeight: 600, color: colorFor(name), display: 'block', mb: 0.5 }}
                noWrap
                title={name}
              >
                {name}
              </Typography>
              <Box sx={{ width: '100%', height: SMALL_CHART_HEIGHT }}>
                <ResponsiveContainer minWidth={0} minHeight={0}>
                  <LineChart
                    data={chartData}
                    margin={{ top: 4, right: 8, bottom: 4, left: 0 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="period" fontSize={10} tick={{ fontSize: 10 }} />
                    <YAxis fontSize={10} tick={{ fontSize: 10 }} width={32} />
                    <Tooltip />
                    <Line
                      type="monotone"
                      dataKey={name}
                      stroke={colorFor(name)}
                      strokeWidth={2}
                      dot={{ r: 2 }}
                      connectNulls
                      isAnimationActive={false}
                    />
                    {targetLines.map((th, i) => (
                      <ReferenceLine
                        key={`t-${i}`}
                        y={th.thresholdValue}
                        stroke={theme.palette.success.light}
                        strokeDasharray="5 5"
                      />
                    ))}
                    {warningLines.map((th, i) => (
                      <ReferenceLine
                        key={`w-${i}`}
                        y={th.thresholdValue}
                        stroke={theme.palette.warning.light}
                        strokeDasharray="5 5"
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </Box>
            </Box>
          ))}
        </Box>
      ) : (
        <>
          {measureNames.length > 1 && (
            <Stack
              direction="row"
              spacing={0.5}
              sx={{ flexWrap: 'wrap', gap: 0.5, mb: 1 }}
            >
              {measureNames.map((name) => {
                const active = selected.has(name)
                return (
                  <Chip
                    key={name}
                    label={name}
                    size="small"
                    clickable
                    onClick={() => toggleMeasure(name)}
                    variant={active ? 'filled' : 'outlined'}
                    sx={{
                      borderColor: colorFor(name),
                      color: active ? '#fff' : colorFor(name),
                      backgroundColor: active ? colorFor(name) : 'transparent',
                      '&:hover': {
                        backgroundColor: active ? colorFor(name) : `${colorFor(name)}22`,
                      },
                    }}
                  />
                )
              })}
            </Stack>
          )}
          <Box sx={{ width: '100%', height: CHART_HEIGHT }}>
            <ResponsiveContainer minWidth={0} minHeight={0}>
              <LineChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="period" fontSize={11} />
                <YAxis domain={[0, 100]} fontSize={11} />
                <Tooltip />
                <Legend />
                {effectiveSelected.map((name) => (
                  <Line
                    key={name}
                    type="monotone"
                    dataKey={name}
                    stroke={colorFor(name)}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    connectNulls
                  />
                ))}
                {targetLines.map((th, i) => (
                  <ReferenceLine
                    key={`target-${i}`}
                    y={th.thresholdValue}
                    stroke={theme.palette.success.light}
                    strokeDasharray="5 5"
                    label={{
                      value: t('dashboard.target'),
                      fill: theme.palette.success.light,
                      fontSize: 10,
                    }}
                  />
                ))}
                {warningLines.map((th, i) => (
                  <ReferenceLine
                    key={`warning-${i}`}
                    y={th.thresholdValue}
                    stroke={theme.palette.warning.light}
                    strokeDasharray="5 5"
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </Box>
        </>
      )}
    </Paper>
  )
}
