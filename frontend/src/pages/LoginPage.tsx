import { useState, useEffect } from 'react'
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
  Divider,
} from '@mui/material'
import { LocalHospital as MedicalIcon } from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api'
import { setCredentials } from '../store/authSlice'
import { validateUsername, validatePassword } from '../utils/validation'

export default function LoginPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const { t } = useTranslation()

  const [isRegister, setIsRegister] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  // PAT-157 — info banner for the register-pending response (self-registration
  // disabled or username taken; backend returns 200 with a message rather than
  // an error). Distinct from `error` because we don't want it styled as a failure.
  const [info, setInfo] = useState('')
  const [loading, setLoading] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({})
  const [oktaEnabled, setOktaEnabled] = useState(false)
  const [oktaConfig, setOktaConfig] = useState<{ authorizationEndpoint?: string; clientId?: string; scopes?: string } | null>(null)

  useEffect(() => {
    authApi.getOktaConfig().then((config) => {
      if (config.enabled) {
        setOktaEnabled(true)
        setOktaConfig(config)
      }
    }).catch(() => {
      // Okta not available, ignore
    })
  }, [])

  const validateFields = (): boolean => {
    const errors: { username?: string; password?: string } = {}
    const usernameErr = validateUsername(username)
    if (usernameErr) errors.username = usernameErr
    if (isRegister) {
      const passwordErr = validatePassword(password)
      if (passwordErr) errors.password = passwordErr
    } else {
      if (!password) errors.password = t('validation:password.required')
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleBlur = (field: 'username' | 'password') => {
    if (field === 'username') {
      const err = validateUsername(username)
      setFieldErrors((prev) => ({ ...prev, username: err || undefined }))
    } else {
      const err = isRegister ? validatePassword(password) : (!password ? t('validation:password.required') : null)
      setFieldErrors((prev) => ({ ...prev, password: err || undefined }))
    }
  }

  const handleOktaLogin = () => {
    if (!oktaConfig) return
    const state = crypto.randomUUID()
    const nonce = crypto.randomUUID()
    const redirectUri = window.location.origin + '/auth/okta/callback'
    sessionStorage.setItem('okta_state', state)
    sessionStorage.setItem('okta_nonce', nonce)
    sessionStorage.setItem('okta_redirect_uri', redirectUri)
    const params = new URLSearchParams({
      client_id: oktaConfig.clientId || '',
      response_type: 'code',
      scope: oktaConfig.scopes || 'openid profile email',
      redirect_uri: redirectUri,
      state,
      nonce,
    })
    window.location.href = `${oktaConfig.authorizationEndpoint}?${params.toString()}`
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setInfo('')

    if (!validateFields()) return

    setLoading(true)

    try {
      const response = isRegister
        ? await authApi.register({ username, password, email: email || undefined })
        : await authApi.login({ username, password })

      // PAT-157 — register may return a plain { message } envelope when self-
      // registration is disabled (or the username is already taken). In that
      // case we surface the pending-approval message instead of dispatching
      // credentials with an undefined token.
      if (!('token' in response) || !response.token) {
        const pending = (response as { message?: string }).message
        setInfo(pending || t('auth.registrationPending'))
        return
      }

      dispatch(setCredentials({
        token: response.token,
        username: response.username,
        role: response.role,
        forcePasswordChange: response.forcePasswordChange,
      }))
      navigate('/')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string; message?: string } } }
      setError(
        axiosErr.response?.data?.error ||
        axiosErr.response?.data?.message ||
        (isRegister ? t('auth.registrationFailed') : t('auth.invalidCredentials'))
      )
    } finally {
      setLoading(false)
    }
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
              {t('app.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {isRegister ? t('auth.createAccount') : t('auth.signInSubtitle')}
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}
          {info && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {info}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label={t('auth.username')}
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
              label={t('auth.password')}
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
                label={t('auth.email')}
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
                t('auth.register')
              ) : (
                t('auth.signIn')
              )}
            </Button>
            {!isRegister && (
              <Box sx={{ textAlign: 'center', mb: 1 }}>
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  onClick={() => navigate('/forgot-password')}
                >
                  {t('auth.forgotPassword')}
                </Link>
              </Box>
            )}
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
                  ? t('auth.alreadyHaveAccount')
                  : t('auth.noAccount')}
              </Link>
            </Box>
          {oktaEnabled && (
            <>
              <Divider sx={{ my: 2 }}>{t('auth.or')}</Divider>
              <Button
                fullWidth
                variant="outlined"
                size="large"
                onClick={handleOktaLogin}
                sx={{ py: 1.5, borderRadius: 2 }}
              >
                {t('auth.loginWithOkta')}
              </Button>
            </>
          )}
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
