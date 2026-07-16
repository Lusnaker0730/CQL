import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  alpha,
  Box,
  Button,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Typography,
  useTheme,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ErrorIcon from '@mui/icons-material/Error'
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart'
import { Helmet } from 'react-helmet-async'
import { statusApi } from '../api/statusApi'

/**
 * PAT-209 — public service status page. Polls the anonymous /api/status endpoint and shows
 * coarse reachability (operational / degraded) plus per-component up/down. No auth required.
 */
export default function StatusPage() {
  const theme = useTheme()
  const navigate = useNavigate()
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()

  const { data, isLoading, isError, dataUpdatedAt } = useQuery({
    queryKey: ['platform-status'],
    queryFn: statusApi.getStatus,
    refetchInterval: 30_000,
    retry: false,
  })

  // If the request fails, the platform is unreachable → treat as an outage.
  const overall: 'operational' | 'degraded' | 'outage' = isError
    ? 'outage'
    : data?.status ?? 'operational'
  const isUp = overall === 'operational'

  return (
    <>
      <Helmet>
        <title>{t('status.pageTitle')} — {tc('app.title')}</title>
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
        <Box
          sx={{
            background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
            px: 3,
            py: 1.5,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/login')}
            sx={{ color: alpha(theme.palette.common.white, 0.9), textTransform: 'none', mr: 2 }}
          >
            {t('learn.backToHome')}
          </Button>
          <MonitorHeartIcon sx={{ color: 'common.white', fontSize: 22, mr: 1 }} />
          <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'common.white' }}>
            {t('status.pageTitle')}
          </Typography>
        </Box>

        <Container maxWidth="sm" sx={{ py: 5, flex: 1 }}>
          {isLoading ? (
            <Stack sx={{ alignItems: 'center', py: 6 }}>
              <CircularProgress />
            </Stack>
          ) : (
            <>
              <Paper
                variant="outlined"
                sx={{
                  p: 3,
                  mb: 3,
                  borderColor: isUp ? 'success.main' : 'error.main',
                  bgcolor: (th) => alpha(isUp ? th.palette.success.main : th.palette.error.main, 0.06),
                }}
              >
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                  {isUp ? (
                    <CheckCircleIcon color="success" sx={{ fontSize: 32 }} />
                  ) : (
                    <ErrorIcon color="error" sx={{ fontSize: 32 }} />
                  )}
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {t(`status.overall.${overall}`)}
                  </Typography>
                </Stack>
              </Paper>

              <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                {t('status.components')}
              </Typography>
              <Stack spacing={1}>
                {(data?.components ?? [{ name: 'api', ok: false }, { name: 'database', ok: false }]).map((c) => (
                  <Paper key={c.name} variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Typography variant="body2">{t(`status.component.${c.name}`, c.name)}</Typography>
                    {c.ok ? (
                      <CheckCircleIcon color="success" fontSize="small" />
                    ) : (
                      <ErrorIcon color="error" fontSize="small" />
                    )}
                  </Paper>
                ))}
              </Stack>

              {dataUpdatedAt > 0 && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
                  {t('status.lastChecked', { time: new Date(dataUpdatedAt).toLocaleTimeString() })}
                </Typography>
              )}
            </>
          )}
        </Container>
      </Box>
    </>
  )
}
