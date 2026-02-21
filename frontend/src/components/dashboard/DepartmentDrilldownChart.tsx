import { Box, Typography, Paper } from '@mui/material'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import { useTranslation } from 'react-i18next'

interface DepartmentDrilldownChartProps {
  data: Record<string, number>
  title?: string
}

function getScoreColor(score: number): string {
  if (score >= 80) return '#4CAF50'
  if (score >= 60) return '#FF9800'
  return '#D32F2F'
}

export default function DepartmentDrilldownChart({ data, title }: DepartmentDrilldownChartProps) {
  const { t } = useTranslation('measures')

  const chartData = Object.entries(data).map(([dept, score]) => ({
    department: dept,
    score,
  }))

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.departmentScores')}
      </Typography>
      <Box sx={{ width: '100%', height: 300 }}>
        <ResponsiveContainer>
          <BarChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="department" fontSize={11} />
            <YAxis domain={[0, 100]} fontSize={11} />
            <Tooltip />
            <Bar dataKey="score" radius={[4, 4, 0, 0]}>
              {chartData.map((entry, i) => (
                <Cell key={i} fill={getScoreColor(entry.score)} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </Box>
    </Paper>
  )
}
