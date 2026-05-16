import { useMemo } from 'react'
import { Box, Typography, Paper } from '@mui/material'
import { CHART_HEIGHT } from '../../constants/layout'
import { PieChart, Pie, Cell, Tooltip, Legend } from 'recharts'
import SafeResponsiveContainer from '../common/SafeResponsiveContainer'
import { useTranslation } from 'react-i18next'
import { CHART_COLORS } from '../../constants/chartColors'

interface ScoreDistributionChartProps {
  data: Record<string, number>
  title?: string
}

export default function ScoreDistributionChart({ data, title }: ScoreDistributionChartProps) {
  const { t } = useTranslation('measures')

  const chartData = useMemo(
    () =>
      Object.entries(data)
        .filter(([, v]) => v > 0)
        .map(([name, value]) => ({ name, value })),
    [data]
  )

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.scoringDistribution')}
      </Typography>
      <Box sx={{ width: '100%', height: CHART_HEIGHT }}>
        <SafeResponsiveContainer>
          <PieChart>
            <Pie
              data={chartData}
              cx="50%"
              cy="50%"
              outerRadius={100}
              dataKey="value"
              label={({ name, value }) => `${name}: ${value}`}
              labelLine={false}
              fontSize={11}
            >
              {chartData.map((_, i) => (
                <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        </SafeResponsiveContainer>
      </Box>
    </Paper>
  )
}
