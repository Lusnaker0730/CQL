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
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  LockReset as ResetIcon,
  CheckCircle as CheckIcon,
} from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../api'
import type { AdminResetPasswordResponse } from '../types'

export default function AdminUsersPage() {
  const [resetResult, setResetResult] = useState<AdminResetPasswordResponse | null>(null)
  const [resetDialogOpen, setResetDialogOpen] = useState(false)
  const [resetLoading, setResetLoading] = useState<number | null>(null)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState('')

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.listUsers,
  })

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

  const handleCopy = async () => {
    if (resetResult) {
      await navigator.clipboard.writeText(resetResult.temporaryPassword)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const handleCloseDialog = () => {
    setResetDialogOpen(false)
    setResetResult(null)
    setCopied(false)
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>
        User Management
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>
          {error}
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
                      <Typography fontWeight={500}>{user.username}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {user.email || '--'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={user.role}
                        size="small"
                        color={user.role === 'ADMIN' ? 'primary' : 'default'}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      {user.forcePasswordChange ? (
                        <Chip label="Force Change" size="small" color="warning" variant="outlined" />
                      ) : user.enabled ? (
                        <Chip label="Active" size="small" color="success" variant="outlined" />
                      ) : (
                        <Chip label="Disabled" size="small" color="error" variant="outlined" />
                      )}
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
                            disabled={resetLoading !== null}
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

      {/* Temporary Password Dialog */}
      <Dialog open={resetDialogOpen} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
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
          <Button onClick={handleCloseDialog} variant="contained" sx={{ borderRadius: 2 }}>
            Done
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
