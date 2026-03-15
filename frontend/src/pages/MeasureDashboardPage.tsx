import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getScoreChipColor } from '../utils/scoreColors'
import {
  Paper,
  Typography,
  Grid,
  Box,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Stack,
  Alert,
} from '@mui/material'
import {
  Dashboard as DashboardIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material'
import { measureApi } from '../api'
import type { DashboardSummary } from '../types'
import StatusChip from '../components/common/StatusChip'
import DashboardSkeleton from '../components/common/DashboardSkeleton'
import DashboardFilterBar from '../components/dashboard/DashboardFilterBar'
import ScoreTrendChart from '../components/dashboard/ScoreTrendChart'
import DepartmentDrilldownChart from '../components/dashboard/DepartmentDrilldownChart'
import ScoreDistributionChart from '../components/dashboard/ScoreDistributionChart'
import ThresholdAlertPanel from '../components/dashboard/ThresholdAlertPanel'
import QualityReportPanel from '../components/dashboard/QualityReportPanel'
import { STALE_30S, STALE_1M } from '../constants/queryConstants'

const TEAL = '#0D7377'

const STATUS_ORDER = ['active', 'draft', 'retired', 'in-review'] as const

function formatDate(dateStr?: string): string {
  if (!dateStr) return '--'
  try {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return dateStr
  }
}

function formatScoreLabel(score?: number, naLabel = 'N/A'): string {
  if (score == null) return naLabel
  return `${score.toFixed(1)}%`
}

function formatStatusLabel(key: string, inReviewLabel = 'In Review'): string {
  if (key === 'in-review') return inReviewLabel
  return key.charAt(0).toUpperCase() + key.slice(1)
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function OverviewCards({ data }: { data: DashboardSummary }) {
  const { t } = useTranslation('measures')
  const cards = [
    { label: t('dashboard.totalMeasures'), value: data.totalMeasures, color: TEAL },
    ...STATUS_ORDER.map((status) => ({
      label: formatStatusLabel(status, t('dashboard.inReview')),
      value: data.byStatus[status] ?? 0,
      color:
        status === 'active'
          ? '#2e7d32'
          : status === 'draft'
            ? '#757575'
            : status === 'retired'
              ? '#ed6c02'
              : '#0288d1',
    })),
  ]

  return (
    <Grid container spacing={2}>
      {cards.map((card) => (
        <Grid size={{ xs: 12, sm: 6, md: 'grow' }} key={card.label}>
          <Paper
            elevation={0}
            sx={{
              p: 2.5,
              textAlign: 'center',
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'divider',
              transition: 'box-shadow 0.2s',
              '&:hover': { boxShadow: 3 },
            }}
          >
            <Typography
              variant="h4"
              sx={{ fontWeight: 700, color: card.color, mb: 0.5 }}
            >
              {card.value}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {card.label}
            </Typography>
          </Paper>
        </Grid>
      ))}
    </Grid>
  )
}

function RecentEvaluationsTable({
  evaluations,
}: {
  evaluations: DashboardSummary['recentEvaluations']
}) {
  const { t } = useTranslation('measures')
  const rows = evaluations.slice(0, 10)

  return (
    <Paper
      elevation={0}
      sx={{
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        overflow: 'hidden',
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{ px: 3, pt: 2.5, pb: 1.5 }}
      >
        <TrendingUpIcon sx={{ color: TEAL, fontSize: 22 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          {t('dashboard.recentEvaluations')}
        </Typography>
      </Stack>

      {rows.length === 0 ? (
        <Box sx={{ px: 3, pb: 3 }}>
          <Typography variant="body2" color="text.secondary">
            {t('dashboard.noRecentEvaluations')}
          </Typography>
        </Box>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('dashboard.tableHeaders.measure')}</TableCell>
                <TableCell scope="col" sx={{ fontWeight: 600 }} align="right">
                  {t('dashboard.tableHeaders.score')}
                </TableCell>
                <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('dashboard.tableHeaders.status')}</TableCell>
                <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('dashboard.tableHeaders.date')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((ev) => (
                <TableRow
                  key={ev.id}
                  sx={{ '&:last-child td': { borderBottom: 0 } }}
                >
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {ev.measureName}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Chip
                      label={formatScoreLabel(ev.score, t('dashboard.na'))}
                      size="small"
                      color={getScoreChipColor(ev.score)}
                      variant="outlined"
                      sx={{ fontWeight: 600, minWidth: 64 }}
                    />
                  </TableCell>
                  <TableCell>
                    {ev.status ? (
                      <StatusChip status={ev.status} size="small" />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        --
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(ev.createdAt)}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  )
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function MeasureDashboardPage() {
  const { t } = useTranslation('measures')
  const [department, setDepartment] = useState('')
  const [periodType, setPeriodType] = useState('monthly')

  // Original dashboard data
  const {
    data,
    isLoading,
    isError,
    error,
  } = useQuery<DashboardSummary>({
    queryKey: ['measure-dashboard'],
    queryFn: measureApi.getDashboard,
    staleTime: STALE_30S,
  })

  // Enhanced dashboard data
  const { data: enhancedData } = useQuery({
    queryKey: ['enhanced-dashboard', department],
    queryFn: () => measureApi.getEnhancedDashboard(department || undefined),
    staleTime: STALE_30S,
  })

  // Trends
  const { data: trendData = [] } = useQuery({
    queryKey: ['dashboard-trends', periodType],
    queryFn: () => measureApi.getDashboardTrends(undefined, periodType, 12),
    staleTime: STALE_1M,
  })

  // Alerts
  const { data: alerts = [] } = useQuery({
    queryKey: ['dashboard-alerts', department],
    queryFn: () => measureApi.getDashboardAlerts(department || undefined),
    staleTime: STALE_30S,
  })

  // Quality report
  const { data: qualityReport = null } = useQuery({
    queryKey: ['quality-report', periodType, department],
    queryFn: () => measureApi.getQualityReport(periodType, department || undefined),
    staleTime: STALE_1M,
  })

  if (isLoading) {
    return <DashboardSkeleton />
  }

  if (isError) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">
          {t('dashboard.loadError', { error: error instanceof Error ? error.message : t('dashboard.unknownError') })}
        </Alert>
      </Box>
    )
  }

  if (!data) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="info">{t('dashboard.noData')}</Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1400, mx: 'auto' }}>
      {/* Header */}
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 2 }}>
        <DashboardIcon sx={{ color: TEAL, fontSize: 28 }} />
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {t('dashboard.title')}
        </Typography>
      </Stack>

      {/* Filter bar */}
      <DashboardFilterBar
        department={department}
        onDepartmentChange={setDepartment}
        periodType={periodType}
        onPeriodTypeChange={setPeriodType}
      />

      {/* Overview cards + Alerts */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 9 }}>
          <OverviewCards data={data} />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <ThresholdAlertPanel alerts={alerts} />
        </Grid>
      </Grid>

      {/* Score Trend Chart (full width) */}
      {trendData.length > 0 && (
        <Box sx={{ mb: 3 }}>
          <ScoreTrendChart data={trendData} title={t('dashboard.scoreTrend')} />
        </Box>
      )}

      {/* Department drill-down + Score distribution */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          {enhancedData?.departmentScores && Object.keys(enhancedData.departmentScores).length > 0 ? (
            <DepartmentDrilldownChart data={enhancedData.departmentScores} />
          ) : (
            <Paper variant="outlined" sx={{ p: 3, height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography color="text.secondary">{t('dashboard.noDepartmentData')}</Typography>
            </Paper>
          )}
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ScoreDistributionChart data={data.byScoring} />
        </Grid>
      </Grid>

      {/* Recent evaluations + Quality report */}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <RecentEvaluationsTable evaluations={data.recentEvaluations} />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <QualityReportPanel report={qualityReport} />
        </Grid>
      </Grid>
    </Box>
  )
}
