import { Box, Typography, Paper } from '@mui/material'
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

interface ScoreTrendChartProps {
  data: TrendSeriesPoint[]
  thresholds?: MeasureThreshold[]
  title?: string
}

const COLORS = ['#0D7377', '#1B3A5C', '#14A3A8', '#E8A838', '#D32F2F', '#7B1FA2']

export default function ScoreTrendChart({ data, thresholds = [], title }: ScoreTrendChartProps) {
  const { t } = useTranslation('measures')

  // Group by measure name for multi-line
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

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.scoreTrend')}
      </Typography>
      <Box sx={{ width: '100%', height: 300 }}>
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
                stroke={COLORS[i % COLORS.length]}
                strokeWidth={2}
                dot={{ r: 3 }}
                connectNulls
              />
            ))}
            {thresholds
              .filter((t) => t.thresholdType === 'target')
              .map((t, i) => (
                <ReferenceLine
                  key={`target-${i}`}
                  y={t.thresholdValue}
                  stroke="#4CAF50"
                  strokeDasharray="5 5"
                  label={{ value: 'Target', fill: '#4CAF50', fontSize: 10 }}
                />
              ))}
            {thresholds
              .filter((t) => t.thresholdType === 'warning')
              .map((t, i) => (
                <ReferenceLine
                  key={`warning-${i}`}
                  y={t.thresholdValue}
                  stroke="#FF9800"
                  strokeDasharray="5 5"
                />
              ))}
          </LineChart>
        </ResponsiveContainer>
      </Box>
    </Paper>
  )
}
