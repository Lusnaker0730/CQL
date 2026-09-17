import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Alert,
  CircularProgress,
} from '@mui/material'
import { LocalHospital as ClinicIcon } from '@mui/icons-material'
import { clinicApplicationApi } from '../api/clinicApplicationApi'

const CODE_PATTERN = /^[a-z0-9][a-z0-9-]{1,49}$/

/**
 * Public clinic application form (#700). Anonymous — the backend responds with a
 * uniform message regardless of conflicts (anti-enumeration), so the page only
 * distinguishes "received" from validation/transport errors.
 */
export default function ClinicApplyPage() {
  const { t } = useTranslation('landing')
  const navigate = useNavigate()

  const [form, setForm] = useState({
    clinicName: '',
    tenantCode: '',
    adminUsername: '',
    adminEmail: '',
  })
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const codeValid = form.tenantCode === '' || CODE_PATTERN.test(form.tenantCode)
  const emailValid = form.adminEmail === '' || /.+@.+\..+/.test(form.adminEmail)
  const complete =
    form.clinicName !== '' &&
    form.tenantCode !== '' &&
    form.adminUsername.length >= 3 &&
    form.adminEmail !== '' &&
    codeValid &&
    emailValid

  const handleSubmit = async () => {
    setLoading(true)
    setError(null)
    try {
      await clinicApplicationApi.submit(form)
      setSubmitted(true)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('apply.failed'))
    } finally {
      setLoading(false)
    }
  }

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value })

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        p: 2,
      }}
    >
      <Paper sx={{ p: 4, maxWidth: 480, width: '100%' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <ClinicIcon color="primary" />
          <Typography variant="h5">{t('apply.title')}</Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {t('apply.subtitle')}
        </Typography>

        {submitted ? (
          <>
            <Alert severity="success" sx={{ mb: 2 }}>
              {t('apply.successMessage')}
            </Alert>
            <Button fullWidth variant="outlined" onClick={() => navigate('/login')}>
              {t('apply.backToHome')}
            </Button>
          </>
        ) : (
          <>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            <TextField
              fullWidth
              margin="dense"
              label={t('apply.clinicName')}
              value={form.clinicName}
              onChange={set('clinicName')}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <TextField
              fullWidth
              margin="dense"
              label={t('apply.tenantCode')}
              value={form.tenantCode}
              error={!codeValid}
              helperText={t('apply.tenantCodeHelp')}
              onChange={set('tenantCode')}
              slotProps={{ htmlInput: { maxLength: 50 } }}
            />
            <TextField
              fullWidth
              margin="dense"
              label={t('apply.adminUsername')}
              value={form.adminUsername}
              helperText={t('apply.adminUsernameHelp')}
              onChange={set('adminUsername')}
              slotProps={{ htmlInput: { maxLength: 50 } }}
            />
            <TextField
              fullWidth
              margin="dense"
              type="email"
              label={t('apply.adminEmail')}
              value={form.adminEmail}
              error={!emailValid}
              onChange={set('adminEmail')}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <Button
              fullWidth
              variant="contained"
              size="large"
              sx={{ mt: 2 }}
              disabled={!complete || loading}
              onClick={handleSubmit}
            >
              {loading ? <CircularProgress size={22} /> : t('apply.submit')}
            </Button>
          </>
        )}
      </Paper>
    </Box>
  )
}
