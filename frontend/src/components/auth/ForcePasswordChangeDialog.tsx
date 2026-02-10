import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Alert,
  Typography,
  CircularProgress,
} from '@mui/material'
import { useDispatch } from 'react-redux'
import { authApi } from '../../api'
import { clearForcePasswordChange } from '../../store/authSlice'
import { validatePassword } from '../../utils/validation'

interface ForcePasswordChangeDialogProps {
  open: boolean
}

export default function ForcePasswordChangeDialog({ open }: ForcePasswordChangeDialogProps) {
  const dispatch = useDispatch()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{
    current?: string
    password?: string
    confirm?: string
  }>({})

  const validate = (): boolean => {
    const errors: { current?: string; password?: string; confirm?: string } = {}
    if (!currentPassword) errors.current = 'Current password is required'
    const pwErr = validatePassword(newPassword)
    if (pwErr) errors.password = pwErr
    if (newPassword !== confirmPassword) errors.confirm = 'Passwords do not match'
    if (currentPassword && newPassword && currentPassword === newPassword) {
      errors.password = 'New password must be different from current password'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!validate()) return

    setLoading(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword })
      dispatch(clearForcePasswordChange())
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error || 'Failed to change password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog
      open={open}
      maxWidth="sm"
      fullWidth
      disableEscapeKeyDown
      onClose={(_e, reason) => {
        if (reason === 'backdropClick') return
      }}
    >
      <DialogTitle sx={{ fontWeight: 700 }}>Password Change Required</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Your password has been reset by an administrator. You must set a new password before continuing.
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <form id="force-change-form" onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label="Current / Temporary Password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            margin="normal"
            required
            autoFocus
            autoComplete="current-password"
            error={!!fieldErrors.current}
            helperText={fieldErrors.current}
          />
          <TextField
            fullWidth
            label="New Password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            margin="normal"
            required
            autoComplete="new-password"
            error={!!fieldErrors.password}
            helperText={fieldErrors.password}
          />
          <TextField
            fullWidth
            label="Confirm New Password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            margin="normal"
            required
            autoComplete="new-password"
            error={!!fieldErrors.confirm}
            helperText={fieldErrors.confirm}
          />
        </form>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button
          type="submit"
          form="force-change-form"
          variant="contained"
          disabled={loading}
          sx={{ borderRadius: 2 }}
        >
          {loading ? <CircularProgress size={20} color="inherit" /> : 'Change Password'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
