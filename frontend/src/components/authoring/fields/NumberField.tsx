import { TextField } from '@mui/material'

interface NumberFieldProps {
  label: string
  value: number | undefined
  onChange: (value: number | undefined) => void
}

export default function NumberField({ label, value, onChange }: NumberFieldProps) {
  return (
    <TextField
      label={label}
      size="small"
      type="number"
      value={value ?? ''}
      onChange={(e) => {
        const v = e.target.value
        onChange(v === '' ? undefined : Number(v))
      }}
      fullWidth
    />
  )
}
