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
import { useTranslation } from 'react-i18next'
import { authApi } from '../api'
import { validatePassword } from '../utils/validation'

export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
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
    if (newPassword !== confirmPassword) errors.confirm = t('validation:password.mismatch')
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!token) {
      setError(t('auth.invalidResetLink'))
      return
    }

    if (!validate()) return

    setLoading(true)
    try {
      await authApi.resetPassword({ token, newPassword })
      setSuccess(true)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setError(axiosErr.response?.data?.error || t('auth.resetFailed'))
    } finally {
      setLoading(false)
    }
  }

  if (!token) {
    return (
      <Box
        sx={(theme) => ({
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
        })}
      >
        <Card sx={{ maxWidth: 420, width: '100%', mx: 2, borderRadius: 3 }}>
          <CardContent sx={{ p: 4, textAlign: 'center' }}>
            <Alert severity="error" sx={{ mb: 2 }}>
              {t('auth.invalidResetLink')}
            </Alert>
            <Link component="button" variant="body2" onClick={() => navigate('/forgot-password')}>
              {t('auth.requestNewResetLink')}
            </Link>
          </CardContent>
        </Card>
      </Box>
    )
  }

  return (
    <Box
      sx={(theme) => ({
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
      })}
    >
      <Card sx={{ maxWidth: 420, width: '100%', mx: 2, borderRadius: 3 }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box
              sx={(theme) => ({
                width: 56,
                height: 56,
                borderRadius: '16px',
                background: `linear-gradient(135deg, ${theme.palette.primary.main}, ${theme.palette.secondary.main})`,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                mb: 2,
              })}
            >
              <MedicalIcon sx={{ fontSize: 32, color: 'common.white' }} />
            </Box>
            <Typography variant="h5" fontWeight={700} color="text.primary">
              {t('auth.setNewPassword')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {t('auth.enterNewPassword')}
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
                {t('auth.passwordResetSuccess')}
              </Alert>
              <Button
                fullWidth
                variant="contained"
                size="large"
                onClick={() => navigate('/login')}
                sx={{ mt: 1, py: 1.5, borderRadius: 2 }}
              >
                {t('auth.goToSignIn')}
              </Button>
            </Box>
          ) : (
            <Box component="form" onSubmit={handleSubmit}>
              <TextField
                fullWidth
                label={t('auth.newPassword')}
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
                label={t('auth.confirmPassword')}
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
                  t('auth.resetPasswordButton')
                )}
              </Button>
              <Box sx={{ textAlign: 'center' }}>
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  onClick={() => navigate('/login')}
                >
                  {t('auth.backToSignIn')}
                </Link>
              </Box>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  )
}
