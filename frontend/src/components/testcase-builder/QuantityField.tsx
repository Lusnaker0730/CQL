import { Box, TextField, Typography } from '@mui/material'
import type { ElementMetadata } from '../../types'

interface Quantity {
  value?: number
  unit?: string
  system?: string
  code?: string
}

interface QuantityFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function QuantityField({ element, value, onChange }: QuantityFieldProps) {
  const qty = (value as Quantity) || {}

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          label="value"
          size="small"
          type="number"
          value={qty.value ?? ''}
          onChange={(e) => {
            const v = e.target.value
            onChange({ ...qty, value: v === '' ? undefined : parseFloat(v) })
          }}
          inputProps={{ step: 0.01 }}
          sx={{ flex: 1 }}
        />
        <TextField
          label="unit"
          size="small"
          value={qty.unit || ''}
          onChange={(e) => onChange({ ...qty, unit: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
        <TextField
          label="system"
          size="small"
          value={qty.system || ''}
          onChange={(e) => onChange({ ...qty, system: e.target.value || undefined })}
          placeholder="http://unitsofmeasure.org"
          sx={{ flex: 1 }}
        />
        <TextField
          label="code"
          size="small"
          value={qty.code || ''}
          onChange={(e) => onChange({ ...qty, code: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
      </Box>
    </Box>
  )
}
