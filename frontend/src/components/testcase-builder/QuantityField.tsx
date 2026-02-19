import { useState } from 'react'
import { Box, TextField, Typography, Collapse, Link } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ElementMetadata } from '../../types'
import { FHIR_UCUM_SYSTEM } from './constants'
import UcumUnitField, { UCUM_UNITS } from '../common/UcumUnitField'

interface Quantity {
  value?: number
  unit?: string
  system?: string
  code?: string
}

interface QuantityFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function QuantityField({ element, value, onChange }: QuantityFieldProps) {
  const { t } = useTranslation('measures')
  const qty = (value as Quantity) || {}
  const [showAdvanced, setShowAdvanced] = useState(false)

  const handleUnitChange = (unit: string) => {
    const knownUnit = UCUM_UNITS.find((u) => u.value === unit)
    if (knownUnit) {
      onChange({ ...qty, unit: knownUnit.label, system: FHIR_UCUM_SYSTEM, code: knownUnit.value })
    } else {
      onChange({ ...qty, unit: unit || undefined, code: unit || undefined })
    }
  }

  // Resolve display value: prefer code (UCUM), fall back to unit
  const unitDisplayValue = qty.code || qty.unit || ''

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
        <TextField
          label={t('testCaseBuilder.fields.value')}
          size="small"
          type="number"
          value={qty.value ?? ''}
          onChange={(e) => {
            const v = e.target.value
            onChange({ ...qty, value: v === '' ? undefined : parseFloat(v) })
          }}
          inputProps={{ step: 0.01 }}
          sx={{ flex: 1 }}
        />
        <UcumUnitField
          label={t('testCaseBuilder.fields.unit')}
          value={unitDisplayValue}
          onChange={handleUnitChange}
          sx={{ flex: 2 }}
        />
      </Box>
      <Link
        component="button"
        variant="caption"
        onClick={() => setShowAdvanced(!showAdvanced)}
        sx={{ mt: 0.5, display: 'inline-block' }}
      >
        {showAdvanced ? t('testCaseBuilder.fields.hideSystemCode') : t('testCaseBuilder.fields.showSystemCode')}
      </Link>
      <Collapse in={showAdvanced}>
        <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
          <TextField
            label={t('testCaseBuilder.fields.system')}
            size="small"
            value={qty.system || ''}
            onChange={(e) => onChange({ ...qty, system: e.target.value || undefined })}
            placeholder={FHIR_UCUM_SYSTEM}
            sx={{ flex: 1 }}
          />
          <TextField
            label={t('testCaseBuilder.fields.code')}
            size="small"
            value={qty.code || ''}
            onChange={(e) => onChange({ ...qty, code: e.target.value || undefined })}
            sx={{ flex: 1 }}
          />
        </Box>
      </Collapse>
    </Box>
  )
}
