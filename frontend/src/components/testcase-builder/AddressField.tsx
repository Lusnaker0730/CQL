import { Box, TextField, Typography, MenuItem, Chip } from '@mui/material'
import { useState } from 'react'
import type { ElementMetadata } from '../../types'

interface Address {
  use?: string
  type?: string
  line?: string[]
  city?: string
  district?: string
  state?: string
  postalCode?: string
  country?: string
}

interface AddressFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

const USE_OPTIONS = ['home', 'work', 'temp', 'old', 'billing']
const TYPE_OPTIONS = ['postal', 'physical', 'both']

export default function AddressField({ element, value, onChange }: AddressFieldProps) {
  const addr = (value as Address) || {}
  const [lineInput, setLineInput] = useState('')

  const addLine = () => {
    if (lineInput.trim()) {
      onChange({ ...addr, line: [...(addr.line || []), lineInput.trim()] })
      setLineInput('')
    }
  }

  const removeLine = (index: number) => {
    const next = (addr.line || []).filter((_, i) => i !== index)
    onChange({ ...addr, line: next.length > 0 ? next : undefined })
  }

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
        <TextField
          select
          label="use"
          size="small"
          value={addr.use || ''}
          onChange={(e) => onChange({ ...addr, use: e.target.value || undefined })}
          sx={{ width: 100 }}
        >
          <MenuItem value="">—</MenuItem>
          {USE_OPTIONS.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
        <TextField
          select
          label="type"
          size="small"
          value={addr.type || ''}
          onChange={(e) => onChange({ ...addr, type: e.target.value || undefined })}
          sx={{ width: 100 }}
        >
          <MenuItem value="">—</MenuItem>
          {TYPE_OPTIONS.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
      </Box>

      <Box sx={{ mb: 1 }}>
        <Typography variant="caption" color="text.secondary">address lines</Typography>
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 0.5 }}>
          {(addr.line || []).map((l, i) => (
            <Chip key={i} label={l} size="small" onDelete={() => removeLine(i)} />
          ))}
        </Box>
        <TextField
          size="small"
          placeholder="Add line"
          value={lineInput}
          onChange={(e) => setLineInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addLine() } }}
          sx={{ width: 250 }}
        />
      </Box>

      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <TextField label="city" size="small" value={addr.city || ''} onChange={(e) => onChange({ ...addr, city: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label="district" size="small" value={addr.district || ''} onChange={(e) => onChange({ ...addr, district: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label="state" size="small" value={addr.state || ''} onChange={(e) => onChange({ ...addr, state: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label="postalCode" size="small" value={addr.postalCode || ''} onChange={(e) => onChange({ ...addr, postalCode: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label="country" size="small" value={addr.country || ''} onChange={(e) => onChange({ ...addr, country: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
      </Box>
    </Box>
  )
}
