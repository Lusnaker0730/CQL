import { Box, TextField, Typography, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
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

const SYSTEM_FALLBACK = ['phone', 'fax', 'email', 'pager', 'url', 'sms', 'other']
const USE_FALLBACK = ['home', 'work', 'temp', 'old', 'mobile']

export default function ContactPointField({ element, value, onChange }: ContactPointFieldProps) {
  const { t } = useTranslation('measures')
  const cp = (value as ContactPoint) || {}
  const systemOptions = element.children?.find(c => c.name === 'system')?.boundCodes ?? SYSTEM_FALLBACK
  const useOptions = element.children?.find(c => c.name === 'use')?.boundCodes ?? USE_FALLBACK

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
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
    </Box>
  )
}
