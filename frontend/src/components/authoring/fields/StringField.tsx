import { TextField } from '@mui/material'

interface StringFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  helperText?: string
  maxLength?: number
}

export default function StringField({ label, value, onChange, placeholder, helperText, maxLength }: StringFieldProps) {
  return (
    <TextField
      label={label}
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      fullWidth
      placeholder={placeholder}
      helperText={helperText}
      inputProps={maxLength ? { maxLength } : undefined}
    />
  )
}
