import { useState } from 'react'
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
import { safeParseJson } from '../utils/validation'

export default function AdminUsersPage() {
  const queryClient = useQueryClient()
  const currentUser = safeParseJson<{ username?: string }>(localStorage.getItem('user'), {})

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
      setSuccess('User created successfully')
      refreshUsers()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string }; status?: number } }
      if (axiosErr.response?.status === 400) {
        setError('Username already exists or invalid input')
      } else {
        setError(axiosErr.response?.data?.error || 'Failed to create user')
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
      setError(axiosErr.response?.data?.error || 'Failed to reset password')
    } finally {
      setResetLoading(null)
    }
  }

  // --- Role Change ---
  const handleRoleChange = (userId: number, username: string, newRole: string) => {
    setConfirmDialog({
      open: true,
      title: 'Change User Role',
      message: `Change ${username}'s role to ${newRole}?`,
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await adminApi.updateUserRole(userId, newRole)
          setSuccess(`${username}'s role updated to ${newRole}`)
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || 'Failed to update role')
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
      title: `${currentlyEnabled ? 'Disable' : 'Enable'} User`,
      message: `Are you sure you want to ${action} ${username}?`,
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }))
        setError('')
        setActionLoading(userId)
        try {
          await adminApi.updateUserEnabled(userId, !currentlyEnabled)
          setSuccess(`${username} has been ${action}d`)
          refreshUsers()
        } catch (err: unknown) {
          const axiosErr = err as { response?: { data?: { error?: string } } }
          setError(axiosErr.response?.data?.error || `Failed to ${action} user`)
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
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const handleCloseResetDialog = () => {
    setResetDialogOpen(false)
    setResetResult(null)
    setCopied(false)
  }

  const isCurrentUser = (username: string) => currentUser?.username === username

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>
          User Management
        </Typography>
        <Button
          variant="contained"
          startIcon={<PersonAddIcon />}
          onClick={() => setCreateDialogOpen(true)}
          sx={{ borderRadius: 2 }}
        >
          Create User
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
                <TableCell sx={{ fontWeight: 600 }}>Username</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Email</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Role</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Created</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">Actions</TableCell>
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
                    <Typography color="text.secondary">No users found</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                users.map((user) => (
                  <TableRow key={user.id} hover>
                    <TableCell>
                      <Typography fontWeight={500}>
                        {user.username}
                        {isCurrentUser(user.username) && (
                          <Chip label="You" size="small" sx={{ ml: 1 }} variant="outlined" />
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
                          <MenuItem value="ADMIN">ADMIN</MenuItem>
                          <MenuItem value="USER">USER</MenuItem>
                        </Select>
                      </FormControl>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {user.forcePasswordChange ? (
                          <Chip label="Force Change" size="small" color="warning" variant="outlined" />
                        ) : user.enabled ? (
                          <Chip label="Active" size="small" color="success" variant="outlined" />
                        ) : (
                          <Chip label="Disabled" size="small" color="error" variant="outlined" />
                        )}
                        <Tooltip title={isCurrentUser(user.username) ? "Cannot disable yourself" : (user.enabled ? "Disable user" : "Enable user")}>
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
                      <Tooltip title="Reset user's password">
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
                            Reset Password
                          </Button>
                        </span>
                      </Tooltip>
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
        <DialogTitle sx={{ fontWeight: 700 }}>Create User</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField
              label="Username"
              value={createForm.username}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, username: e.target.value }))}
              fullWidth
              required
              autoFocus
              inputProps={{ maxLength: 50 }}
            />
            <TextField
              label="Password"
              type="password"
              value={createForm.password}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, password: e.target.value }))}
              fullWidth
              required
              helperText="Minimum 8 characters, must include uppercase, lowercase, and a number"
              inputProps={{ maxLength: 100 }}
            />
            <TextField
              label="Email"
              type="email"
              value={createForm.email}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, email: e.target.value }))}
              fullWidth
              inputProps={{ maxLength: 200 }}
            />
            <FormControl fullWidth>
              <InputLabel>Role</InputLabel>
              <Select
                value={createForm.role}
                label="Role"
                onChange={(e) => setCreateForm((prev) => ({ ...prev, role: e.target.value as 'ADMIN' | 'USER' }))}
              >
                <MenuItem value="USER">USER</MenuItem>
                <MenuItem value="ADMIN">ADMIN</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCreateDialogOpen(false)} disabled={createLoading}>
            Cancel
          </Button>
          <Button
            onClick={handleCreateUser}
            variant="contained"
            disabled={createLoading || !createForm.username || createForm.password.length < 8}
            startIcon={createLoading ? <CircularProgress size={16} /> : undefined}
            sx={{ borderRadius: 2 }}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Temporary Password Dialog */}
      <Dialog open={resetDialogOpen} onClose={handleCloseResetDialog} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Password Reset Successful</DialogTitle>
        <DialogContent>
          {resetResult && (
            <Box>
              <Alert severity="info" sx={{ mb: 2 }}>
                {resetResult.message}
              </Alert>
              <Typography variant="body2" sx={{ mb: 1 }}>
                User: <strong>{resetResult.username}</strong>
              </Typography>
              <Typography variant="body2" sx={{ mb: 1 }}>
                Temporary Password:
              </Typography>
              <TextField
                fullWidth
                value={resetResult.temporaryPassword}
                InputProps={{
                  readOnly: true,
                  sx: { fontFamily: 'monospace', fontSize: '1.1rem' },
                  endAdornment: (
                    <InputAdornment position="end">
                      <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'}>
                        <IconButton onClick={handleCopy} size="small">
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
                Share this password securely with the user. They will be required to change it upon next login.
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleCloseResetDialog} variant="contained" sx={{ borderRadius: 2 }}>
            Done
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
          <Button onClick={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}>Cancel</Button>
          <Button onClick={confirmDialog.onConfirm} variant="contained" sx={{ borderRadius: 2 }}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
