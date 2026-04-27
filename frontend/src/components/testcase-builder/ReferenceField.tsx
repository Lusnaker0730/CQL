import { useMemo } from 'react'
import { Box, TextField, Typography, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import FieldWrapper from './FieldWrapper'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import { asObject } from '../../utils/fhirGuards'
import type { ElementMetadata } from '../../types'

interface Reference {
  reference?: string
  display?: string
}

interface ReferenceFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function ReferenceField({ element, value, onChange }: ReferenceFieldProps) {
  const { t } = useTranslation('measures')
  const { state } = useBundleBuilder()
  const ref = asObject(value) as Reference

  // Build reference options from bundle entries, filtered by allowed target types
  const targets = useMemo(() => element.referenceTargets || [], [element.referenceTargets])
  const options = useMemo(
    () => state.entries
      .filter((e) => targets.length === 0 || targets.includes(e.resourceType))
      .map((e) => {
        const label = `${e.resourceType}/${(e.resourceData.id as string) || e.id}`
        return { value: label, label }
      }),
    [state.entries, targets]
  )

  return (
    <FieldWrapper
      name={element.name}
      isRequired={element.isRequired}
      extra={targets.length > 0 ? (
        <Typography component="span" variant="caption" color="text.disabled" sx={{ ml: 1 }}>
          ({targets.join(', ')})
        </Typography>
      ) : undefined}
    >
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          select
          label={t('testCaseBuilder.fields.reference')}
          size="small"
          value={ref.reference || ''}
          onChange={(e) => onChange({ ...ref, reference: e.target.value || undefined })}
          sx={{ flex: 1 }}
        >
          <MenuItem value="">{t('testCaseBuilder.fields.selectOption')}</MenuItem>
          {options.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
          ))}
        </TextField>
        <TextField
          label={t('testCaseBuilder.fields.display')}
          size="small"
          value={ref.display || ''}
          onChange={(e) => onChange({ ...ref, display: e.target.value || undefined })}
          sx={{ flex: 1 }}
        />
      </Box>
    </FieldWrapper>
  )
}
