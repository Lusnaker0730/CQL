import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Chip,
  CircularProgress,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  TablePagination,
  Tooltip,
  alpha,
} from '@mui/material'
import {
  Security as SecurityIcon,
  Download as DownloadIcon,
  Shield as ShieldIcon,
  People as PeopleIcon,
  Warning as WarningIcon,
  HealthAndSafety as PhiIcon,
} from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../api'
import type { AuditLogEntry, AuditLogSearchParams, AuditStatsResponse } from '../types'

function StatCard({ title, value, icon, color }: { title: string; value: number; icon: React.ReactNode; color: string }) {
  return (
    <Paper
      sx={{
        p: 2.5,
        borderRadius: 2,
        flex: 1,
        minWidth: 200,
        border: '1px solid',
        borderColor: 'divider',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: '12px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: alpha(color, 0.1),
            color: color,
          }}
        >
          {icon}
        </Box>
        <Box>
          <Typography variant="h5" fontWeight={700}>
            {value.toLocaleString()}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {title}
          </Typography>
        </Box>
      </Box>
    </Paper>
  )
}

function ActivityChart({ stats, t }: { stats: AuditStatsResponse; t: (key: string, options?: Record<string, unknown>) => string }) {
  const maxCount = Math.max(...stats.dailyActivity.map((d) => d.count), 1)

  return (
    <Paper sx={{ p: 2.5, borderRadius: 2, flex: 2, border: '1px solid', borderColor: 'divider' }}>
      <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 2 }}>
        {t('audit.activityTimeline')}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 0.5, height: 120 }}>
        {stats.dailyActivity.map((day) => (
          <Tooltip key={day.date} title={t('audit.activityTooltip', { date: day.date, count: day.count })}>
            <Box
              sx={{
                flex: 1,
                height: `${(day.count / maxCount) * 100}%`,
                minHeight: 4,
                bgcolor: 'primary.main',
                borderRadius: '4px 4px 0 0',
                transition: 'height 0.3s ease',
                '&:hover': { bgcolor: 'primary.dark' },
              }}
            />
          </Tooltip>
        ))}
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
        <Typography variant="caption" color="text.secondary">
          {stats.dailyActivity.length > 0 ? stats.dailyActivity[0].date : ''}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {stats.dailyActivity.length > 0 ? stats.dailyActivity[stats.dailyActivity.length - 1].date : ''}
        </Typography>
      </Box>
    </Paper>
  )
}

function ActionDistribution({ stats, t }: { stats: AuditStatsResponse; t: (key: string) => string }) {
  const entries = Object.entries(stats.actionCounts)
  const maxCount = Math.max(...entries.map(([, v]) => v), 1)
  const colors: Record<string, string> = {
    READ: '#0D7377',
    CREATE: '#2E7D32',
    UPDATE: '#ED6C02',
    DELETE: '#D32F2F',
  }

  return (
    <Paper sx={{ p: 2.5, borderRadius: 2, flex: 1, border: '1px solid', borderColor: 'divider' }}>
      <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 2 }}>
        {t('audit.actionDistribution')}
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {entries.map(([action, count]) => (
          <Box key={action}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="body2" fontWeight={500}>
                {action}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {count.toLocaleString()}
              </Typography>
            </Box>
            <Box sx={{ height: 8, bgcolor: 'action.hover', borderRadius: 4 }}>
              <Box
                sx={{
                  height: '100%',
                  width: `${(count / maxCount) * 100}%`,
                  bgcolor: colors[action] || 'primary.main',
                  borderRadius: 4,
                  transition: 'width 0.3s ease',
                }}
              />
            </Box>
          </Box>
        ))}
      </Box>
    </Paper>
  )
}

