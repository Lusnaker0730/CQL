import { Box, TextField, Typography, MenuItem, Chip } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ElementMetadata } from '../../types'

interface Address {
  use?: string
  type?: string
  line?: string[]
  city?: string
  district?: string
  state?: string
  postalCode?: string
  country?: string
}

interface AddressFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

const USE_FALLBACK = ['home', 'work', 'temp', 'old', 'billing']
const TYPE_FALLBACK = ['postal', 'physical', 'both']

export default function AddressField({ element, value, onChange }: AddressFieldProps) {
  const { t } = useTranslation('measures')
  const addr = (value as Address) || {}
  const [lineInput, setLineInput] = useState('')
  const useOptions = element.children?.find(c => c.name === 'use')?.boundCodes ?? USE_FALLBACK
  const typeOptions = element.children?.find(c => c.name === 'type')?.boundCodes ?? TYPE_FALLBACK

  const addLine = () => {
    if (lineInput.trim()) {
      onChange({ ...addr, line: [...(addr.line || []), lineInput.trim()] })
      setLineInput('')
    }
  }

  const removeLine = (index: number) => {
    const next = (addr.line || []).filter((_, i) => i !== index)
    onChange({ ...addr, line: next.length > 0 ? next : undefined })
  }

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
        <TextField
          select
          label={t('testCaseBuilder.fields.use')}
          size="small"
          value={addr.use || ''}
          onChange={(e) => onChange({ ...addr, use: e.target.value || undefined })}
          sx={{ width: 100 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {useOptions.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
        <TextField
          select
          label={t('testCaseBuilder.fields.type')}
          size="small"
          value={addr.type || ''}
          onChange={(e) => onChange({ ...addr, type: e.target.value || undefined })}
          sx={{ width: 100 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {typeOptions.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
      </Box>

      <Box sx={{ mb: 1 }}>
        <Typography variant="caption" color="text.secondary">{t('testCaseBuilder.fields.addressLines')}</Typography>
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 0.5 }}>
          {(addr.line || []).map((l, i) => (
            <Chip key={i} label={l} size="small" onDelete={() => removeLine(i)} />
          ))}
        </Box>
        <TextField
          size="small"
          placeholder={t('testCaseBuilder.fields.addLine')}
          value={lineInput}
          onChange={(e) => setLineInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addLine() } }}
          sx={{ width: 250 }}
        />
      </Box>

      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <TextField label={t('testCaseBuilder.fields.city')} size="small" value={addr.city || ''} onChange={(e) => onChange({ ...addr, city: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label={t('testCaseBuilder.fields.district')} size="small" value={addr.district || ''} onChange={(e) => onChange({ ...addr, district: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label={t('testCaseBuilder.fields.state')} size="small" value={addr.state || ''} onChange={(e) => onChange({ ...addr, state: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label={t('testCaseBuilder.fields.postalCode')} size="small" value={addr.postalCode || ''} onChange={(e) => onChange({ ...addr, postalCode: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label={t('testCaseBuilder.fields.country')} size="small" value={addr.country || ''} onChange={(e) => onChange({ ...addr, country: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
      </Box>
    </Box>
  )
}
