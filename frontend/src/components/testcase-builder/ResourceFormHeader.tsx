import { Box, TextField, Chip, Typography } from '@mui/material'

interface ResourceFormHeaderProps {
  resourceType: string
  resourceId: string
  onIdChange: (id: string) => void
}

export default function ResourceFormHeader({ resourceType, resourceId, onIdChange }: ResourceFormHeaderProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
      <Chip
        label={resourceType}
        color="primary"
        size="small"
        sx={{ fontWeight: 600 }}
      />
      <Typography variant="caption" color="text.secondary">ID:</Typography>
      <TextField
        size="small"
        value={resourceId}
        onChange={(e) => onIdChange(e.target.value)}
        placeholder="resource-id"
        sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.85rem' } }}
        variant="standard"
      />
    </Box>
  )
}
