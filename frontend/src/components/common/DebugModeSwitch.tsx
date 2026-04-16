import { FormControlLabel, Switch, Stack, Typography } from '@mui/material'
import { BugReport as DebugIcon } from '@mui/icons-material'

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
        <Stack direction="row" spacing={0.5} alignItems="center">
          <DebugIcon sx={{ fontSize: 16, color: checked ? 'secondary.main' : 'text.secondary' }} />
          <Typography variant="body2" color={checked ? 'secondary.main' : 'text.secondary'}>
            {label}
          </Typography>
        </Stack>
      }
    />
  )
}
