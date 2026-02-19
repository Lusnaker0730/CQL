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
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { usePreferences } from '../../hooks/usePreferences'
import FhirServerUrlField from './FhirServerUrlField'

interface PreferencesDialogProps {
  open: boolean
  onClose: () => void
}

export default function PreferencesDialog({ open, onClose }: PreferencesDialogProps) {
  const { preferences, updatePreferences, resetPreferences } = usePreferences()
  const { t } = useTranslation()

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
              onChange={(e) => updatePreferences({ editorTabSize: e.target.value as number })}
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
