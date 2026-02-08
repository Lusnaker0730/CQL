import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  TextField,
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
import { usePreferences } from '../../hooks/usePreferences'

interface PreferencesDialogProps {
  open: boolean
  onClose: () => void
}

export default function PreferencesDialog({ open, onClose }: PreferencesDialogProps) {
  const { preferences, updatePreferences, resetPreferences } = usePreferences()

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Preferences</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Editor Settings
          </Typography>

          <Box>
            <Typography variant="body2" gutterBottom>
              Font Size: {preferences.editorFontSize}px
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
            <InputLabel>Tab Size</InputLabel>
            <Select
              value={preferences.editorTabSize}
              onChange={(e) => updatePreferences({ editorTabSize: e.target.value as number })}
              label="Tab Size"
            >
              <MenuItem value={2}>2 spaces</MenuItem>
              <MenuItem value={4}>4 spaces</MenuItem>
              <MenuItem value={8}>8 spaces</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small" fullWidth>
            <InputLabel>Word Wrap</InputLabel>
            <Select
              value={preferences.editorWordWrap}
              onChange={(e) =>
                updatePreferences({ editorWordWrap: e.target.value as 'on' | 'off' | 'wordWrapColumn' })
              }
              label="Word Wrap"
            >
              <MenuItem value="on">On</MenuItem>
              <MenuItem value="off">Off</MenuItem>
              <MenuItem value="wordWrapColumn">Word Wrap Column</MenuItem>
            </Select>
          </FormControl>

          <FormControlLabel
            control={
              <Switch
                checked={preferences.editorMinimap}
                onChange={(e) => updatePreferences({ editorMinimap: e.target.checked })}
              />
            }
            label="Show Minimap"
          />

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            Appearance
          </Typography>

          <FormControl size="small" fullWidth>
            <InputLabel>Theme</InputLabel>
            <Select
              value={preferences.themeMode}
              onChange={(e) => updatePreferences({ themeMode: e.target.value as 'light' | 'dark' })}
              label="Theme"
            >
              <MenuItem value="light">Light</MenuItem>
              <MenuItem value="dark">Dark</MenuItem>
            </Select>
          </FormControl>

          <Divider />

          <Typography variant="subtitle2" color="text.secondary">
            Defaults
          </Typography>

          <TextField
            label="Default FHIR Server URL"
            value={preferences.defaultFhirServerUrl}
            onChange={(e) => updatePreferences({ defaultFhirServerUrl: e.target.value })}
            size="small"
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2, justifyContent: 'space-between' }}>
        <Button onClick={resetPreferences} color="warning">
          Reset to Defaults
        </Button>
        <Button onClick={onClose} variant="contained">
          Done
        </Button>
      </DialogActions>
    </Dialog>
  )
}
