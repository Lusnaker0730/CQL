import { Box, TextField } from '@mui/material'
import { useTranslation } from 'react-i18next'
import FieldWrapper from './FieldWrapper'
import type { ElementMetadata } from '../../types'

interface Period {
  start?: string
  end?: string
}

interface PeriodFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function PeriodField({ element, value, onChange }: PeriodFieldProps) {
  const { t } = useTranslation('measures')
  const period = (value as Period) || {}

  return (
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          label={t('testCaseBuilder.fields.start')}
          size="small"
          type="datetime-local"
          value={period.start ? String(period.start).slice(0, 16) : ''}
          onChange={(e) => {
            const v = e.target.value
            onChange({ ...period, start: v ? v + ':00' : undefined })
          }}
          InputLabelProps={{ shrink: true }}
          sx={{ flex: 1 }}
        />
        <TextField
          label={t('testCaseBuilder.fields.end')}
          size="small"
          type="datetime-local"
          value={period.end ? String(period.end).slice(0, 16) : ''}
          onChange={(e) => {
            const v = e.target.value
            onChange({ ...period, end: v ? v + ':00' : undefined })
          }}
          InputLabelProps={{ shrink: true }}
          sx={{ flex: 1 }}
        />
      </Box>
    </FieldWrapper>
  )
}
