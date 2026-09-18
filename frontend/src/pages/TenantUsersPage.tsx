import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { tenantUserApi } from '../api'
import type { AdminCreateUserRequest } from '../types'
import { getStoredUsername } from '../utils/validation'

const ROLES = ['USER', 'DEPARTMENT_ADMIN', 'ADMIN'] as const

// Mirrors the backend TenantCreateUserRequest.password rule so the dialog rejects a
// non-compliant password up front (a >=8 length alone passed the button but the server
// still 400s without upper/lower/digit — which used to surface as a misleading
// "username taken" message).
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/

export default function TenantUsersPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const currentUsername = getStoredUsername()

  // Reset password (setup link) state
  const [setupLink, setSetupLink] = useState<string | null>(null)
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
    queryKey: ['tenant', 'users'],
    queryFn: tenantUserApi.listUsers,
  })

  const refreshUsers = () => {
    queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] })
  }

  const roleLabel = (role: string) => {
    if (role === 'ADMIN') return t('tenantUsers.roles.admin')
    if (role === 'DEPARTMENT_ADMIN') return t('tenantUsers.roles.departmentAdmin')
    return t('tenantUsers.roles.user')
  }

  // --- Create User ---
  const handleCreateUser = async () => {
    setError('')
    setCreateLoading(true)
    try {
      await tenantUserApi.createUser(createForm)
      setCreateDialogOpen(false)
      setCreateForm({ username: '', password: '', email: '', role: 'USER' })
      setSuccess(t('tenantUsers.success.userCreated'))
      refreshUsers()
    } catch (err: unknown) {
      const axiosErr = err as {
        response?: { data?: { message?: string; details?: string[]; error?: string }; status?: number }
      }
      const status = axiosErr.response?.status
      const data = axiosErr.response?.data
      if (status === 409) {
        // Only a genuine duplicate username is a conflict.
        setError(t('tenantUsers.errors.usernameExists'))
      } else if (status === 400) {
        // Validation failure (most often the password policy). Show the server's
        // specific reason when present, else a localized hint.
        const detail = data?.details?.length ? data.details.join('; ') : data?.message
        setError(detail || t('tenantUsers.errors.invalidInput'))
      } else {
        setError(data?.message || t('tenantUsers.errors.createFailed'))
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
      const result = await tenantUserApi.resetUserPassword(userId)
      setSetupLink(result.setupLink)
      setResetDialogOpen(true)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error || t('tenantUsers.errors.resetFailed'))
    } finally {
      setResetLoading(null)
    }
  }

  // --- Role Change ---
  const handleRoleChange = (userId: number, username: string, newRole: string) => {
    setConfirmDialog({
      open: true,
      title: t('tenantUsers.confirmDialog.changeRoleTitle'),
      message: t('tenantUsers.confirmDialog.changeRoleMessage', { username, role: roleLabel(newRole) }),
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await tenantUserApi.updateUserRole(userId, newRole)
          setSuccess(t('tenantUsers.success.roleUpdated', { username, role: roleLabel(newRole) }))
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || t('tenantUsers.errors.updateRoleFailed'))
        } finally {
          setActionLoading(null)
        }
      },
    })
  }

  // --- Enable/Disable ---
  const handleToggleEnabled = (userId: number, username: string, currentlyEnabled: boolean) => {
    setConfirmDialog({
      open: true,
      title: currentlyEnabled ? t('tenantUsers.confirmDialog.disableUserTitle') : t('tenantUsers.confirmDialog.enableUserTitle'),
      message: currentlyEnabled
        ? t('tenantUsers.confirmDialog.disableMessage', { username })
        : t('tenantUsers.confirmDialog.enableMessage', { username }),
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await tenantUserApi.updateUserEnabled(userId, !currentlyEnabled)
          setSuccess(currentlyEnabled
            ? t('tenantUsers.success.userDisabled', { username })
            : t('tenantUsers.success.userEnabled', { username }))
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || t('tenantUsers.errors.toggleFailed'))
        } finally {
          setActionLoading(null)
        }
      },
    })
  }

  const handleCopy = async () => {
    if (setupLink) {
      await navigator.clipboard.writeText(setupLink)
      setCopied(true)
      setTimeout(() => setCopied(false), COPY_FEEDBACK_TIMEOUT_MS)
    }
  }

  const handleCloseResetDialog = () => {
    setResetDialogOpen(false)
    setSetupLink(null)
    setCopied(false)
  }

  const isCurrentUser = (username: string) => currentUsername === username

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {t('tenantUsers.title')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<PersonAddIcon />}
          onClick={() => setCreateDialogOpen(true)}
          sx={{ borderRadius: 2 }}
        >
          {t('tenantUsers.createUser')}
        </Button>
      </Box>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
        {t('tenantUsers.subtitle')}
      </Typography>
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
                <TableCell sx={{ fontWeight: 600 }}>{t('tenantUsers.columns.username')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('tenantUsers.columns.email')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('tenantUsers.columns.role')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('tenantUsers.columns.status')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('tenantUsers.columns.created')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">{t('tenantUsers.columns.actions')}</TableCell>
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
                    <Typography sx={{ color: 'text.secondary' }}>{t('tenantUsers.noUsersFound')}</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                users.map((user) => (
                  <TableRow key={user.id} hover>
                    <TableCell>
                      <Typography sx={{ fontWeight: 500 }}>
                        {user.username}
                        {isCurrentUser(user.username) && (
                          <Chip label={t('tenantUsers.youChip')} size="small" sx={{ ml: 1 }} variant="outlined" />
                        )}
                        {user.authProvider === 'OKTA' && (
                          <Chip label={tc('auth.authProviderOkta')} size="small" sx={{ ml: 1 }} color="info" variant="outlined" />
                        )}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                        {user.email || '--'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <FormControl size="small" sx={{ minWidth: 150 }}>
                        <Select
                          value={user.role}
                          onChange={(e) => handleRoleChange(user.id, user.username, e.target.value)}
                          disabled={isCurrentUser(user.username) || actionLoading === user.id}
                          size="small"
                          sx={{ fontSize: '0.875rem' }}
                        >
                          {ROLES.map((r) => (
                            <MenuItem key={r} value={r}>{roleLabel(r)}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {user.forcePasswordChange ? (
                          <Chip label={t('tenantUsers.status.forceChange')} size="small" color="warning" variant="outlined" />
                        ) : user.enabled ? (
                          <Chip label={t('tenantUsers.status.active')} size="small" color="success" variant="outlined" />
                        ) : (
                          <Chip label={t('tenantUsers.status.disabled')} size="small" color="error" variant="outlined" />
                        )}
                        <Tooltip title={isCurrentUser(user.username) ? t('tenantUsers.tooltips.cannotDisableSelf') : (user.enabled ? t('tenantUsers.tooltips.disableUser') : t('tenantUsers.tooltips.enableUser'))}>
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
                      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                        {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '--'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      {user.authProvider !== 'OKTA' && (
                        <Tooltip title={t('tenantUsers.tooltips.resetPassword')}>
                          <span>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={resetLoading === user.id ? <CircularProgress size={16} /> : <ResetIcon />}
                              onClick={() => handleResetPassword(user.id)}
                              disabled={resetLoading !== null || actionLoading === user.id}
                            >
                              {t('tenantUsers.resetPasswordButton')}
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
        <DialogTitle sx={{ fontWeight: 700 }}>{t('tenantUsers.createDialog.title')}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField
              label={t('tenantUsers.createDialog.usernameLabel')}
              value={createForm.username}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, username: e.target.value }))}
              fullWidth
              required
              autoFocus
              slotProps={{ htmlInput: { maxLength: 50 } }}
            />
            <TextField
              label={t('tenantUsers.createDialog.passwordLabel')}
              type="password"
              value={createForm.password}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, password: e.target.value }))}
              fullWidth
              required
              error={createForm.password.length > 0 && !PASSWORD_RE.test(createForm.password)}
              helperText={t('tenantUsers.createDialog.passwordHelperText')}
              slotProps={{ htmlInput: { maxLength: 100 } }}
            />
            <TextField
              label={t('tenantUsers.createDialog.emailLabel')}
              type="email"
              value={createForm.email}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, email: e.target.value }))}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <FormControl fullWidth>
              <InputLabel>{t('tenantUsers.createDialog.roleLabel')}</InputLabel>
              <Select
                value={createForm.role}
                label={t('tenantUsers.createDialog.roleLabel')}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, role: e.target.value as AdminCreateUserRequest['role'] }))}
              >
                {ROLES.map((r) => (
                  <MenuItem key={r} value={r}>{roleLabel(r)}</MenuItem>
                ))}
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
            disabled={createLoading || !createForm.username || !PASSWORD_RE.test(createForm.password)}
            startIcon={createLoading ? <CircularProgress size={16} /> : undefined}
            sx={{ borderRadius: 2 }}
          >
            {t('tenantUsers.createDialog.createButton')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Setup Link Dialog */}
      <Dialog open={resetDialogOpen} onClose={handleCloseResetDialog} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{t('tenantUsers.resetDialog.title')}</DialogTitle>
        <DialogContent>
          {setupLink && (
            <Box>
              <Alert severity="info" sx={{ mb: 2 }}>
                {t('tenantUsers.resetDialog.info')}
              </Alert>
              <TextField
                fullWidth
                value={setupLink}
                sx={{ mb: 2 }}
                slotProps={{
                  input: {
                    readOnly: true,
                    sx: { fontFamily: 'monospace', fontSize: '0.9rem' },
                    endAdornment: (
                      <InputAdornment position="end">
                        <Tooltip title={copied ? t('tenantUsers.resetDialog.copiedTooltip') : t('tenantUsers.resetDialog.copyTooltip')}>
                          <IconButton onClick={handleCopy} size="small" aria-label={t('tenantUsers.resetDialog.copyAriaLabel')}>
                            {copied ? <CheckIcon color="success" /> : <CopyIcon />}
                          </IconButton>
                        </Tooltip>
                      </InputAdornment>
                    ),
                  },
                }}
              />
              <Alert severity="warning">
                {t('tenantUsers.resetDialog.shareWarning')}
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
