import { Box, TextField, Typography, Chip, MenuItem } from '@mui/material'
import { useState } from 'react'
import type { ElementMetadata } from '../../types'

interface HumanName {
  use?: string
  family?: string
  given?: string[]
  prefix?: string[]
  suffix?: string[]
}

interface HumanNameFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

const USE_FALLBACK = ['usual', 'official', 'temp', 'nickname', 'anonymous', 'old', 'maiden']

export default function HumanNameField({ element, value, onChange }: HumanNameFieldProps) {
  const name = (value as HumanName) || {}
  const [givenInput, setGivenInput] = useState('')
  const useOptions = element.children?.find(c => c.name === 'use')?.boundCodes ?? USE_FALLBACK

  const addGiven = () => {
    if (givenInput.trim()) {
      onChange({ ...name, given: [...(name.given || []), givenInput.trim()] })
      setGivenInput('')
    }
  }

  const removeGiven = (index: number) => {
    const next = (name.given || []).filter((_, i) => i !== index)
    onChange({ ...name, given: next.length > 0 ? next : undefined })
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
          value={name.use || ''}
          onChange={(e) => onChange({ ...name, use: e.target.value || undefined })}
          sx={{ width: 120 }}
        >
          <MenuItem value="">—</MenuItem>
          {useOptions.map((opt) => (
            <MenuItem key={opt} value={opt}>{opt}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="family"
          size="small"
          value={name.family || ''}
          onChange={(e) => onChange({ ...name, family: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
      </Box>
      <Box sx={{ mb: 1 }}>
        <Typography variant="caption" color="text.secondary">given names</Typography>
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 0.5 }}>
          {(name.given || []).map((g, i) => (
            <Chip key={i} label={g} size="small" onDelete={() => removeGiven(i)} />
          ))}
        </Box>
        <TextField
          size="small"
          placeholder="Add given name"
          value={givenInput}
          onChange={(e) => setGivenInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') { e.preventDefault(); addGiven() }
          }}
          sx={{ width: 200 }}
        />
      </Box>
    </Box>
  )
}
