import { Box, TextField, Chip, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface ResourceFormHeaderProps {
  resourceType: string
  resourceId: string
  onIdChange: (id: string) => void
}

export default function ResourceFormHeader({ resourceType, resourceId, onIdChange }: ResourceFormHeaderProps) {
  const { t } = useTranslation('measures')

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
      <Chip
        label={resourceType}
        color="primary"
        size="small"
        sx={{ fontWeight: 600 }}
      />
      <Typography variant="caption" sx={{
        color: "text.secondary"
      }}>{t('testCaseBuilder.idLabel')}</Typography>
      <TextField
        size="small"
        value={resourceId}
        onChange={(e) => onIdChange(e.target.value)}
        placeholder={t('testCaseBuilder.idPlaceholder')}
        sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.85rem' } }}
        variant="standard"
      />
    </Box>
  );
}