function AuditLogTable({
  logs,
  loading,
  page,
  totalElements,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  t,
}: {
  logs: AuditLogEntry[]
  loading: boolean
  page: number
  totalElements: number
  rowsPerPage: number
  onPageChange: (page: number) => void
  onRowsPerPageChange: (size: number) => void
  t: (key: string) => string
}) {
  const statusColor = (code?: number) => {
    if (!code) return 'default'
    if (code < 300) return 'success'
    if (code < 400) return 'info'
    if (code < 500) return 'warning'
    return 'error'
  }

  return (
    <>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.timestamp')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.user')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.action')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.method')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.path')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.status')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.ipAddress')}</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>{t('audit.table.timeMs')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={32} />
                </TableCell>
              </TableRow>
            ) : logs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">{t('audit.table.noEntries')}</Typography>
                </TableCell>
              </TableRow>
            ) : (
              logs.map((log) => (
                <TableRow key={log.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                      {log.createdAt ? new Date(log.createdAt).toLocaleString() : '--'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontWeight={500}>
                      {log.username}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip label={log.action} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{log.method}</Typography>
                  </TableCell>
                  <TableCell>
                    <Tooltip title={log.path}>
                      <Typography
                        variant="body2"
                        sx={{
                          maxWidth: 300,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {log.path}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={log.statusCode ?? '--'}
                      size="small"
                      color={statusColor(log.statusCode)}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                      {log.ipAddress || '--'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{log.responseTimeMs ?? '--'}</Typography>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={totalElements}
        page={page}
        onPageChange={(_, p) => onPageChange(p)}
        rowsPerPage={rowsPerPage}
        onRowsPerPageChange={(e) => onRowsPerPageChange(parseInt(e.target.value, 10))}
        rowsPerPageOptions={[10, 20, 50]}
      />
    </>
  )
}

export default function AuditDashboardPage() {
  const { t } = useTranslation('admin')
  const [tab, setTab] = useState(0)

  // All logs filters
  const [filters, setFilters] = useState<AuditLogSearchParams>({ page: 0, size: 20 })

  // Tab-specific pagination
  const [phiPage, setPhiPage] = useState(0)
  const [phiSize, setPhiSize] = useState(20)
  const [loginPage, setLoginPage] = useState(0)
  const [loginSize, setLoginSize] = useState(20)
  const [secPage, setSecPage] = useState(0)
  const [secSize, setSecSize] = useState(20)

  // Stats query
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['audit', 'stats'],
    queryFn: adminApi.getAuditStats,
    refetchInterval: 30000,
  })

  // All logs query
  const { data: allLogs, isLoading: allLogsLoading } = useQuery({
    queryKey: ['audit', 'logs', filters],
    queryFn: () => adminApi.getAuditLogs(filters),
    enabled: tab === 0,
  })

  // PHI access query
  const { data: phiLogs, isLoading: phiLoading } = useQuery({
    queryKey: ['audit', 'phi', phiPage, phiSize],
    queryFn: () => adminApi.getPhiAccess(phiPage, phiSize),
    enabled: tab === 1,
  })

  // Login activity query
  const { data: loginLogs, isLoading: loginLoading } = useQuery({
    queryKey: ['audit', 'login', loginPage, loginSize],
    queryFn: () => adminApi.getLoginActivity(loginPage, loginSize),
    enabled: tab === 2,
  })

  // Security events query
  const { data: secLogs, isLoading: secLoading } = useQuery({
    queryKey: ['audit', 'security', secPage, secSize],
    queryFn: () => adminApi.getSecurityEvents(secPage, secSize),
    enabled: tab === 3,
  })

  const handleExport = useCallback(async () => {
    try {
      const blob = await adminApi.exportAuditLogs(filters)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      // Export failed silently
    }
  }, [filters])

  const updateFilter = (key: string, value: string) => {
    setFilters((prev) => ({ ...prev, [key]: value || undefined, page: 0 }))
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
        <SecurityIcon sx={{ fontSize: 28, color: 'primary.main' }} />
        <Typography variant="h5" fontWeight={700}>
          {t('audit.title')}
        </Typography>
      </Box>

      {/* Stat Cards */}
      {statsLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : stats ? (
        <>
          <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
            <StatCard
              title={t('audit.stats.eventsToday')}
              value={stats.totalEventsToday}
              icon={<ShieldIcon />}
              color="#0D7377"
            />
            <StatCard
              title={t('audit.stats.phiAccessesToday')}
              value={stats.phiAccessCount}
              icon={<PhiIcon />}
              color="#1B3A5C"
            />
            <StatCard
              title={t('audit.stats.failedLoginsToday')}
              value={stats.failedLoginAttempts}
              icon={<WarningIcon />}
              color="#D32F2F"
            />
            <StatCard
              title={t('audit.stats.activeUsersToday')}
              value={stats.activeUsersToday}
              icon={<PeopleIcon />}
              color="#2E7D32"
            />
          </Box>

          {/* Charts Row */}
          <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
            {stats.dailyActivity.length > 0 && <ActivityChart stats={stats} t={t} />}
            {Object.keys(stats.actionCounts).length > 0 && <ActionDistribution stats={stats} t={t} />}
          </Box>

          {/* Top Users */}
          {stats.topUsers.length > 0 && (
            <Paper sx={{ p: 2.5, borderRadius: 2, mb: 3, border: '1px solid', borderColor: 'divider' }}>
              <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
                {t('audit.topUsers.title')}
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>{t('audit.topUsers.username')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }} align="right">{t('audit.topUsers.events')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t('audit.topUsers.lastActivity')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {stats.topUsers.map((u) => (
                      <TableRow key={u.username} hover>
                        <TableCell>
                          <Typography variant="body2" fontWeight={500}>{u.username}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">{u.eventCount.toLocaleString()}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">
                            {u.lastActivityAt ? new Date(u.lastActivityAt).toLocaleString() : '--'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          )}
        </>
      ) : null}

      {/* Tabbed Log Viewer */}
      <Paper sx={{ borderRadius: 2, border: '1px solid', borderColor: 'divider' }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
        >
          <Tab label={t('audit.tabs.allLogs')} />
          <Tab label={t('audit.tabs.phiAccess')} />
          <Tab label={t('audit.tabs.loginActivity')} />
          <Tab label={t('audit.tabs.securityEvents')} />
        </Tabs>

        {/* All Logs Tab */}
        {tab === 0 && (
          <Box>
            <Box sx={{ display: 'flex', gap: 1.5, p: 2, flexWrap: 'wrap', alignItems: 'center' }}>
              <TextField
                label={t('audit.filters.username')}
                size="small"
                value={filters.username || ''}
                onChange={(e) => updateFilter('username', e.target.value)}
                sx={{ minWidth: 150 }}
              />
              <FormControl size="small" sx={{ minWidth: 130 }}>
                <InputLabel>{t('audit.filters.action')}</InputLabel>
                <Select
                  label={t('audit.filters.action')}
                  value={filters.action || ''}
                  onChange={(e) => updateFilter('action', e.target.value)}
                >
                  <MenuItem value="">{t('audit.filters.actionAll')}</MenuItem>
                  <MenuItem value="READ">READ</MenuItem>
                  <MenuItem value="CREATE">CREATE</MenuItem>
                  <MenuItem value="UPDATE">UPDATE</MenuItem>
                  <MenuItem value="DELETE">DELETE</MenuItem>
                </Select>
              </FormControl>
              <TextField
                label={t('audit.filters.startDate')}
                type="date"
                size="small"
                value={filters.startDate || ''}
                onChange={(e) => updateFilter('startDate', e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ minWidth: 150 }}
              />
              <TextField
                label={t('audit.filters.endDate')}
                type="date"
                size="small"
                value={filters.endDate || ''}
                onChange={(e) => updateFilter('endDate', e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ minWidth: 150 }}
              />
              <TextField
                label={t('audit.filters.statusCode')}
                type="number"
                size="small"
                value={filters.statusCode || ''}
                onChange={(e) => updateFilter('statusCode', e.target.value)}
                sx={{ minWidth: 110 }}
              />
              <Box sx={{ flexGrow: 1 }} />
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={handleExport}
                size="small"
                sx={{ borderRadius: 2 }}
              >
                {t('audit.exportCsv')}
              </Button>
            </Box>
            <AuditLogTable
              logs={allLogs?.content || []}
              loading={allLogsLoading}
              page={filters.page || 0}
              totalElements={allLogs?.totalElements || 0}
              rowsPerPage={filters.size || 20}
              onPageChange={(p) => setFilters((prev) => ({ ...prev, page: p }))}
              onRowsPerPageChange={(s) => setFilters((prev) => ({ ...prev, size: s, page: 0 }))}
              t={t}
            />
          </Box>
        )}

        {/* PHI Access Tab */}
        {tab === 1 && (
          <AuditLogTable
            logs={phiLogs?.content || []}
            loading={phiLoading}
            page={phiPage}
            totalElements={phiLogs?.totalElements || 0}
            rowsPerPage={phiSize}
            onPageChange={setPhiPage}
            onRowsPerPageChange={(s) => { setPhiSize(s); setPhiPage(0) }}
            t={t}
          />
        )}

        {/* Login Activity Tab */}
        {tab === 2 && (
          <AuditLogTable
            logs={loginLogs?.content || []}
            loading={loginLoading}
            page={loginPage}
            totalElements={loginLogs?.totalElements || 0}
            rowsPerPage={loginSize}
            onPageChange={setLoginPage}
            onRowsPerPageChange={(s) => { setLoginSize(s); setLoginPage(0) }}
            t={t}
          />
        )}

        {/* Security Events Tab */}
        {tab === 3 && (
          <AuditLogTable
            logs={secLogs?.content || []}
            loading={secLoading}
            page={secPage}
            totalElements={secLogs?.totalElements || 0}
            rowsPerPage={secSize}
            onPageChange={setSecPage}
            onRowsPerPageChange={(s) => { setSecSize(s); setSecPage(0) }}
            t={t}
          />
        )}
      </Paper>
    </Box>
  )
}
