import { useState } from 'react'
import { Box, TextField, Typography, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ElementMetadata } from '../../types'
import ElementField from './ElementField'

interface ChoiceTypeFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
  depth: number
}

export default function ChoiceTypeField({ element, value, onChange, depth }: ChoiceTypeFieldProps) {
  const { t } = useTranslation('measures')
  const choiceTypes = element.choiceTypes || []
  const [selectedType, setSelectedType] = useState<string>(choiceTypes[0] || '')

  const handleTypeChange = (newType: string) => {
    setSelectedType(newType)
    onChange(undefined) // Clear value when switching type
  }

  // Create a synthetic ElementMetadata for the chosen type
  const syntheticElement: ElementMetadata = {
    name: element.name + selectedType.charAt(0).toUpperCase() + selectedType.slice(1),
    path: element.path,
    type: selectedType,
    isArray: false,
    isRequired: element.isRequired,
    min: element.min,
    max: element.max,
    isChoiceType: false,
    choiceTypes: [],
    bindingStrength: null,
    bindingValueSetUrl: null,
    boundCodes: [],
    children: [],
    description: element.description,
    referenceTargets: [],
  }

  return (
    <Box sx={{ mb: 1 }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {element.name}[x] {element.isRequired && '*'}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
        <TextField
          select
          size="small"
          label={t('testCaseBuilder.choiceType.type')}
          value={selectedType}
          onChange={(e) => handleTypeChange(e.target.value)}
          sx={{ width: 150 }}
        >
          {choiceTypes.map((t) => (
            <MenuItem key={t} value={t}>{t}</MenuItem>
          ))}
        </TextField>
        <Box sx={{ flex: 1 }}>
          <ElementField
            element={syntheticElement}
            path={element.path}
            value={value}
            onChange={onChange}
            depth={depth}
          />
        </Box>
      </Box>
    </Box>
  )
}
