import { useState } from 'react'
import {
  Autocomplete, TextField, Typography, Box, IconButton, Tooltip, Stack,
  FormControl, InputLabel, Select, MenuItem, FormHelperText,
} from '@mui/material'
import { MenuBook as MenuBookIcon } from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { useCurrentResourceType } from '../../contexts/ResourceTypeContext'
import TwcoreCodePicker from './TwcoreCodePicker'
import type { ElementMetadata, CodeSearchResult } from '../../types'

interface CodeFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function CodeField({ element, value, onChange }: CodeFieldProps) {
  const [inputValue, setInputValue] = useState(String(value || ''))
  const [twcoreOpen, setTwcoreOpen] = useState(false)
  const resourceType = useCurrentResourceType()

  const hasBinding = !!element.bindingValueSetUrl
  const isRequiredBinding = element.bindingStrength === 'required' && element.boundCodes.length > 0

  const { data: options = [] } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', element.bindingValueSetUrl, inputValue],
    queryFn: () => fhirApi.searchCodes(element.bindingValueSetUrl || '', inputValue, 20),
    enabled: hasBinding && !isRequiredBinding && inputValue.length >= 1,
    staleTime: 30_000,
  })

  const twcoreButton = (
    <Tooltip title="Browse TWCORE terminology">
      <IconButton size="small" onClick={() => setTwcoreOpen(true)} aria-label="Browse TWCORE">
        <MenuBookIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  )

  const twcorePicker = (
    <TwcoreCodePicker
      open={twcoreOpen}
      onClose={() => setTwcoreOpen(false)}
      onSelect={(selected) => {
        onChange(selected.code)
        setInputValue(selected.code)
      }}
      resourceType={resourceType}
    />
  )

  // Required binding with known codes → fixed dropdown, no TWCORE button
  if (isRequiredBinding) {
    return (
      <FormControl fullWidth size="small" required={element.isRequired} sx={{ mb: 1 }}>
        <InputLabel>{element.name}</InputLabel>
        <Select
          value={String(value || '')}
          onChange={(e) => onChange(e.target.value || undefined)}
          label={element.name}
        >
          {element.boundCodes.map((code) => (
            <MenuItem key={code} value={code}>{code}</MenuItem>
          ))}
        </Select>
        {element.bindingValueSetUrl && (
          <FormHelperText>Bound to: {element.bindingValueSetUrl}</FormHelperText>
        )}
      </FormControl>
    )
  }

  if (!hasBinding) {
    return (
      <Stack direction="row" alignItems="flex-start" spacing={0.5} sx={{ mb: 1 }}>
        <TextField
          label={element.name}
          size="small"
          fullWidth
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value || undefined)}
          required={element.isRequired}
          helperText={element.description || undefined}
        />
        {twcoreButton}
        {twcorePicker}
      </Stack>
    )
  }

  return (
    <Stack direction="row" alignItems="flex-start" spacing={0.5} sx={{ mb: 1 }}>
      <Autocomplete
        freeSolo
        options={options}
        getOptionLabel={(opt) =>
          typeof opt === 'string' ? opt : `${opt.code} — ${opt.display}`
        }
        inputValue={inputValue}
        onInputChange={(_, v) => setInputValue(v)}
        onChange={(_, newVal) => {
          if (typeof newVal === 'string') {
            onChange(newVal)
          } else if (newVal) {
            onChange(newVal.code)
          } else {
            onChange(undefined)
          }
        }}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={typeof option === 'string' ? option : option.code}>
            <Box>
              <Typography variant="body2" fontWeight={500}>
                {typeof option === 'string' ? option : option.code}
              </Typography>
              {typeof option !== 'string' && (
                <Typography variant="caption" color="text.secondary">
                  {option.display}
                </Typography>
              )}
            </Box>
          </Box>
        )}
        renderInput={(params) => (
          <TextField
            {...params}
            label={element.name}
            size="small"
            fullWidth
            required={element.isRequired}
            helperText={element.description || `Bound to: ${element.bindingValueSetUrl}`}
          />
        )}
        sx={{ flex: 1 }}
      />
      {twcoreButton}
      {twcorePicker}
    </Stack>
  )
}
