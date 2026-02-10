import { TextField } from '@mui/material'

interface TextAreaFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
}

export default function TextAreaField({ label, value, onChange }: TextAreaFieldProps) {
  return (
    <TextField
      label={label}
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      fullWidth
      multiline
      rows={2}
    />
  )
}
