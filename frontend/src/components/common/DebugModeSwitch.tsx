import { FormControlLabel, Switch, Stack, Typography } from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import DebugIcon from '@mui/icons-material/BugReport'

interface DebugModeSwitchProps {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  disabled?: boolean
}

export default function DebugModeSwitch({ checked, onChange, label, disabled }: DebugModeSwitchProps) {
  return (
    <FormControlLabel
      control={
        <Switch
          size="small"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          disabled={disabled}
        />
      }
      label={
        <Stack direction="row" spacing={0.5} sx={{
          alignItems: "center"
        }}>
          <DebugIcon sx={{ fontSize: 16, color: checked ? 'secondary.main' : 'text.secondary' }} />
          <Typography variant="body2" color={checked ? 'secondary.main' : 'text.secondary'}>
            {label}
          </Typography>
        </Stack>
      }
    />
  );
}
