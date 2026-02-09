import { Box, TextField, Typography, MenuItem } from '@mui/material'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import type { ElementMetadata } from '../../types'

interface Reference {
  reference?: string
  display?: string
}

interface ReferenceFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function ReferenceField({ element, value, onChange }: ReferenceFieldProps) {
  const { state } = useBundleBuilder()
  const ref = (value as Reference) || {}

  // Build reference options from bundle entries, filtered by allowed target types
  const targets = element.referenceTargets || []
  const options = state.entries
    .filter((e) => targets.length === 0 || targets.includes(e.resourceType))
    .map((e) => ({
      value: `${e.resourceType}/${(e.resourceData.id as string) || e.id}`,
      label: `${e.resourceType}/${(e.resourceData.id as string) || e.id}`,
    }))

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
        {targets.length > 0 && (
          <Typography component="span" variant="caption" color="text.disabled" sx={{ ml: 1 }}>
            ({targets.join(', ')})
          </Typography>
        )}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          select
          label="reference"
          size="small"
          value={ref.reference || ''}
          onChange={(e) => onChange({ ...ref, reference: e.target.value || undefined })}
          sx={{ flex: 1 }}
        >
          <MenuItem value="">— Select —</MenuItem>
          {options.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="display"
          size="small"
          value={ref.display || ''}
          onChange={(e) => onChange({ ...ref, display: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
      </Box>
    </Box>
  )
}
