import { useTranslation } from 'react-i18next'
import { Box, Stack, TextField, Typography } from '@mui/material'
import type { MeasurementPeriodFields as MeasurementPeriodValue } from '../../types'
import { measurementPeriodInverted } from '../../utils/measureMetadata'

interface Props {
  value: MeasurementPeriodValue
  onChange: (updates: Partial<MeasurementPeriodValue>) => void
  readOnly?: boolean
  size?: 'small' | 'medium'
}

/**
 * PAT-242 — the measure's own Measurement Period. Shared by the measure details page and the
 * eCQM summary tab: the artifact publishes it onto the measure and bakes it into the generated
 * CQL as the "Measurement Period" parameter default; test case runs and evaluations without an
 * explicit period use it. A cleared date is reported as an explicit `null` (the artifact tab
 * maps it to `''` for its partial-update API, like the standard metadata dates).
 */
export default function MeasurementPeriodFields({ value, onChange, readOnly, size = 'small' }: Props) {
  const { t } = useTranslation('measures')
  const inverted = measurementPeriodInverted(value.measurementPeriodStart, value.measurementPeriodEnd)

  const dateField = (field: 'measurementPeriodStart' | 'measurementPeriodEnd') => (
    <TextField
      label={t(`measurementPeriod.${field}`)}
      type="date"
      size={size}
      fullWidth
      disabled={readOnly}
      value={value[field] || ''}
      error={inverted}
      onChange={(e) => onChange({ [field]: e.target.value || null })}
      slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'data-testid': `measurement-period-${field}` } }}
    />
  )

  return (
    <Box>
      <Stack direction="row" spacing={2}>
        {dateField('measurementPeriodStart')}
        {dateField('measurementPeriodEnd')}
      </Stack>
      {inverted ? (
        <Typography variant="caption" color="error" role="alert" sx={{ display: 'block', mt: 0.5 }}>
          {t('measurementPeriod.inverted')}
        </Typography>
      ) : (
        <Typography variant="caption" sx={{ display: 'block', mt: 0.5, color: 'text.secondary' }}>
          {t('measurementPeriod.hint')}
        </Typography>
      )}
    </Box>
  )
}
