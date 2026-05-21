import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Stack,
  Button,
  CircularProgress,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the old Proxy mock).
import RefreshIcon from '@mui/icons-material/Refresh'
import { api } from '../../api/client'
import { REFETCH_10S } from '../../constants/queryConstants'

interface InvocationRecord {
  timestamp: string
  serviceId: string
  hook: string
  patientId: string
  success: boolean
  elapsedMs: number
  errorPhase?: string
  errorMessage?: string
}

export default function RecentInvocationsPanel() {
  const { t } = useTranslation('cds')

  const { data, isLoading, isError, refetch, isFetching } = useQuery<InvocationRecord[]>({
    queryKey: ['cds-recent-invocations'],
    queryFn: () =>
      api.get<InvocationRecord[]>('/admin/cds/recent-invocations?limit=100').then((r) => r.data),
    refetchInterval: REFETCH_10S,
  })

  if (isLoading) return <CircularProgress />
  if (isError) return <Alert severity="error">{t('recentInvocations.loadError')}</Alert>

  const records = data ?? []

  return (
    <Stack spacing={1}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle2">
          {t('recentInvocations.total', { count: records.length })}
        </Typography>
        <Button
          size="small"
          startIcon={isFetching ? <CircularProgress size={14} /> : <RefreshIcon fontSize="small" />}
          onClick={() => refetch()}
          disabled={isFetching}
        >
          {t('recentInvocations.refresh')}
        </Button>
      </Stack>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('recentInvocations.time')}</TableCell>
              <TableCell>{t('recentInvocations.service')}</TableCell>
              <TableCell>{t('recentInvocations.hook')}</TableCell>
              <TableCell>{t('recentInvocations.patient')}</TableCell>
              <TableCell>{t('recentInvocations.status')}</TableCell>
              <TableCell>{t('recentInvocations.duration')}</TableCell>
              <TableCell>{t('recentInvocations.detail')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {records.map((r, i) => (
              <TableRow key={`${r.timestamp}-${i}`} hover>
                <TableCell>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                    {new Date(r.timestamp).toLocaleString()}
                  </Typography>
                </TableCell>
                <TableCell>{r.serviceId}</TableCell>
                <TableCell>
                  <Chip size="small" label={r.hook} variant="outlined" />
                </TableCell>
                <TableCell>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                    {r.patientId}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={r.success ? 'success' : 'error'}
                    label={r.success ? t('recentInvocations.success') : t('recentInvocations.failed')}
                  />
                </TableCell>
                <TableCell>{r.elapsedMs}ms</TableCell>
                <TableCell>
                  {r.errorPhase && (
                    <Chip size="small" variant="outlined" label={t(`debug.phase.${r.errorPhase}`, { defaultValue: r.errorPhase })} />
                  )}
                  {r.errorMessage && (
                    <Typography variant="caption" color="error" sx={{ ml: 1, fontFamily: 'monospace' }}>
                      {r.errorMessage}
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {records.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary">
                    {t('recentInvocations.empty')}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  )
}
