import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Chip,
  Stack,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from '@mui/material'
import { Error as ErrorIcon } from '@mui/icons-material'
import type { CdsCard } from '../../types'

interface CriticalCardDialogProps {
  open: boolean
  card: CdsCard
  onAccept: () => void
  onOverride: (reason: string) => void
}

export default function CriticalCardDialog({
  open,
  card,
  onAccept,
  onOverride,
}: CriticalCardDialogProps) {
  const { t } = useTranslation('cds')
  const [showOverrideForm, setShowOverrideForm] = useState(false)
  const [selectedReason, setSelectedReason] = useState('')
  const [customReason, setCustomReason] = useState('')

  const hasPresetReasons = card.overrideReasons && card.overrideReasons.length > 0

  const handleOverrideSubmit = () => {
    const reason = hasPresetReasons ? selectedReason : customReason
    if (reason.trim()) {
      onOverride(reason)
      setShowOverrideForm(false)
      setSelectedReason('')
      setCustomReason('')
    }
  }

  const handleAccept = () => {
    setShowOverrideForm(false)
    setSelectedReason('')
    setCustomReason('')
    onAccept()
  }

  return (
    <Dialog
      open={open}
      // Critical cards must be acted on (Accept / Override). Block both Esc
      // and backdrop-click via the canonical onClose-reason guard rather than
      // a backdrop preventDefault hack.
      disableEscapeKeyDown
      onClose={(_event, reason) => {
        if (reason === 'backdropClick') return
        // No other dismissal paths today, but if MUI adds one we want the
        // dialog to stay open until the user accepts or overrides.
      }}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>
        <Stack direction="row" spacing={1} alignItems="center">
          <ErrorIcon color="error" />
          <Typography variant="h6">{t('critical.alertTitle')}</Typography>
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Chip label={card.indicator} size="small" color="error" />
            <Typography variant="subtitle1" fontWeight="bold">
              {card.summary}
            </Typography>
          </Stack>

          {card.detail && (
            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>
              {card.detail}
            </Typography>
          )}

          {card.source && (
            <Typography variant="caption" color="text.secondary">
              {card.source.label}
            </Typography>
          )}

          {showOverrideForm && (
            <Stack spacing={2} sx={{ mt: 1, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
              <Typography variant="subtitle2">{t('critical.overrideReason')}</Typography>
              {hasPresetReasons ? (
                <FormControl size="small" fullWidth>
                  <InputLabel>{t('critical.selectReason')}</InputLabel>
                  <Select
                    value={selectedReason}
                    onChange={(e) => setSelectedReason(e.target.value)}
                    label={t('critical.selectReason')}
                  >
                    {card.overrideReasons!.map((reason, i) => (
                      <MenuItem key={`${reason.display}-${i}`} value={reason.display || ''}>
                        {reason.display}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              ) : (
                <TextField
                  label={t('critical.overrideReason')}
                  placeholder={t('critical.overridePlaceholder')}
                  value={customReason}
                  onChange={(e) => setCustomReason(e.target.value)}
                  size="small"
                  fullWidth
                  multiline
                  rows={2}
                />
              )}
              <Button
                variant="contained"
                color="warning"
                onClick={handleOverrideSubmit}
                disabled={hasPresetReasons ? !selectedReason : !customReason.trim()}
              >
                {t('critical.submitOverride')}
              </Button>
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        {!showOverrideForm && (
          <Button
            color="inherit"
            onClick={() => setShowOverrideForm(true)}
          >
            {t('critical.overrideCard')}
          </Button>
        )}
        <Button variant="contained" color="error" onClick={handleAccept}>
          {t('critical.acceptCard')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
