import { Stack, TextField } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { isDateRangeReversed } from '../../utils/dateDefaults'

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
  const reversed = isDateRangeReversed(dateFrom, dateTo)

  return (
    <Stack direction="row" spacing={2}>
      <TextField
        type="date"
        label={t('batch.dateFrom')}
        value={dateFrom}
        onChange={(e) => onDateFromChange(e.target.value)}
        size="small"
        sx={{ flex: 1 }}
        error={reversed}
        helperText={reversed ? t('batch.dateRangeReversed') : undefined}
        slotProps={{
          inputLabel: { shrink: true }
        }}
      />
      <TextField
        type="date"
        label={t('batch.dateTo')}
        value={dateTo}
        onChange={(e) => onDateToChange(e.target.value)}
        size="small"
        sx={{ flex: 1 }}
        error={reversed}
        slotProps={{
          inputLabel: { shrink: true }
        }}
      />
    </Stack>
  );
}
