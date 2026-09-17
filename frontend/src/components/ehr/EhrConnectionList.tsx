import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
  IconButton,
  Chip,
  Stack,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Alert,
} from '@mui/material'
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  PlayArrow as TestIcon,
  Add as AddIcon,
} from '@mui/icons-material'
import { ehrApi } from '../../api'
import type { ConnectionHealthOverview } from '../../api/ehrApi'
import type { EhrConnection } from '../../types'
import { STALE_5M, STALE_30S, REFETCH_30S } from '../../constants/queryConstants'
import GradientButton from '../common/GradientButton'
import EhrConnectionForm from './EhrConnectionForm'

function statusColor(status?: string): 'default' | 'success' | 'error' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'error'
  return 'default'
}

// PAT-111: map the backend's `currentStatus` wire value to a MUI Chip color.
// Returns a tuple [color, i18n key for display label].
function healthDisplay(status: string | undefined): {
  color: 'success' | 'warning' | 'error' | 'default'
  labelKey: string
} {
  switch (status) {
    case 'healthy':
      return { color: 'success', labelKey: 'ehr.health.healthy' }
    case 'degraded':
      return { color: 'warning', labelKey: 'ehr.health.degraded' }
    case 'down':
      return { color: 'error', labelKey: 'ehr.health.down' }
    default:
      return { color: 'default', labelKey: 'ehr.health.unknown' }
  }
}

