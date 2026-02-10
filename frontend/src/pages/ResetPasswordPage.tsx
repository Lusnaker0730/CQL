import { useState } from 'react'
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  Link,
  CircularProgress,
} from '@mui/material'
import { LocalHospital as MedicalIcon } from '@mui/icons-material'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../api'
import { validatePassword } from '../utils/validation'

export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ password?: string; confirm?: string }>({})

  const validate = (): boolean => {
    const errors: { password?: string; confirm?: string } = {}
    const pwErr = validatePassword(newPassword)
    if (pwErr) errors.password = pwErr
    if (newPassword !== confirmPassword) errors.confirm = 'Passwords do not match'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!token) {
      setError('Invalid reset link. Please request a new password reset.')
      return
    }

    if (!validate()) return

    setLoading(true)
    try {
      await authApi.resetPassword({ token, newPassword })
      setSuccess(true)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error || 'Failed to reset password. The link may be expired.')
    } finally {
      setLoading(false)
    }
  }

  if (!token) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, #0D7377 0%, #1B3A5C 100%)',
        }}
      >
        <Card sx={{ maxWidth: 420, width: '100%', mx: 2, borderRadius: 3 }}>
          <CardContent sx={{ p: 4, textAlign: 'center' }}>
            <Alert severity="error" sx={{ mb: 2 }}>
              Invalid reset link. Please request a new password reset.
            </Alert>
            <Link component="button" variant="body2" onClick={() => navigate('/forgot-password')}>
              Request New Reset Link
            </Link>
          </CardContent>
        </Card>
      </Box>
    )
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0D7377 0%, #1B3A5C 100%)',
      }}
    >
      <Card sx={{ maxWidth: 420, width: '100%', mx: 2, borderRadius: 3 }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box
              sx={{
                width: 56,
                height: 56,
                borderRadius: '16px',
                background: 'linear-gradient(135deg, #0D7377, #1B3A5C)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                mb: 2,
              }}
            >
              <MedicalIcon sx={{ fontSize: 32, color: 'white' }} />
            </Box>
            <Typography variant="h5" fontWeight={700} color="text.primary">
              Set New Password
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Enter your new password below
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {success ? (
            <Box>
              <Alert severity="success" sx={{ mb: 2 }}>
                Your password has been reset successfully!
              </Alert>
              <Button
                fullWidth
                variant="contained"
                size="large"
                onClick={() => navigate('/login')}
                sx={{ mt: 1, py: 1.5, borderRadius: 2 }}
              >
                Go to Sign In
              </Button>
            </Box>
          ) : (
            <Box component="form" onSubmit={handleSubmit}>
              <TextField
                fullWidth
                label="New Password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                margin="normal"
                required
                autoFocus
                autoComplete="new-password"
                error={!!fieldErrors.password}
                helperText={fieldErrors.password}
              />
              <TextField
                fullWidth
                label="Confirm Password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                margin="normal"
                required
                autoComplete="new-password"
                error={!!fieldErrors.confirm}
                helperText={fieldErrors.confirm}
              />
              <Button
                type="submit"
                fullWidth
                variant="contained"
                size="large"
                disabled={loading}
                sx={{ mt: 2, mb: 2, py: 1.5, borderRadius: 2 }}
              >
                {loading ? (
                  <CircularProgress size={24} color="inherit" />
                ) : (
                  'Reset Password'
                )}
              </Button>
              <Box sx={{ textAlign: 'center' }}>
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  onClick={() => navigate('/login')}
                >
                  Back to Sign In
                </Link>
              </Box>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  )
}
