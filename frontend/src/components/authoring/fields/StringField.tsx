import { TextField } from '@mui/material'

interface StringFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
}

export default function StringField({ label, value, onChange }: StringFieldProps) {
  return (
    <TextField
      label={label}
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      fullWidth
    />
  )
}
