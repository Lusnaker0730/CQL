import { Box, TextField, Typography, MenuItem } from '@mui/material'
import type { ElementMetadata } from '../../types'

interface Identifier {
  use?: string
  system?: string
  value?: string
}

interface IdentifierFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

const USE_OPTIONS = ['usual', 'official', 'temp', 'secondary', 'old']

export default function IdentifierField({ element, value, onChange }: IdentifierFieldProps) {
  const ident = (value as Identifier) || {}

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          select
          label="use"
          size="small"
          value={ident.use || ''}
          onChange={(e) => onChange({ ...ident, use: e.target.value || undefined })}
          sx={{ width: 120 }}
        >
          <MenuItem value="">—</MenuItem>
          {USE_OPTIONS.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
        <TextField
          label="system"
          size="small"
          value={ident.system || ''}
          onChange={(e) => onChange({ ...ident, system: e.target.value || undefined })}
          sx={{ flex: 1 }}
          placeholder="e.g. http://hospital.org/mrn"
        />
        <TextField
          label="value"
          size="small"
          value={ident.value || ''}
          onChange={(e) => onChange({ ...ident, value: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
      </Box>
    </Box>
  )
}
