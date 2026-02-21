import { Box, Typography, Paper } from '@mui/material'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { useTranslation } from 'react-i18next'

interface ScoreDistributionChartProps {
  data: Record<string, number>
  title?: string
}

const COLORS = ['#0D7377', '#1B3A5C', '#14A3A8', '#E8A838', '#D32F2F', '#7B1FA2', '#00BCD4']

export default function ScoreDistributionChart({ data, title }: ScoreDistributionChartProps) {
  const { t } = useTranslation('measures')

  const chartData = Object.entries(data)
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name, value }))

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.scoringDistribution')}
      </Typography>
      <Box sx={{ width: '100%', height: 300 }}>
        <ResponsiveContainer>
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
                <Cell key={i} fill={COLORS[i % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      </Box>
    </Paper>
  )
}
