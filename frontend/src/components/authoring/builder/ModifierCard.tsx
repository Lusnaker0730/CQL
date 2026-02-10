import { Card, CardContent, Stack, Typography, IconButton, Tooltip, TextField } from '@mui/material'
import { Close as RemoveIcon } from '@mui/icons-material'
import type { Modifier } from '../../../types/authoring'

interface ModifierCardProps {
  modifier: Modifier
  onRemove: () => void
  onUpdateValues: (values: Record<string, unknown>) => void
}

export default function ModifierCard({ modifier, onRemove, onUpdateValues }: ModifierCardProps) {
  const hasValues = modifier.values && Object.keys(modifier.values).length > 0

  const handleValueChange = (key: string, value: unknown) => {
    onUpdateValues({ ...(modifier.values || {}), [key]: value })
  }

  return (
    <Card variant="outlined" sx={{ backgroundColor: 'action.hover' }}>
      <CardContent sx={{ py: 1, px: 2, '&:last-child': { pb: 1 } }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>
            {modifier.name}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {modifier.returnType.replace(/_/g, ' ')}
          </Typography>
          <Tooltip title="Remove modifier">
            <IconButton size="small" onClick={onRemove}>
              <RemoveIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>

        {hasValues && (
          <Stack direction="row" spacing={1} mt={1} flexWrap="wrap" useFlexGap>
            {Object.entries(modifier.values!).map(([key, val]) => (
              <TextField
                key={key}
                label={key}
                size="small"
                value={val ?? ''}
                onChange={(e) => handleValueChange(key, e.target.value)}
                sx={{ minWidth: 120 }}
              />
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  )
}
