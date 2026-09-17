import { useState } from 'react'
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Chip,
  TextField,
  CircularProgress,
  ToggleButton,
  ToggleButtonGroup,
  IconButton,
  Tooltip,
} from '@mui/material'
import { ContentCopy as CopyIcon, CheckCircle as CheckIcon } from '@mui/icons-material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  clinicApplicationApi,
  type ClinicApplication,
  type ApprovalResult,
} from '../api/clinicApplicationApi'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../constants/timing'

const STATUS_COLOR: Record<string, 'warning' | 'success' | 'default'> = {
  pending: 'warning',
  approved: 'success',
  rejected: 'default',
}

/** Operator review of clinic applications (#700 PR-2). */
export default function ClinicApplicationsAdminPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()

  const [statusFilter, setStatusFilter] = useState<string>('pending')
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState<number | null>(null)

  // Approval result dialog — the setup link is shown ONCE, copy-to-clipboard offered.
  const [approval, setApproval] = useState<ApprovalResult | null>(null)
  const [copied, setCopied] = useState(false)

  // Reject dialog
  const [rejectTarget, setRejectTarget] = useState<ClinicApplication | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  const { data: applications, isLoading } = useQuery({
    queryKey: ['admin', 'clinic-applications', statusFilter],
    queryFn: () => clinicApplicationApi.list(statusFilter === 'all' ? undefined : statusFilter),
  })

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['admin', 'clinic-applications'] })

  const handleApprove = async (app: ClinicApplication) => {
    setActionLoading(app.id)
    setError(null)
    try {
      const result = await clinicApplicationApi.approve(app.id)
      setApproval(result)
      await refresh()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('applications.approveFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleReject = async () => {
    if (!rejectTarget) return
    setActionLoading(rejectTarget.id)
    setError(null)
    try {
      await clinicApplicationApi.reject(rejectTarget.id, rejectReason)
      setRejectTarget(null)
      setRejectReason('')
      await refresh()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('applications.rejectFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const copyLink = async () => {
    if (!approval) return
    await navigator.clipboard.writeText(approval.setupLink)
    setCopied(true)
    setTimeout(() => setCopied(false), COPY_FEEDBACK_TIMEOUT_MS)
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1100, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5">{t('applications.title')}</Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={statusFilter}
          onChange={(_, v) => v && setStatusFilter(v)}
        >
          <ToggleButton value="pending">{t('applications.statusPending')}</ToggleButton>
          <ToggleButton value="approved">{t('applications.statusApproved')}</ToggleButton>
          <ToggleButton value="rejected">{t('applications.statusRejected')}</ToggleButton>
          <ToggleButton value="all">{t('applications.filterAll')}</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('applications.clinicName')}</TableCell>
              <TableCell>{t('applications.tenantCode')}</TableCell>
              <TableCell>{t('applications.adminUsername')}</TableCell>
              <TableCell>{t('applications.adminEmail')}</TableCell>
              <TableCell>{t('applications.status')}</TableCell>
              <TableCell>{t('applications.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            )}
            {(applications ?? []).map((app) => (
              <TableRow key={app.id}>
                <TableCell>{app.clinicName}</TableCell>
                <TableCell>
                  <code>{app.tenantCode}</code>
                </TableCell>
                <TableCell>{app.adminUsername}</TableCell>
                <TableCell>{app.adminEmail}</TableCell>
                <TableCell>
                  <Tooltip title={app.rejectionReason ?? ''}>
                    <Chip
                      size="small"
                      label={t(`applications.status${app.status[0].toUpperCase()}${app.status.slice(1)}`)}
                      color={STATUS_COLOR[app.status]}
                    />
                  </Tooltip>
                </TableCell>
                <TableCell>
                  {app.status === 'pending' && (
                    <Box sx={{ display: 'flex', gap: 1 }}>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={actionLoading === app.id}
                        onClick={() => handleApprove(app)}
                      >
                        {actionLoading === app.id ? (
                          <CircularProgress size={16} />
                        ) : (
                          t('applications.approve')
                        )}
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        disabled={actionLoading === app.id}
                        onClick={() => setRejectTarget(app)}
                      >
                        {t('applications.reject')}
                      </Button>
                    </Box>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Approval result — setup link shown once */}
      <Dialog open={approval != null} onClose={() => setApproval(null)} fullWidth maxWidth="sm">
        <DialogTitle>{t('applications.approvedTitle')}</DialogTitle>
        <DialogContent>
          <Alert severity="success" sx={{ mb: 2 }}>
            {t('applications.approvedMessage', {
              tenant: approval?.application.tenantCode ?? '',
              username: approval?.application.adminUsername ?? '',
            })}
          </Alert>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {t('applications.setupLinkHelp')}
          </Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            <TextField
              fullWidth
              size="small"
              value={approval?.setupLink ?? ''}
              slotProps={{ htmlInput: { readOnly: true } }}
            />
            <IconButton onClick={copyLink} aria-label={t('applications.copyLink')}>
              {copied ? <CheckIcon color="success" /> : <CopyIcon />}
            </IconButton>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApproval(null)}>{tc('actions.close')}</Button>
        </DialogActions>
      </Dialog>

      {/* Reject dialog */}
      <Dialog
        open={rejectTarget != null}
        onClose={() => setRejectTarget(null)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>{t('applications.rejectTitle', { name: rejectTarget?.clinicName ?? '' })}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            margin="dense"
            multiline
            minRows={2}
            label={t('applications.rejectReason')}
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)}>{tc('actions.cancel')}</Button>
          <Button color="error" variant="contained" onClick={handleReject}>
            {t('applications.reject')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
