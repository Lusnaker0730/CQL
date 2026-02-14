import { Autocomplete, TextField, Typography } from '@mui/material'
import { FHIR_SERVER_PRESETS, type FhirServerPreset } from '../../constants/fhirServers'
import { validateFhirUrl } from '../../utils/validation'
import { useState } from 'react'

interface FhirServerUrlFieldProps {
  value: string
  onChange: (value: string) => void
  label?: string
  size?: 'small' | 'medium'
  fullWidth?: boolean
  error?: boolean
  helperText?: string | null
  /** If true, component manages its own validation state on blur */
  selfValidate?: boolean
}

export default function FhirServerUrlField({
  value,
  onChange,
  label = 'FHIR Server URL',
  size = 'small',
  fullWidth = true,
  error: externalError,
  helperText: externalHelperText,
  selfValidate = false,
}: FhirServerUrlFieldProps) {
  const [internalError, setInternalError] = useState<string | null>(null)

  const handleBlur = () => {
    if (selfValidate) {
      setInternalError(validateFhirUrl(value))
    }
  }

  const isError = externalError ?? !!internalError
  const helperText = externalHelperText ?? internalError

  return (
    <Autocomplete
      freeSolo
      size={size}
      fullWidth={fullWidth}
      options={FHIR_SERVER_PRESETS}
      getOptionLabel={(option) =>
        typeof option === 'string' ? option : option.url
      }
      renderOption={(props, option) => (
        <li {...props} key={option.url}>
          <div>
            <Typography variant="body2" fontWeight={600}>{option.label}</Typography>
            <Typography variant="caption" color="text.secondary">
              {option.description}
            </Typography>
          </div>
        </li>
      )}
      inputValue={value}
      onInputChange={(_, newValue) => {
        onChange(newValue)
        if (selfValidate && internalError) {
          setInternalError(null)
        }
      }}
      onChange={(_, newValue) => {
        if (newValue && typeof newValue !== 'string') {
          onChange((newValue as FhirServerPreset).url)
        }
      }}
      filterOptions={(options, { inputValue }) => {
        const lower = inputValue.toLowerCase()
        return options.filter(
          (o) =>
            o.url.toLowerCase().includes(lower) ||
            o.label.toLowerCase().includes(lower) ||
            o.description.toLowerCase().includes(lower)
        )
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          error={isError}
          helperText={helperText}
          onBlur={handleBlur}
        />
      )}
    />
  )
}
