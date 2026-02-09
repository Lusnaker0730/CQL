import { useState } from 'react'
import { Autocomplete, TextField, Typography, Box } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import type { ElementMetadata, CodeSearchResult } from '../../types'

interface CodeFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function CodeField({ element, value, onChange }: CodeFieldProps) {
  const [inputValue, setInputValue] = useState(String(value || ''))

  const hasBinding = !!element.bindingValueSetUrl

  const { data: options = [] } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', element.bindingValueSetUrl, inputValue],
    queryFn: () => fhirApi.searchCodes(element.bindingValueSetUrl || '', inputValue, 20),
    enabled: hasBinding && inputValue.length >= 1,
    staleTime: 30_000,
  })

  if (!hasBinding) {
    return (
      <TextField
        label={element.name}
        size="small"
        fullWidth
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
        required={element.isRequired}
        helperText={element.description || undefined}
        sx={{ mb: 1 }}
      />
    )
  }

  return (
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
      sx={{ mb: 1 }}
    />
  )
}