export default function EhrConnectionList() {
  const { t } = useTranslation('fhir')
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<EhrConnection | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<EhrConnection | null>(null)
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; msg: string } | null>(null)

  const { data: connections = [], isLoading } = useQuery({
    queryKey: ['ehr-connections'],
    queryFn: () => ehrApi.getConnections(),
    staleTime: STALE_5M,
  })

  // PAT-111: background poll of /api/ehr/health/overview so the admin table
  // reflects the current circuit-breaker / availability state, not just the
  // last manual "Test" result. React Query dedups overlapping polls; if the
  // request fails, the previous snapshot stays rendered (we deliberately do
  // NOT show a loading skeleton for the health column — admin table must
  // stay usable during transient backend hiccups).
  const { data: healthOverview = [] } = useQuery({
    queryKey: ['ehr-health-overview'],
    queryFn: () => ehrApi.getHealthOverview(),
    staleTime: STALE_30S,
    refetchInterval: REFETCH_30S,
  })

  const healthById = new Map<number, ConnectionHealthOverview>(
    healthOverview.map((h) => [h.connectionId, h])
  )

  const testMutation = useMutation({
    mutationFn: (id: number) => ehrApi.testConnection(id),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['ehr-connections'] })
      setFeedback({
        type: result.status === 'success' ? 'success' : 'error',
        msg: result.status === 'success'
          ? t('ehr.testSuccess')
          : t('ehr.testFailed', { message: result.lastTestMessage || '' }),
      })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => ehrApi.deleteConnection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ehr-connections'] })
      setDeleteTarget(null)
      setFeedback({ type: 'success', msg: t('ehr.connectionDeleted') })
    },
  })

  const handleEdit = (conn: EhrConnection) => {
    setEditing(conn)
    setFormOpen(true)
  }

  const handleAdd = () => {
    setEditing(null)
    setFormOpen(true)
  }

  const handleFormClose = (saved?: boolean) => {
    setFormOpen(false)
    setEditing(null)
    if (saved) {
      queryClient.invalidateQueries({ queryKey: ['ehr-connections'] })
      setFeedback({ type: 'success', msg: t('ehr.connectionSaved') })
    }
  }

  return (
    <Box>
      {feedback && (
        <Alert
          severity={feedback.type}
          onClose={() => setFeedback(null)}
          sx={{ mb: 2 }}
        >
          {feedback.msg}
        </Alert>
      )}
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2
        }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          {t('ehr.tabTitle')}
        </Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          {t('ehr.addConnection')}
        </GradientButton>
      </Stack>
      {connections.length === 0 && !isLoading ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography sx={{
            color: "text.secondary"
          }}>
            {t('ehr.noConnections')}
          </Typography>
        </Paper>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.connectionName')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.serverUrl')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.authType')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.department')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="center">{t('ehr.status.untested')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="center">
                  <Tooltip title={t('ehr.health.columnTooltip')}>
                    <span>{t('ehr.health.column')}</span>
                  </Tooltip>
                </TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {connections.map((conn) => (
                <TableRow key={conn.id} sx={{ '&:last-child td': { borderBottom: 0 } }}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {conn.name}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                      {conn.fhirServerUrl}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={t({ basic: 'ehr.authBasic', bearer: 'ehr.authBearer', smart_backend: 'ehr.authSmartBackend', none: 'ehr.authNone' }[conn.authType] ?? 'ehr.authNone')}
                      size="small"
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>{conn.department || '--'}</TableCell>
                  <TableCell align="center">
                    <Chip
                      label={t(`ehr.status.${conn.status || 'untested'}`)}
                      size="small"
                      color={statusColor(conn.status)}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <HealthCell
                      t={t}
                      health={conn.id ? healthById.get(conn.id) : undefined}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} sx={{
                      justifyContent: "flex-end"
                    }}>
                      <Tooltip title={t('ehr.testConnection')}>
                        <IconButton
                          size="small"
                          onClick={() => conn.id && testMutation.mutate(conn.id)}
                          disabled={testMutation.isPending}
                        >
                          <TestIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t('ehr.editConnection')}>
                        <IconButton size="small" onClick={() => handleEdit(conn)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t('ehr.deleteConnection')}>
                        <IconButton size="small" onClick={() => setDeleteTarget(conn)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {/* Connection form dialog */}
      {formOpen && (
        <EhrConnectionForm
          open={formOpen}
          connection={editing}
          onClose={handleFormClose}
        />
      )}
      {/* Delete confirmation dialog */}
      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>{t('ehr.deleteConnection')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('ehr.deleteConfirm', { name: deleteTarget?.name })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>{t('ehr.cancel')}</Button>
          <Button
            color="error"
            onClick={() => deleteTarget?.id && deleteMutation.mutate(deleteTarget.id)}
            disabled={deleteMutation.isPending}
          >
            {t('ehr.deleteConnection')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// PAT-111: one row's Live Health cell. When the overview hasn't loaded yet
// or the connection has never been health-checked, render a muted "unknown"
// chip rather than an empty space — zero info is its own signal the admin
// needs to see (e.g. scheduled health check hasn't run since last restart).
function HealthCell({
  t,
  health,
}: {
  t: ReturnType<typeof useTranslation>['t']
  health: ConnectionHealthOverview | undefined
}) {
  if (!health) {
    const { color, labelKey } = healthDisplay('unknown')
    return <Chip size="small" color={color} label={t(labelKey)} />
  }
  const { color, labelKey } = healthDisplay(health.currentStatus)
  const tooltipLines: string[] = []
  if (health.availability24h != null) {
    tooltipLines.push(t('ehr.health.availability24h', { pct: health.availability24h.toFixed(1) }))
  }
  if (health.avgResponseTimeMs24h != null) {
    tooltipLines.push(t('ehr.health.avgResponseMs', { ms: Math.round(health.avgResponseTimeMs24h) }))
  }
  if (health.errorCount24h > 0) {
    tooltipLines.push(t('ehr.health.errorCount24h', { count: health.errorCount24h }))
  }
  if (health.lastCheckedAt) {
    tooltipLines.push(t('ehr.health.lastCheckedAt', { at: new Date(health.lastCheckedAt).toLocaleString() }))
  }
  const tooltip = tooltipLines.length ? tooltipLines.join('\n') : t('ehr.health.noData')
  // Show the availability number on the chip itself when available — one
  // glance tells the admin "100%" vs "87%" vs "degrading".
  const label = health.availability24h != null
    ? `${t(labelKey)} ${health.availability24h.toFixed(0)}%`
    : t(labelKey)
  return (
    <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{tooltip}</span>} arrow>
      <Chip size="small" color={color} label={label} />
    </Tooltip>
  )
}
