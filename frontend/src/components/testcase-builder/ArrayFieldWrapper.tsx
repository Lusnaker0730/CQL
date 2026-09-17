import { Box, IconButton, Button, Typography, Divider } from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'

interface ArrayFieldWrapperProps {
  label: string
  items: unknown[]
  onAdd: () => void
  onRemove: (index: number) => void
  renderItem: (item: unknown, index: number) => ReactNode
}

export default function ArrayFieldWrapper({
  label,
  items,
  onAdd,
  onRemove,
  renderItem,
}: ArrayFieldWrapperProps) {
  const { t } = useTranslation('measures')

  return (
    <Box sx={{ mb: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography
          variant="caption"
          sx={{
            fontWeight: 600,
            color: "text.secondary"
          }}>
          {label}
        </Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={onAdd} sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
          {t('testCaseBuilder.arrayField.add')}
        </Button>
      </Box>
      {items.map((item, i) => (
        <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5, mb: 0.5 }}>
          <Box sx={{ flex: 1 }}>{renderItem(item, i)}</Box>
          <IconButton size="small" onClick={() => onRemove(i)} sx={{ mt: 0.5 }} aria-label={t('testCaseBuilder.arrayField.removeItem')}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Box>
      ))}
      {items.length > 0 && <Divider sx={{ mt: 0.5 }} />}
    </Box>
  );
}
