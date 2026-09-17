import { useEffect, useState } from 'react'
import { Box, Card, CardContent, Typography, CircularProgress, Alert, Button } from '@mui/material'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api'
import { setCredentials } from '../store/authSlice'

export default function OktaCallbackPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const [error, setError] = useState('')

  useEffect(() => {
    const processCallback = async () => {
      const code = searchParams.get('code')
      const state = searchParams.get('state')

      if (!code) {
        setError(t('auth.ssoMissingCode'))
        return
      }

      // Validate state matches what we stored (CSRF protection)
      const storedState = sessionStorage.getItem('okta_state')
      if (!state || state !== storedState) {
        setError(t('auth.ssoStateMismatch'))
        return
      }

      const nonce = sessionStorage.getItem('okta_nonce') || undefined
      const redirectUri = sessionStorage.getItem('okta_redirect_uri') || window.location.origin + '/auth/okta/callback'

      // Clean up sessionStorage
      sessionStorage.removeItem('okta_state')
      sessionStorage.removeItem('okta_nonce')
      sessionStorage.removeItem('okta_redirect_uri')

      try {
        const response = await authApi.oktaCallback({
          code,
          redirectUri,
          nonce,
        })

        dispatch(setCredentials({
          token: response.token,
          username: response.username,
          role: response.role,
          forcePasswordChange: response.forcePasswordChange,
        }))
        navigate('/')
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { error?: string } } }
        setError(axiosErr.response?.data?.error || t('auth.ssoFailed'))
      }
    }

    processCallback()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (error) {
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
            <Alert severity="error" sx={{ mb: 3 }}>
              {error}
            </Alert>
            <Button
              variant="contained"
              onClick={() => navigate('/login')}
              sx={{ borderRadius: 2 }}
            >
              {t('auth.backToSignIn')}
            </Button>
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
        <CardContent sx={{ p: 4, textAlign: 'center' }}>
          <CircularProgress sx={{ mb: 2 }} />
          <Typography variant="body1" sx={{
            color: "text.secondary"
          }}>
            {t('auth.ssoProcessing')}
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
