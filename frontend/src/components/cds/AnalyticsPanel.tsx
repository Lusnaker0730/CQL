import {
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
import { useCdsAnalytics } from '../../hooks/useCdsHooks'
import type { CdsServiceAnalytics } from '../../types'
import CardListSkeleton from '../common/CardListSkeleton'

export default function AnalyticsPanel() {
  const { data: analytics, isLoading, isError } = useCdsAnalytics()

  if (isLoading) return <CardListSkeleton />
  if (isError) return <Alert severity="error">Failed to load analytics</Alert>

  if (!analytics || analytics.length === 0) {
    return (
      <Alert severity="info">No analytics data available yet. Invoke CDS services to generate analytics.</Alert>
    )
  }

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell scope="col">Service</TableCell>
            <TableCell scope="col" align="right">Invocations</TableCell>
            <TableCell scope="col" align="right">Errors</TableCell>
            <TableCell scope="col" align="right">Error Rate</TableCell>
            <TableCell scope="col" align="right">Avg Time (ms)</TableCell>
            <TableCell scope="col" align="right">Accepted</TableCell>
            <TableCell scope="col" align="right">Overridden</TableCell>
            <TableCell scope="col">Last Invoked</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {analytics.map((row: CdsServiceAnalytics) => (
            <TableRow key={row.serviceId}>
              <TableCell>{row.serviceId}</TableCell>
              <TableCell align="right">{row.invocationCount}</TableCell>
              <TableCell align="right">{row.errorCount}</TableCell>
              <TableCell
                align="right"
                sx={{ color: row.errorRate > 10 ? 'error.main' : row.errorRate > 5 ? 'warning.main' : 'inherit' }}
              >
                {row.errorRate}%
              </TableCell>
              <TableCell align="right">{row.avgResponseTimeMs}</TableCell>
              <TableCell align="right">{row.feedbackAcceptedCount}</TableCell>
              <TableCell align="right">{row.feedbackOverriddenCount}</TableCell>
              <TableCell>{row.lastInvokedAt ? new Date(row.lastInvokedAt).toLocaleString() : '-'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
