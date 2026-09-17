import { TextField } from '@mui/material'

interface TextAreaFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  helperText?: string
  maxLength?: number
}

export default function TextAreaField({ label, value, onChange, placeholder, helperText, maxLength }: TextAreaFieldProps) {
  return (
    <TextField
      label={label}
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      fullWidth
      multiline
      rows={2}
      placeholder={placeholder}
      helperText={maxLength ? `${value.length} / ${maxLength}` : helperText}
      slotProps={{
        htmlInput: maxLength ? { maxLength } : undefined
      }}
    />
  );
}
