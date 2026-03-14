import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation('cds')

  if (isLoading) return <CardListSkeleton />
  if (isError) return <Alert severity="error">{t('analytics.loadError')}</Alert>

  if (!analytics || analytics.length === 0) {
    return (
      <Alert severity="info">{t('analytics.noData')}</Alert>
    )
  }

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell scope="col">{t('analytics.colService')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colInvocations')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colErrors')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colErrorRate')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colAvgTime')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colAccepted')}</TableCell>
            <TableCell scope="col" align="right">{t('analytics.colOverridden')}</TableCell>
            <TableCell scope="col">{t('analytics.colLastInvoked')}</TableCell>
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
