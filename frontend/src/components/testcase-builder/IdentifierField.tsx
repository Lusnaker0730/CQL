import { Box, TextField, Typography, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ElementMetadata } from '../../types'

interface IdentifierType {
  coding?: Array<{
    system?: string
    code?: string
    display?: string
  }>
  text?: string
}

interface Identifier {
  use?: string
  system?: string
  value?: string
  type?: IdentifierType
}

interface IdentifierFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

const USE_FALLBACK = ['usual', 'official', 'temp', 'secondary', 'old']

interface TwIdentifierPreset {
  labelKey: string
  use: string
  system: string
  typeCode: string
  typeDisplay: string
  hintKey?: string
}

const TW_IDENTIFIER_PRESETS: TwIdentifierPreset[] = [
  {
    labelKey: 'testCaseBuilder.identifierPresets.nationalId',
    use: 'official',
    system: 'http://www.moi.gov.tw/',
    typeCode: 'NNxxx',
    typeDisplay: 'National Person Identifier',
    hintKey: 'testCaseBuilder.identifierPresets.nationalIdHint',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.passport',
    use: 'official',
    system: 'http://www.boca.gov.tw/',
    typeCode: 'PPN',
    typeDisplay: 'Passport Number',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.residentCert',
    use: 'official',
    system: 'http://www.immigration.gov.tw/',
    typeCode: 'PRC',
    typeDisplay: 'Permanent Resident Card Number',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.medicalRecord',
    use: 'usual',
    system: '',
    typeCode: 'MR',
    typeDisplay: 'Medical Record Number',
  },
]

export default function IdentifierField({ element, value, onChange }: IdentifierFieldProps) {
  const { t } = useTranslation('measures')
  const ident = (value as Identifier) || {}
  const useOptions = element.children?.find(c => c.name === 'use')?.boundCodes ?? USE_FALLBACK

  const handlePresetChange = (presetIndex: string) => {
    if (!presetIndex) return
    const preset = TW_IDENTIFIER_PRESETS[Number(presetIndex)]
    if (!preset) return
    onChange({
      ...ident,
      use: preset.use,
      system: preset.system || ident.system,
      type: {
        coding: [
          {
            system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
            code: preset.typeCode,
            display: preset.typeDisplay,
          },
        ],
      },
    })
  }

  const currentPresetIndex = TW_IDENTIFIER_PRESETS.findIndex(
    (p) => ident.type?.coding?.[0]?.code === p.typeCode
  )

  const activeHint = currentPresetIndex >= 0
    ? TW_IDENTIFIER_PRESETS[currentPresetIndex].hintKey
    : undefined

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
        <TextField
          select
          label={t('testCaseBuilder.identifierPresets.label')}
          size="small"
          value={currentPresetIndex >= 0 ? String(currentPresetIndex) : ''}
          onChange={(e) => handlePresetChange(e.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {TW_IDENTIFIER_PRESETS.map((preset, idx) => (
            <MenuItem key={preset.typeCode} value={String(idx)}>
              {t(preset.labelKey)}
            </MenuItem>
          ))}
        </TextField>
      </Box>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          select
          label={t('testCaseBuilder.fields.use')}
          size="small"
          value={ident.use || ''}
          onChange={(e) => onChange({ ...ident, use: e.target.value || undefined })}
          sx={{ width: 120 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.emptyOption')}</MenuItem>
          {useOptions.map((opt) => <MenuItem key={opt} value={opt}>{opt}</MenuItem>)}
        </TextField>
        <TextField
          label={t('testCaseBuilder.fields.system')}
          size="small"
          value={ident.system || ''}
          onChange={(e) => onChange({ ...ident, system: e.target.value || undefined })}
          sx={{ flex: 1 }}
          placeholder={t('testCaseBuilder.fields.identifierSystemPlaceholder')}
        />
        <TextField
          label={t('testCaseBuilder.fields.value')}
          size="small"
          value={ident.value || ''}
          onChange={(e) => onChange({ ...ident, value: e.target.value || undefined })}
          sx={{ flex: 1 }}
          helperText={activeHint ? t(activeHint) : undefined}
        />
      </Box>
    </Box>
  )
}
