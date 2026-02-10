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
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!email.trim()) {
      setError('Email is required')
      return
    }

    setLoading(true)
    try {
      await authApi.forgotPassword({ email })
      setSuccess(true)
    } catch {
      // Still show success to prevent email enumeration
      setSuccess(true)
    } finally {
      setLoading(false)
    }
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
              Reset Password
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Enter your email to receive a reset link
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
                If an account with that email exists, a password reset link has been sent.
                Please check your inbox.
              </Alert>
              <Box sx={{ textAlign: 'center', mt: 2 }}>
                <Link
                  component="button"
                  variant="body2"
                  onClick={() => navigate('/login')}
                >
                  Back to Sign In
                </Link>
              </Box>
            </Box>
          ) : (
            <Box component="form" onSubmit={handleSubmit}>
              <TextField
                fullWidth
                label="Email Address"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                margin="normal"
                required
                autoFocus
                autoComplete="email"
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
                  'Send Reset Link'
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
