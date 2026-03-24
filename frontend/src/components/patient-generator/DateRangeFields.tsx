import { Stack, TextField } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface DateRangeFieldsProps {
  dateFrom: string
  dateTo: string
  onDateFromChange: (value: string) => void
  onDateToChange: (value: string) => void
}

export default function DateRangeFields({
  dateFrom,
  dateTo,
  onDateFromChange,
  onDateToChange,
}: DateRangeFieldsProps) {
  const { t } = useTranslation('patientGenerator')

  return (
    <Stack direction="row" spacing={2}>
      <TextField
        type="date"
        label={t('batch.dateFrom')}
        value={dateFrom}
        onChange={(e) => onDateFromChange(e.target.value)}
        size="small"
        InputLabelProps={{ shrink: true }}
        sx={{ flex: 1 }}
      />
      <TextField
        type="date"
        label={t('batch.dateTo')}
        value={dateTo}
        onChange={(e) => onDateToChange(e.target.value)}
        size="small"
        InputLabelProps={{ shrink: true }}
        sx={{ flex: 1 }}
      />
    </Stack>
  )
}
