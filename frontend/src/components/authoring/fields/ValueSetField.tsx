import { TextField, Typography, Box } from '@mui/material'

interface ValueSetFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  selectPath?: string
}

export default function ValueSetField({ label, value, onChange, selectPath }: ValueSetFieldProps) {
  // For now, render as a text input.
  // In future phases, this will integrate with VSAC for value set selection.
  return (
    <Box>
      <TextField
        label={label}
        size="small"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        fullWidth
        placeholder="Enter value set OID or search..."
      />
      {selectPath && (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
          Value set lookup will be available when VSAC integration is configured.
        </Typography>
      )}
    </Box>
  )
}
