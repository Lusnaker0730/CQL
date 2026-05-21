import { Box, TextField, MenuItem, Chip, Collapse, IconButton, Typography } from '@mui/material'
import { useState } from 'react'
import { ExpandMore as ExpandMoreIcon, ExpandLess as ExpandLessIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import FieldWrapper from './FieldWrapper'
import { getChildBoundCodes, ADDRESS_USE_CODES, ADDRESS_TYPE_CODES } from './constants'
import { TW_ADDRESS_EXT_BASE } from '../../constants/fhirExtensions'
import { asObject } from '../../utils/fhirGuards'
import type { ElementMetadata } from '../../types'

interface FhirExtension {
  url: string
  valueString?: string
}

interface Address {
  use?: string
  type?: string
  line?: string[]
  city?: string
  district?: string
  state?: string
  postalCode?: string
  country?: string
  extension?: FhirExtension[]
}

interface AddressFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

interface TwAddressField {
  key: string
  extSuffix: string
  labelKey: string
}

const TW_ADDRESS_FIELDS: TwAddressField[] = [
  { key: 'section', extSuffix: 'section', labelKey: 'testCaseBuilder.twAddress.section' },
  { key: 'village', extSuffix: 'village', labelKey: 'testCaseBuilder.twAddress.village' },
  { key: 'neighborhood', extSuffix: 'neighborhood', labelKey: 'testCaseBuilder.twAddress.neighborhood' },
  { key: 'lane', extSuffix: 'lane', labelKey: 'testCaseBuilder.twAddress.lane' },
  { key: 'alley', extSuffix: 'alley', labelKey: 'testCaseBuilder.twAddress.alley' },
  { key: 'number', extSuffix: 'number', labelKey: 'testCaseBuilder.twAddress.number' },
  { key: 'floor', extSuffix: 'floor', labelKey: 'testCaseBuilder.twAddress.floor' },
  { key: 'room', extSuffix: 'room', labelKey: 'testCaseBuilder.twAddress.room' },
]

function getExtValue(extensions: FhirExtension[] | undefined, suffix: string): string {
  return extensions?.find((e) => e.url === TW_ADDRESS_EXT_BASE + suffix)?.valueString || ''
}

function setExtValue(extensions: FhirExtension[] | undefined, suffix: string, val: string): FhirExtension[] | undefined {
  const url = TW_ADDRESS_EXT_BASE + suffix
  const existing = (extensions || []).filter((e) => e.url !== url)
  if (val) {
    existing.push({ url, valueString: val })
  }
  return existing.length > 0 ? existing : undefined
}

export default function AddressField({ element, value, onChange }: AddressFieldProps) {
  const { t } = useTranslation('measures')
  const addr = asObject(value) as Address
  const [lineInput, setLineInput] = useState('')
  const [twExpanded, setTwExpanded] = useState(false)
  const useOptions = getChildBoundCodes(element, 'use', ADDRESS_USE_CODES)
  const typeOptions = getChildBoundCodes(element, 'type', ADDRESS_TYPE_CODES)

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

  const handleTwFieldChange = (suffix: string, val: string) => {
    onChange({ ...addr, extension: setExtValue(addr.extension, suffix, val) })
  }

  return (
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
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

      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 1 }}>
        <TextField label={t('testCaseBuilder.fields.city')} size="small" value={addr.city || ''} onChange={(e) => onChange({ ...addr, city: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label={t('testCaseBuilder.fields.district')} size="small" value={addr.district || ''} onChange={(e) => onChange({ ...addr, district: e.target.value || undefined })} sx={{ flex: 1, minWidth: 120 }} />
        <TextField label={t('testCaseBuilder.fields.state')} size="small" value={addr.state || ''} onChange={(e) => onChange({ ...addr, state: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label={t('testCaseBuilder.fields.postalCode')} size="small" value={addr.postalCode || ''} onChange={(e) => onChange({ ...addr, postalCode: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
        <TextField label={t('testCaseBuilder.fields.country')} size="small" value={addr.country || ''} onChange={(e) => onChange({ ...addr, country: e.target.value || undefined })} sx={{ flex: 1, minWidth: 80 }} />
      </Box>

      <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, px: 1, py: 0.5 }}>
        <Box
          sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
          onClick={() => setTwExpanded(!twExpanded)}
        >
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
            {t('testCaseBuilder.twAddress.title')}
          </Typography>
          <IconButton size="small">
            {twExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
          </IconButton>
        </Box>
        <Collapse in={twExpanded}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1, mb: 0.5 }}>
            {TW_ADDRESS_FIELDS.map((field) => (
              <TextField
                key={field.key}
                label={t(field.labelKey)}
                size="small"
                value={getExtValue(addr.extension, field.extSuffix)}
                onChange={(e) => handleTwFieldChange(field.extSuffix, e.target.value)}
                sx={{ flex: 1, minWidth: 80 }}
              />
            ))}
          </Box>
        </Collapse>
      </Box>
    </FieldWrapper>
  )
}
