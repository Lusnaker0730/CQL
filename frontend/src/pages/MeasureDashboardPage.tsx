import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
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
  Assessment as AssessmentIcon,
  RateReview as ReviewIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material'
import { measureApi } from '../api'
import type { DashboardSummary } from '../types'
import StatusChip from '../components/common/StatusChip'
import DashboardSkeleton from '../components/common/DashboardSkeleton'

const TEAL = '#0D7377'
const TEAL_LIGHT = '#14A3A8'

const STATUS_ORDER = ['active', 'draft', 'retired', 'in-review'] as const

function getScoreColor(score: number | undefined): 'success' | 'warning' | 'error' {
  if (score == null) return 'error'
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'error'
}

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

function formatScoringLabel(key: string): string {
  return key
    .replace(/-/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
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
        <Grid item xs={12} sm={6} md key={card.label}>
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

function ScoringDistribution({ byScoring }: { byScoring: Record<string, number> }) {
  const { t } = useTranslation('measures')
  const entries = Object.entries(byScoring).sort((a, b) => b[1] - a[1])
  const maxCount = Math.max(...entries.map(([, v]) => v), 1)

  return (
    <Paper
      elevation={0}
      sx={{
        p: 3,
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        height: '100%',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2.5 }}>
        <AssessmentIcon sx={{ color: TEAL, fontSize: 22 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          {t('dashboard.scoringDistribution')}
        </Typography>
      </Stack>

      {entries.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.noScoringData')}
        </Typography>
      ) : (
        <Stack spacing={2}>
          {entries.map(([type, count]) => (
            <Box key={type}>
              <Stack
                direction="row"
                justifyContent="space-between"
                sx={{ mb: 0.5 }}
              >
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {formatScoringLabel(type)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {count}
                </Typography>
              </Stack>
              <Box
                sx={{
                  width: '100%',
                  height: 10,
                  borderRadius: 5,
                  bgcolor: 'grey.100',
                  overflow: 'hidden',
                }}
              >
                <Box
                  sx={{
                    width: `${(count / maxCount) * 100}%`,
                    height: '100%',
                    borderRadius: 5,
                    background: `linear-gradient(90deg, ${TEAL} 0%, ${TEAL_LIGHT} 100%)`,
                    transition: 'width 0.6s ease-in-out',
                  }}
                />
              </Box>
            </Box>
          ))}
        </Stack>
      )}
    </Paper>
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
                      color={getScoreColor(ev.score)}
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

function PendingReviewList({
  items,
}: {
  items: DashboardSummary['pendingReview']
}) {
  const { t } = useTranslation('measures')
  return (
    <Paper
      elevation={0}
      sx={{
        p: 3,
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        height: '100%',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2.5 }}>
        <ReviewIcon sx={{ color: TEAL, fontSize: 22 }} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          {t('dashboard.pendingReview')}
        </Typography>
        {items.length > 0 && (
          <Chip
            label={items.length}
            size="small"
            sx={{
              bgcolor: TEAL,
              color: '#fff',
              fontWeight: 600,
              height: 22,
              fontSize: 12,
            }}
          />
        )}
      </Stack>

      {items.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.noPendingReview')}
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {items.map((item) => (
            <Box
              key={item.id}
              sx={{
                p: 1.5,
                borderRadius: 1.5,
                bgcolor: 'grey.50',
                border: '1px solid',
                borderColor: 'grey.200',
              }}
            >
              <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.25 }}>
                {item.name}
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="caption" color="text.secondary">
                  v{item.version}
                </Typography>
                {item.owner && (
                  <>
                    <Typography variant="caption" color="text.disabled">
                      |
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {item.owner}
                    </Typography>
                  </>
                )}
              </Stack>
            </Box>
          ))}
        </Stack>
      )}
    </Paper>
  )
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function MeasureDashboardPage() {
  const { t } = useTranslation('measures')
  const {
    data,
    isLoading,
    isError,
    error,
  } = useQuery<DashboardSummary>({
    queryKey: ['measure-dashboard'],
    queryFn: measureApi.getDashboard,
    staleTime: 30_000,
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
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 3 }}>
        <DashboardIcon sx={{ color: TEAL, fontSize: 28 }} />
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {t('dashboard.title')}
        </Typography>
      </Stack>

      {/* Overview cards */}
      <Box sx={{ mb: 3 }}>
        <OverviewCards data={data} />
      </Box>

      {/* Middle row: Scoring distribution + Pending review */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={7}>
          <ScoringDistribution byScoring={data.byScoring} />
        </Grid>
        <Grid item xs={12} md={5}>
          <PendingReviewList items={data.pendingReview} />
        </Grid>
      </Grid>

      {/* Recent evaluations table */}
      <RecentEvaluationsTable evaluations={data.recentEvaluations} />
    </Box>
  )
}
