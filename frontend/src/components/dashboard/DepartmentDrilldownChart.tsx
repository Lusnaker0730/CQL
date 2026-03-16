import { useMemo } from 'react'
import { Box, Typography, Paper } from '@mui/material'
import { CHART_HEIGHT } from '../../constants/layout'
import { getScoreHex } from '../../utils/scoreColors'
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

export default function DepartmentDrilldownChart({ data, title }: DepartmentDrilldownChartProps) {
  const { t } = useTranslation('measures')

  const chartData = useMemo(
    () =>
      Object.entries(data).map(([dept, score]) => ({
        department: dept,
        score,
      })),
    [data]
  )

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {title || t('dashboard.departmentScores')}
      </Typography>
      <Box sx={{ width: '100%', height: CHART_HEIGHT }}>
        <ResponsiveContainer minWidth={0} minHeight={0}>
          <BarChart data={chartData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="department" fontSize={11} />
            <YAxis domain={[0, 100]} fontSize={11} />
            <Tooltip />
            <Bar dataKey="score" radius={[4, 4, 0, 0]}>
              {chartData.map((entry, i) => (
                <Cell key={i} fill={getScoreHex(entry.score)} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </Box>
    </Paper>
  )
}
