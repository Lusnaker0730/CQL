import { Box, TextField, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import FieldWrapper from './FieldWrapper'
import { getChildBoundCodes, CONTACT_SYSTEM_CODES, CONTACT_USE_CODES } from './constants'
import { asObject } from '../../utils/fhirGuards'
import type { ElementMetadata } from '../../types'

interface ContactPoint {
  system?: string
  value?: string
  use?: string
}

interface ContactPointFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function ContactPointField({ element, value, onChange }: ContactPointFieldProps) {
  const { t } = useTranslation('measures')
  const cp = asObject(value) as ContactPoint
  const systemOptions = getChildBoundCodes(element, 'system', CONTACT_SYSTEM_CODES)
  const useOptions = getChildBoundCodes(element, 'use', CONTACT_USE_CODES)

  return (
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          select
          label={t('testCaseBuilder.fields.system')}
          size="small"
          value={cp.system || ''}
          onChange={(e) => onChange({ ...cp, system: e.target.value || undefined })}
          sx={{ width: 120 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {systemOptions.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
        <TextField
          label={t('testCaseBuilder.fields.value')}
          size="small"
          value={cp.value || ''}
          onChange={(e) => onChange({ ...cp, value: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
        <TextField
          select
          label={t('testCaseBuilder.fields.use')}
          size="small"
          value={cp.use || ''}
          onChange={(e) => onChange({ ...cp, use: e.target.value || undefined })}
          sx={{ width: 120 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {useOptions.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
      </Box>
    </FieldWrapper>
  )
}
