import { useState } from 'react'
import { useTranslation, Trans } from 'react-i18next'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../constants/timing'
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
  IconButton,
  Tooltip,
  TextField,
  InputAdornment,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Switch,
  DialogContentText,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  LockReset as ResetIcon,
  CheckCircle as CheckIcon,
  PersonAdd as PersonAddIcon,
} from '@mui/icons-material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api'
import type { AdminResetPasswordResponse, AdminCreateUserRequest } from '../types'
import { getStoredUsername } from '../utils/validation'

export default function AdminUsersPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const currentUsername = getStoredUsername()

  // Reset password state
  const [resetResult, setResetResult] = useState<AdminResetPasswordResponse | null>(null)
  const [resetDialogOpen, setResetDialogOpen] = useState(false)
  const [resetLoading, setResetLoading] = useState<number | null>(null)
  const [copied, setCopied] = useState(false)

  // Create user state
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [createForm, setCreateForm] = useState<AdminCreateUserRequest>({
    username: '',
    password: '',
    email: '',
    role: 'USER',
  })
  const [createLoading, setCreateLoading] = useState(false)

  // Confirmation dialog state
  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean
    title: string
    message: string
    onConfirm: () => void
  }>({ open: false, title: '', message: '', onConfirm: () => {} })

  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.listUsers,
  })

  const refreshUsers = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
  }

  // --- Create User ---
  const handleCreateUser = async () => {
    setError('')
    setCreateLoading(true)
    try {
      await adminApi.createUser(createForm)
      setCreateDialogOpen(false)
      setCreateForm({ username: '', password: '', email: '', role: 'USER' })
      setSuccess(t('users.success.userCreated'))
      refreshUsers()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string }; status?: number } }
      if (axiosErr.response?.status === 400) {
        setError(t('users.errors.usernameExists'))
      } else {
        setError(axiosErr.response?.data?.error || t('users.errors.createFailed'))
      }
    } finally {
      setCreateLoading(false)
    }
  }

  // --- Reset Password ---
  const handleResetPassword = async (userId: number) => {
    setError('')
    setResetLoading(userId)
    try {
      const result = await adminApi.resetUserPassword(userId)
      setResetResult(result)
      setResetDialogOpen(true)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error || t('users.errors.resetFailed'))
    } finally {
      setResetLoading(null)
    }
  }

  // --- Role Change ---
  const handleRoleChange = (userId: number, username: string, newRole: string) => {
    setConfirmDialog({
      open: true,
      title: t('users.confirmDialog.changeRoleTitle'),
      message: t('users.confirmDialog.changeRoleMessage', { username, role: newRole }),
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await adminApi.updateUserRole(userId, newRole)
          setSuccess(t('users.success.roleUpdated', { username, role: newRole }))
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || t('users.errors.updateRoleFailed'))
        } finally {
          setActionLoading(null)
        }
      },
    })
  }

  // --- Enable/Disable ---
  const handleToggleEnabled = (userId: number, username: string, currentlyEnabled: boolean) => {
    const action = currentlyEnabled ? 'disable' : 'enable'
    setConfirmDialog({
      open: true,
      title: currentlyEnabled ? t('users.confirmDialog.disableUserTitle') : t('users.confirmDialog.enableUserTitle'),
      message: t('users.confirmDialog.toggleEnabledMessage', { action, username }),
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await adminApi.updateUserEnabled(userId, !currentlyEnabled)
          setSuccess(t('users.success.userToggled', { username, action }))
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || t('users.errors.toggleFailed', { action }))
        } finally {
          setActionLoading(null)
        }
      },
    })
  }

  const handleCopy = async () => {
    if (resetResult) {
      await navigator.clipboard.writeText(resetResult.temporaryPassword)
      setCopied(true)
      setTimeout(() => setCopied(false), COPY_FEEDBACK_TIMEOUT_MS)
    }
  }

  const handleCloseResetDialog = () => {
    setResetDialogOpen(false)
    setResetResult(null)
    setCopied(false)
  }

  const isCurrentUser = (username: string) => currentUsername === username

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>
          {t('users.title')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<PersonAddIcon />}
          onClick={() => setCreateDialogOpen(true)}
          sx={{ borderRadius: 2 }}
        >
          {t('users.createUser')}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Paper sx={{ borderRadius: 2 }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>{t('users.columns.username')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('users.columns.email')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('users.columns.role')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('users.columns.status')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('users.columns.created')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">{t('users.columns.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    <CircularProgress size={32} />
                  </TableCell>
                </TableRow>
              ) : users.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">{t('users.noUsersFound')}</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                users.map((user) => (
                  <TableRow key={user.id} hover>
                    <TableCell>
                      <Typography fontWeight={500}>
                        {user.username}
                        {isCurrentUser(user.username) && (
                          <Chip label={t('users.youChip')} size="small" sx={{ ml: 1 }} variant="outlined" />
                        )}
                        {user.authProvider === 'OKTA' && (
                          <Chip label={tc('auth.authProviderOkta')} size="small" sx={{ ml: 1 }} color="info" variant="outlined" />
                        )}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {user.email || '--'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <FormControl size="small" sx={{ minWidth: 100 }}>
                        <Select
                          value={user.role}
                          onChange={(e) => handleRoleChange(user.id, user.username, e.target.value)}
                          disabled={isCurrentUser(user.username) || actionLoading === user.id}
                          size="small"
                          sx={{ fontSize: '0.875rem' }}
                        >
                          <MenuItem value="ADMIN">{t('users.roles.admin')}</MenuItem>
                          <MenuItem value="USER">{t('users.roles.user')}</MenuItem>
                        </Select>
                      </FormControl>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {user.forcePasswordChange ? (
                          <Chip label={t('users.status.forceChange')} size="small" color="warning" variant="outlined" />
                        ) : user.enabled ? (
                          <Chip label={t('users.status.active')} size="small" color="success" variant="outlined" />
                        ) : (
                          <Chip label={t('users.status.disabled')} size="small" color="error" variant="outlined" />
                        )}
                        <Tooltip title={isCurrentUser(user.username) ? t('users.tooltips.cannotDisableSelf') : (user.enabled ? t('users.tooltips.disableUser') : t('users.tooltips.enableUser'))}>
                          <span>
                            <Switch
                              size="small"
                              checked={user.enabled}
                              onChange={() => handleToggleEnabled(user.id, user.username, user.enabled)}
                              disabled={isCurrentUser(user.username) || actionLoading === user.id}
                            />
                          </span>
                        </Tooltip>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '--'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      {user.authProvider !== 'OKTA' && (
                        <Tooltip title={t('users.tooltips.resetPassword')}>
                          <span>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={
                                resetLoading === user.id ? (
                                  <CircularProgress size={16} />
                                ) : (
                                  <ResetIcon />
                                )
                              }
                              onClick={() => handleResetPassword(user.id)}
                              disabled={resetLoading !== null || actionLoading === user.id}
                            >
                              {t('users.resetPasswordButton')}
                            </Button>
                          </span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Create User Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t('users.createDialog.title')}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField
              label={t('users.createDialog.usernameLabel')}
              value={createForm.username}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, username: e.target.value }))}
              fullWidth
              required
              autoFocus
              inputProps={{ maxLength: 50 }}
            />
            <TextField
              label={t('users.createDialog.passwordLabel')}
              type="password"
              value={createForm.password}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, password: e.target.value }))}
              fullWidth
              required
              helperText={t('users.createDialog.passwordHelperText')}
              inputProps={{ maxLength: 100 }}
            />
            <TextField
              label={t('users.createDialog.emailLabel')}
              type="email"
              value={createForm.email}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, email: e.target.value }))}
              fullWidth
              inputProps={{ maxLength: 200 }}
            />
            <FormControl fullWidth>
              <InputLabel>{t('users.createDialog.roleLabel')}</InputLabel>
              <Select
                value={createForm.role}
                label={t('users.createDialog.roleLabel')}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, role: e.target.value as 'ADMIN' | 'USER' }))}
              >
                <MenuItem value="USER">{t('users.roles.user')}</MenuItem>
                <MenuItem value="ADMIN">{t('users.roles.admin')}</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCreateDialogOpen(false)} disabled={createLoading}>
            {tc('actions.cancel')}
          </Button>
          <Button
            onClick={handleCreateUser}
            variant="contained"
            disabled={createLoading || !createForm.username || createForm.password.length < 8}
            startIcon={createLoading ? <CircularProgress size={16} /> : undefined}
            sx={{ borderRadius: 2 }}
          >
            {t('users.createDialog.createButton')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Temporary Password Dialog */}
      <Dialog open={resetDialogOpen} onClose={handleCloseResetDialog} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t('users.resetDialog.title')}</DialogTitle>
        <DialogContent>
          {resetResult && (
            <Box>
              <Alert severity="info" sx={{ mb: 2 }}>
                {resetResult.message}
              </Alert>
              <Typography variant="body2" sx={{ mb: 1 }}>
                <Trans i18nKey="users.resetDialog.userLabel" ns="admin" values={{ username: resetResult.username }} components={{ strong: <strong /> }} />
              </Typography>
              <Typography variant="body2" sx={{ mb: 1 }}>
                {t('users.resetDialog.temporaryPasswordLabel')}
              </Typography>
              <TextField
                fullWidth
                value={resetResult.temporaryPassword}
                InputProps={{
                  readOnly: true,
                  sx: { fontFamily: 'monospace', fontSize: '1.1rem' },
                  endAdornment: (
                    <InputAdornment position="end">
                      <Tooltip title={copied ? t('users.resetDialog.copiedTooltip') : t('users.resetDialog.copyTooltip')}>
                        <IconButton onClick={handleCopy} size="small" aria-label={t('users.resetDialog.copyAriaLabel')}>
                          {copied ? (
                            <CheckIcon color="success" />
                          ) : (
                            <CopyIcon />
                          )}
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ),
                }}
                sx={{ mb: 2 }}
              />
              <Alert severity="warning">
                {t('users.resetDialog.shareWarning')}
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleCloseResetDialog} variant="contained" sx={{ borderRadius: 2 }}>
            {tc('actions.done')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog */}
      <Dialog open={confirmDialog.open} onClose={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}>
        <DialogTitle sx={{ fontWeight: 700 }}>{confirmDialog.title}</DialogTitle>
        <DialogContent>
          <DialogContentText>{confirmDialog.message}</DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}>{tc('actions.cancel')}</Button>
          <Button onClick={confirmDialog.onConfirm} variant="contained" sx={{ borderRadius: 2 }}>
            {tc('actions.confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
