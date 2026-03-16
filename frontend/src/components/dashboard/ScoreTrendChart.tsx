import { useMemo } from 'react'
import { Box, Typography, Paper } from '@mui/material'
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

interface ScoreTrendChartProps {
  data: TrendSeriesPoint[]
  thresholds?: MeasureThreshold[]
  title?: string
}

export default function ScoreTrendChart({ data, thresholds = [], title }: ScoreTrendChartProps) {
  const { t } = useTranslation('measures')
  const theme = useTheme()

  const { measureNames, chartData } = useMemo(() => {
    const measureNames = [...new Set(data.map((d) => d.measureName))]
    const periods = [...new Set(data.map((d) => d.period))]

    const chartData = periods.map((period) => {
      const point: Record<string, unknown> = { period }
      for (const name of measureNames) {
        const match = data.find((d) => d.period === period && d.measureName === name)
        point[name] = match?.score ?? null
      }
      return point
    })

    return { measureNames, chartData }
  }, [data])

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.scoreTrend')}
      </Typography>
      <Box sx={{ width: '100%', height: CHART_HEIGHT }}>
        <ResponsiveContainer minWidth={0} minHeight={0}>
          <LineChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="period" fontSize={11} />
            <YAxis domain={[0, 100]} fontSize={11} />
            <Tooltip />
            <Legend />
            {measureNames.map((name, i) => (
              <Line
                key={name}
                type="monotone"
                dataKey={name}
                stroke={CHART_COLORS[i % CHART_COLORS.length]}
                strokeWidth={2}
                dot={{ r: 3 }}
                connectNulls
              />
            ))}
            {thresholds
              .filter((th) => th.thresholdType === 'target')
              .map((th, i) => (
                <ReferenceLine
                  key={`target-${i}`}
                  y={th.thresholdValue}
                  stroke={theme.palette.success.light}
                  strokeDasharray="5 5"
                  label={{ value: t('dashboard.target'), fill: theme.palette.success.light, fontSize: 10 }}
                />
              ))}
            {thresholds
              .filter((th) => th.thresholdType === 'warning')
              .map((th, i) => (
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
    </Paper>
  )
}
