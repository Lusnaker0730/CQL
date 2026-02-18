import { TextField, Switch, FormControlLabel, Typography, MenuItem } from '@mui/material'
import type { ElementMetadata } from '../../types'

interface PrimitiveFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function PrimitiveField({ element, value, onChange }: PrimitiveFieldProps) {
  const type = element.type

  if (type === 'boolean') {
    return (
      <FormControlLabel
        control={
          <Switch
            size="small"
            checked={!!value}
            onChange={(e) => onChange(e.target.checked)}
          />
        }
        label={<Typography variant="body2">{element.name}</Typography>}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'integer' || type === 'positiveInt' || type === 'unsignedInt') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        type="number"
        value={value ?? ''}
        onChange={(e) => {
          const v = e.target.value
          onChange(v === '' ? undefined : parseInt(v, 10))
        }}
        required={element.isRequired}
        inputProps={{
          min: type === 'positiveInt' ? 1 : type === 'unsignedInt' ? 0 : undefined,
          step: 1,
        }}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'decimal') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        type="number"
        value={value ?? ''}
        onChange={(e) => {
          const v = e.target.value
          onChange(v === '' ? undefined : parseFloat(v))
        }}
        required={element.isRequired}
        inputProps={{ step: 0.01 }}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'date') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        type="date"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        required={element.isRequired}
        InputLabelProps={{ shrink: true }}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'dateTime' || type === 'instant') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        type="datetime-local"
        value={value ? String(value).slice(0, 16) : ''}
        onChange={(e) => {
          const v = e.target.value
          onChange(v ? v + ':00' : undefined)
        }}
        required={element.isRequired}
        InputLabelProps={{ shrink: true }}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'markdown') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        multiline
        rows={3}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        required={element.isRequired}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  if (type === 'code' && element.boundCodes?.length > 0) {
    return (
      <TextField
        select
        label={element.name}
        size="small"
        fullWidth
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        required={element.isRequired}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      >
        <MenuItem value="">—</MenuItem>
        {element.boundCodes.map((code) => (
          <MenuItem key={code} value={code}>{code}</MenuItem>
        ))}
      </TextField>
    )
  }

  if (type === 'id') {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        required={element.isRequired}
        helperText={element.description || 'Auto-generated if left empty'}
        sx={{ mb: 1 }}
      />
    )
  }

  // Default: string, uri, url, canonical, code (without binding), etc.
  return (
    <TextField
      label={element.name}
      size="small"
      fullWidth
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value || undefined)}
      required={element.isRequired}
      helperText={element.description || undefined}
      sx={{ mb: 1 }}
    />
  )
}
