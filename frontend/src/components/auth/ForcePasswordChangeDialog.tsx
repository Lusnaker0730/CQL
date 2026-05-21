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
import { useTranslation } from 'react-i18next'
import { authApi } from '../../api'
import { clearForcePasswordChange } from '../../store/authSlice'
import { validatePassword } from '../../utils/validation'
import { extractApiError } from '../../utils/errorUtils'

interface ForcePasswordChangeDialogProps {
  open: boolean
}

export default function ForcePasswordChangeDialog({ open }: ForcePasswordChangeDialogProps) {
  const dispatch = useDispatch()
  const { t } = useTranslation()
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
    if (!currentPassword) errors.current = t('validation:password.required')
    const pwErr = validatePassword(newPassword)
    if (pwErr) errors.password = pwErr
    if (newPassword !== confirmPassword) errors.confirm = t('validation:password.mismatch')
    if (currentPassword && newPassword && currentPassword === newPassword) {
      errors.password = t('validation:password.sameasCurrent')
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
      // PAT-147: clear plain-text passwords from React state once they're no
      // longer needed. The dialog will close as soon as
      // clearForcePasswordChange dispatches, but until React unmounts the
      // component the values would otherwise sit in the heap (visible via
      // React DevTools) for an arbitrary tick.
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setFieldErrors({})
      dispatch(clearForcePasswordChange())
    } catch (err: unknown) {
      // PAT-147: extractApiError unwraps both AxiosError and structured server
      // errors — replaces the previous bare `err.response?.data?.error` lookup
      // that missed non-Axios reject paths (matches the PAT-144 hooks pattern).
      setError(extractApiError(err) || t('auth.changePasswordFailed'))
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
      <DialogTitle sx={{ fontWeight: 700 }}>{t('auth.passwordChangeRequired')}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('auth.passwordChangeDescription')}
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <form id="force-change-form" onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label={t('auth.currentPassword')}
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
            label={t('auth.newPassword')}
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
            label={t('auth.confirmNewPassword')}
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
          {loading ? <CircularProgress size={20} color="inherit" /> : t('auth.changePassword')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
