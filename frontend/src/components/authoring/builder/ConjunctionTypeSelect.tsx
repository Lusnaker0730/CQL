import { Select, MenuItem, FormControl } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface ConjunctionTypeSelectProps {
  value: string
  onChange: (value: string) => void
}

export default function ConjunctionTypeSelect({ value, onChange }: ConjunctionTypeSelectProps) {
  const { t } = useTranslation('authoring')

  return (
    <FormControl size="small">
      <Select
        value={value}
        onChange={(e) => onChange(e.target.value as string)}
        sx={{
          minWidth: 80,
          '& .MuiSelect-select': {
            py: 0.5,
            fontWeight: 600,
            fontSize: '0.85rem',
          },
        }}
      >
        <MenuItem value="And">{t('conjunctionType.and')}</MenuItem>
        <MenuItem value="Or">{t('conjunctionType.or')}</MenuItem>
        <MenuItem value="Union">{t('conjunctionType.union')}</MenuItem>
        <MenuItem value="Intersect">{t('conjunctionType.intersect')}</MenuItem>
      </Select>
    </FormControl>
  )
}
