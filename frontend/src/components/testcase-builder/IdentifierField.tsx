import { Box, TextField, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import FieldWrapper from './FieldWrapper'
import { getChildBoundCodes, IDENTIFIER_USE_CODES } from './constants'
import { asObject } from '../../utils/fhirGuards'
import {
  TW_IDENTIFIER_PRESETS,
  TW_IDENTIFIER_TYPE_SYSTEM,
} from '../../config/twcore/identifierPresets'
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

export default function IdentifierField({ element, value, onChange }: IdentifierFieldProps) {
  const { t } = useTranslation('measures')
  const ident = asObject(value) as Identifier
  const useOptions = getChildBoundCodes(element, 'use', IDENTIFIER_USE_CODES)

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
            system: TW_IDENTIFIER_TYPE_SYSTEM,
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
  const activePreset = currentPresetIndex >= 0 ? TW_IDENTIFIER_PRESETS[currentPresetIndex] : null
  const activeHint = activePreset?.hintKey

  return (
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
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
    </FieldWrapper>
  )
}
