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
import { useDispatch } from 'react-redux'
import { authApi } from '../api'
import { setCredentials } from '../store/authSlice'
import { validateUsername, validatePassword } from '../utils/validation'

export default function LoginPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch()

  const [isRegister, setIsRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({})

  const validateFields = (): boolean => {
    const errors: { username?: string; password?: string } = {}
    const usernameErr = validateUsername(username)
    if (usernameErr) errors.username = usernameErr
    if (isRegister) {
      const passwordErr = validatePassword(password)
      if (passwordErr) errors.password = passwordErr
    } else {
      if (!password) errors.password = 'Password is required'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleBlur = (field: 'username' | 'password') => {
    if (field === 'username') {
      const err = validateUsername(username)
      setFieldErrors((prev) => ({ ...prev, username: err || undefined }))
    } else {
      const err = isRegister ? validatePassword(password) : (!password ? 'Password is required' : null)
      setFieldErrors((prev) => ({ ...prev, password: err || undefined }))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!validateFields()) return

    setLoading(true)

    try {
      const response = isRegister
        ? await authApi.register({ username, password, email: email || undefined })
        : await authApi.login({ username, password })

      dispatch(setCredentials({
        token: response.token,
        username: response.username,
        role: response.role,
      }))
      navigate('/')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string; message?: string } } }
      setError(
        axiosErr.response?.data?.error ||
        axiosErr.response?.data?.message ||
        (isRegister ? 'Registration failed' : 'Invalid username or password')
      )
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
              CQL Platform
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {isRegister ? 'Create your account' : 'Sign in to continue'}
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onBlur={() => handleBlur('username')}
              margin="normal"
              required
              autoFocus
              autoComplete="username"
              error={!!fieldErrors.username}
              helperText={fieldErrors.username}
            />
            <TextField
              fullWidth
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onBlur={() => handleBlur('password')}
              margin="normal"
              required
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              error={!!fieldErrors.password}
              helperText={fieldErrors.password}
            />
            {isRegister && (
              <TextField
                fullWidth
                label="Email (optional)"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                margin="normal"
                autoComplete="email"
              />
            )}
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
              ) : isRegister ? (
                'Register'
              ) : (
                'Sign In'
              )}
            </Button>
            <Box sx={{ textAlign: 'center' }}>
              <Link
                component="button"
                type="button"
                variant="body2"
                onClick={() => {
                  setIsRegister(!isRegister)
                  setError('')
                  setFieldErrors({})
                }}
              >
                {isRegister
                  ? 'Already have an account? Sign in'
                  : "Don't have an account? Register"}
              </Link>
            </Box>
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
