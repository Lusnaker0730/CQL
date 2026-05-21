import { useEffect, useRef, useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Switch,
  Slider,
  Typography,
  Divider,
  Box,
  TextField,
  InputAdornment,
  IconButton,
  Chip,
  Alert,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import Visibility from '@mui/icons-material/Visibility'
import VisibilityOff from '@mui/icons-material/VisibilityOff'
import CheckCircle from '@mui/icons-material/CheckCircle'
import Cancel from '@mui/icons-material/Cancel'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import { usePreferences } from '../../hooks/usePreferences'
import { settingsApi, type VsacStatus, type AiStatus } from '../../api/settingsApi'
import type { RootState } from '../../store'
import FhirServerUrlField from './FhirServerUrlField'

interface PreferencesDialogProps {
  open: boolean
  onClose: () => void
}

export default function PreferencesDialog({ open, onClose }: PreferencesDialogProps) {
  const { preferences, updatePreferences, resetPreferences } = usePreferences()
  const { t } = useTranslation()

  const [vsacStatus, setVsacStatus] = useState<VsacStatus | null>(null)
  const [vsacApiKey, setVsacApiKey] = useState('')
  const [showApiKey, setShowApiKey] = useState(false)
  const [vsacSaving, setVsacSaving] = useState(false)
  const [vsacMessage, setVsacMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null)
  const [aiApiKey, setAiApiKey] = useState('')
  const [showAiApiKey, setShowAiApiKey] = useState(false)
  const [aiSaving, setAiSaving] = useState(false)
  const [aiMessage, setAiMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  // Guards post-await setState calls when the user closes the dialog mid-flight
  // (PAT-133 P1). Combined with the isAdmin gate below so non-admins don't even
  // attempt the ADMIN-only fetches.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // /api/settings/{vsac-status,ai-status} are ADMIN-only (SettingsController
  // PAT-145). Skip the calls entirely for non-admins so opening Preferences
  // doesn't surface a 403 in the network panel for the operator-only fields
  // (BUG-117 follow-up).
  const isAdmin = useSelector((state: RootState) => state.auth.user?.role === 'ADMIN')

  useEffect(() => {
    if (open && isAdmin) {
      settingsApi.getVsacStatus()
        .then((s) => isMountedRef.current && setVsacStatus(s))
        .catch(() => isMountedRef.current && setVsacStatus(null))
      settingsApi.getAiStatus()
        .then((s) => isMountedRef.current && setAiStatus(s))
        .catch(() => isMountedRef.current && setAiStatus(null))
    } else {
      // Clear typed-but-not-saved API keys when the dialog closes so they
      // don't survive into the next session — important on shared workstations
      // and to make the show/hide toggle a one-shot rather than persistent.
      setVsacApiKey('')
      setAiApiKey('')
      setVsacMessage(null)
      setAiMessage(null)
      setShowApiKey(false)
      setShowAiApiKey(false)
    }
  }, [open, isAdmin])

  const handleSaveVsacKey = async () => {
    setVsacSaving(true)
    setVsacMessage(null)
    try {
      const result = await settingsApi.updateVsacApiKey(vsacApiKey)
      if (!isMountedRef.current) return
      setVsacStatus((prev) => prev ? { ...prev, configured: result.configured } : null)
      setVsacApiKey('')
      setVsacMessage({ type: 'success', text: t('preferences.vsacKeySaved') })
    } catch {
      if (!isMountedRef.current) return
      setVsacMessage({ type: 'error', text: t('preferences.vsacKeySaveFailed') })
    } finally {
      if (isMountedRef.current) setVsacSaving(false)
    }
  }

  const handleSaveAiKey = async () => {
    setAiSaving(true)
    setAiMessage(null)
    try {
      const result = await settingsApi.updateAiApiKey(aiApiKey)
      if (!isMountedRef.current) return
      setAiStatus((prev) => prev ? { ...prev, configured: result.configured } : null)
      setAiApiKey('')
      setAiMessage({ type: 'success', text: t('preferences.aiKeySaved') })
    } catch {
      if (!isMountedRef.current) return
      setAiMessage({ type: 'error', text: t('preferences.aiKeySaveFailed') })
    } finally {
      if (isMountedRef.current) setAiSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('preferences.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <Typography variant="subtitle2" color="text.secondary">
            {t('preferences.editorSettings')}
          </Typography>

          <Box>
            <Typography variant="body2" gutterBottom>
              {t('preferences.fontSize', { size: preferences.editorFontSize })}
            </Typography>
            <Slider
              value={preferences.editorFontSize}
              onChange={(_, value) => updatePreferences({ editorFontSize: value as number })}
              min={10}
              max={24}
              step={1}
              marks={[
                { value: 10, label: '10' },
                { value: 14, label: '14' },
                { value: 18, label: '18' },
                { value: 24, label: '24' },
              ]}
            />
          </Box>

          <FormControl size="small" fullWidth>
            <InputLabel>{t('preferences.tabSize')}</InputLabel>
            <Select
              value={preferences.editorTabSize}
              onChange={(e) => updatePreferences({ editorTabSize: Number(e.target.value) })}
              label={t('preferences.tabSize')}
            >
              <MenuItem value={2}>{t('preferences.tabSizeSpaces', { count: 2 })}</MenuItem>
              <MenuItem value={4}>{t('preferences.tabSizeSpaces', { count: 4 })}</MenuItem>
              <MenuItem value={8}>{t('preferences.tabSizeSpaces', { count: 8 })}</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small" fullWidth>
            <InputLabel>{t('preferences.wordWrap')}</InputLabel>
            <Select
              value={preferences.editorWordWrap}
              onChange={(e) =>
                updatePreferences({ editorWordWrap: e.target.value as 'on' | 'off' | 'wordWrapColumn' })
              }
              label={t('preferences.wordWrap')}
            >
              <MenuItem value="on">{t('preferences.wordWrapOn')}</MenuItem>
              <MenuItem value="off">{t('preferences.wordWrapOff')}</MenuItem>
              <MenuItem value="wordWrapColumn">{t('preferences.wordWrapColumn')}</MenuItem>
            </Select>
          </FormControl>

          <FormControlLabel
            control={
              <Switch
                checked={preferences.editorMinimap}
                onChange={(e) => updatePreferences({ editorMinimap: e.target.checked })}
              />
            }
            label={t('preferences.showMinimap')}
          />

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            {t('preferences.appearance')}
          </Typography>

          <FormControl size="small" fullWidth>
            <InputLabel>{t('preferences.theme')}</InputLabel>
            <Select
              value={preferences.themeMode}
              onChange={(e) => updatePreferences({ themeMode: e.target.value as 'light' | 'dark' })}
              label={t('preferences.theme')}
            >
              <MenuItem value="light">{t('preferences.themeLight')}</MenuItem>
              <MenuItem value="dark">{t('preferences.themeDark')}</MenuItem>
            </Select>
          </FormControl>

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            {t('preferences.defaults')}
          </Typography>

          <FhirServerUrlField
            label={t('preferences.defaultFhirServerUrl')}
            value={preferences.defaultFhirServerUrl}
            onChange={(value) => updatePreferences({ defaultFhirServerUrl: value })}
            selfValidate
          />

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            {t('preferences.terminology')}
          </Typography>

          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <Typography variant="body2">{t('preferences.vsacStatus')}</Typography>
              {vsacStatus ? (
                <Chip
                  size="small"
                  icon={vsacStatus.configured ? <CheckCircle /> : <Cancel />}
                  label={vsacStatus.configured ? t('preferences.vsacConfigured') : t('preferences.vsacNotConfigured')}
                  color={vsacStatus.configured ? 'success' : 'warning'}
                  variant="outlined"
                />
              ) : (
                <Chip size="small" label={t('preferences.vsacUnavailable')} color="default" variant="outlined" />
              )}
            </Box>

            {vsacStatus && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                {t('preferences.vsacUrl')}: {vsacStatus.url}
              </Typography>
            )}

            <TextField
              label={t('preferences.vsacApiKey')}
              size="small"
              fullWidth
              type={showApiKey ? 'text' : 'password'}
              value={vsacApiKey}
              onChange={(e) => setVsacApiKey(e.target.value)}
              placeholder={t('preferences.vsacApiKeyPlaceholder')}
              helperText={t('preferences.vsacApiKeyHelp')}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowApiKey(!showApiKey)} edge="end">
                      {showApiKey ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            <Button
              size="small"
              variant="outlined"
              onClick={handleSaveVsacKey}
              disabled={!vsacApiKey.trim() || vsacSaving}
              sx={{ mt: 1, textTransform: 'none' }}
            >
              {vsacSaving ? t('preferences.vsacKeySaving') : t('preferences.vsacKeySave')}
            </Button>

            {vsacMessage && (
              <Alert severity={vsacMessage.type} sx={{ mt: 1 }} onClose={() => setVsacMessage(null)}>
                {vsacMessage.text}
              </Alert>
            )}
          </Box>

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            {t('preferences.aiFixSuggestion')}
          </Typography>

          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <Typography variant="body2">{t('preferences.aiStatus')}</Typography>
              {aiStatus ? (
                aiStatus.enabled ? (
                  <Chip
                    size="small"
                    icon={<CheckCircle />}
                    label={
                      aiStatus.provider === 'ollama'
                        ? t('preferences.aiProviderOllama', { model: aiStatus.model })
                        : t('preferences.aiProviderCloud', { model: aiStatus.model })
                    }
                    color="success"
                    variant="outlined"
                  />
                ) : (
                  <Chip
                    size="small"
                    icon={<Cancel />}
                    label={t('preferences.aiDisabled')}
                    color="default"
                    variant="outlined"
                  />
                )
              ) : (
                <Chip size="small" label={t('preferences.aiUnavailable')} color="default" variant="outlined" />
              )}
            </Box>

            {aiStatus?.provider === 'cloud' && (
              <>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Typography variant="body2">{t('preferences.aiApiKeyStatus')}</Typography>
                  <Chip
                    size="small"
                    icon={aiStatus.configured ? <CheckCircle /> : <Cancel />}
                    label={aiStatus.configured ? t('preferences.aiKeyConfigured') : t('preferences.aiKeyNotConfigured')}
                    color={aiStatus.configured ? 'success' : 'warning'}
                    variant="outlined"
                  />
                </Box>

                <TextField
                  label={t('preferences.aiApiKey')}
                  size="small"
                  fullWidth
                  type={showAiApiKey ? 'text' : 'password'}
                  value={aiApiKey}
                  onChange={(e) => setAiApiKey(e.target.value)}
                  placeholder={t('preferences.aiApiKeyPlaceholder')}
                  helperText={t('preferences.aiApiKeyHelp')}
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton size="small" onClick={() => setShowAiApiKey(!showAiApiKey)} edge="end">
                          {showAiApiKey ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />

                <Button
                  size="small"
                  variant="outlined"
                  onClick={handleSaveAiKey}
                  disabled={!aiApiKey.trim() || aiSaving}
                  sx={{ mt: 1, textTransform: 'none' }}
                >
                  {aiSaving ? t('preferences.aiKeySaving') : t('preferences.aiKeySave')}
                </Button>

                {aiMessage && (
                  <Alert severity={aiMessage.type} sx={{ mt: 1 }} onClose={() => setAiMessage(null)}>
                    {aiMessage.text}
                  </Alert>
                )}
              </>
            )}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2, justifyContent: 'space-between' }}>
        <Button onClick={resetPreferences} color="warning">
          {t('actions.resetToDefaults')}
        </Button>
        <Button onClick={onClose} variant="contained">
          {t('actions.done')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
