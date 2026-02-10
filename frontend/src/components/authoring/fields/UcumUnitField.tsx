import { Autocomplete, TextField } from '@mui/material'

/**
 * Common UCUM units used in clinical decision support.
 * Grouped by category for better discoverability.
 */
const UCUM_UNITS = [
  // Concentration / Mass per Volume
  { value: 'mg/dL', label: 'mg/dL', category: 'Concentration' },
  { value: 'g/dL', label: 'g/dL', category: 'Concentration' },
  { value: 'g/L', label: 'g/L', category: 'Concentration' },
  { value: 'mg/L', label: 'mg/L', category: 'Concentration' },
  { value: 'ug/dL', label: 'ug/dL (mcg/dL)', category: 'Concentration' },
  { value: 'ug/L', label: 'ug/L (mcg/L)', category: 'Concentration' },
  { value: 'ng/dL', label: 'ng/dL', category: 'Concentration' },
  { value: 'ng/mL', label: 'ng/mL', category: 'Concentration' },
  { value: 'pg/mL', label: 'pg/mL', category: 'Concentration' },
  { value: 'mmol/L', label: 'mmol/L', category: 'Concentration' },
  { value: 'umol/L', label: 'umol/L', category: 'Concentration' },
  { value: 'nmol/L', label: 'nmol/L', category: 'Concentration' },
  { value: 'mEq/L', label: 'mEq/L', category: 'Concentration' },
  { value: '[IU]/L', label: 'IU/L', category: 'Concentration' },
  { value: '[IU]/mL', label: 'IU/mL', category: 'Concentration' },
  { value: 'U/L', label: 'U/L', category: 'Concentration' },

  // Percentages & Ratios
  { value: '%', label: '%', category: 'Ratio' },
  { value: '10*3/uL', label: '10^3/uL (K/uL)', category: 'Ratio' },
  { value: '10*6/uL', label: '10^6/uL (M/uL)', category: 'Ratio' },
  { value: '10*9/L', label: '10^9/L', category: 'Ratio' },
  { value: '10*12/L', label: '10^12/L', category: 'Ratio' },

  // Pressure
  { value: 'mm[Hg]', label: 'mmHg', category: 'Pressure' },
  { value: 'cm[H2O]', label: 'cmH2O', category: 'Pressure' },

  // Weight & Mass
  { value: 'kg', label: 'kg', category: 'Mass' },
  { value: 'g', label: 'g', category: 'Mass' },
  { value: 'mg', label: 'mg', category: 'Mass' },
  { value: 'ug', label: 'ug (mcg)', category: 'Mass' },
  { value: 'lb', label: 'lb', category: 'Mass' },
  { value: 'oz', label: 'oz', category: 'Mass' },

  // Length & Height
  { value: 'cm', label: 'cm', category: 'Length' },
  { value: 'm', label: 'm', category: 'Length' },
  { value: 'mm', label: 'mm', category: 'Length' },
  { value: '[in_i]', label: 'in (inches)', category: 'Length' },
  { value: '[ft_i]', label: 'ft (feet)', category: 'Length' },

  // Temperature
  { value: 'Cel', label: 'Celsius (°C)', category: 'Temperature' },
  { value: '[degF]', label: 'Fahrenheit (°F)', category: 'Temperature' },

  // Volume
  { value: 'L', label: 'L', category: 'Volume' },
  { value: 'mL', label: 'mL', category: 'Volume' },
  { value: 'dL', label: 'dL', category: 'Volume' },
  { value: 'uL', label: 'uL', category: 'Volume' },

  // Time
  { value: 'a', label: 'years', category: 'Time' },
  { value: 'mo', label: 'months', category: 'Time' },
  { value: 'wk', label: 'weeks', category: 'Time' },
  { value: 'd', label: 'days', category: 'Time' },
  { value: 'h', label: 'hours', category: 'Time' },
  { value: 'min', label: 'minutes', category: 'Time' },
  { value: 's', label: 'seconds', category: 'Time' },

  // Rate
  { value: '/min', label: '/min (per minute)', category: 'Rate' },
  { value: '/h', label: '/h (per hour)', category: 'Rate' },
  { value: 'mL/min', label: 'mL/min', category: 'Rate' },
  { value: 'mL/min/{1.73_m2}', label: 'mL/min/1.73m² (GFR)', category: 'Rate' },
  { value: '{beats}/min', label: 'beats/min (bpm)', category: 'Rate' },
  { value: '{breaths}/min', label: 'breaths/min', category: 'Rate' },

  // Dosage
  { value: 'mg/kg', label: 'mg/kg', category: 'Dosage' },
  { value: 'mg/m2', label: 'mg/m²', category: 'Dosage' },
  { value: 'ug/kg/min', label: 'mcg/kg/min', category: 'Dosage' },

  // Area
  { value: 'm2', label: 'm² (BSA)', category: 'Area' },
]

interface UcumUnitFieldProps {
  value: string
  onChange: (value: string) => void
  label?: string
  placeholder?: string
  sx?: object
}

export default function UcumUnitField({
  value,
  onChange,
  label = 'Unit',
  placeholder = 'Search units...',
  sx,
}: UcumUnitFieldProps) {
  const selectedOption = UCUM_UNITS.find((u) => u.value === value) || null

  return (
    <Autocomplete
      size="small"
      options={UCUM_UNITS}
      groupBy={(option) => option.category}
      getOptionLabel={(option) => (typeof option === 'string' ? option : `${option.label} (${option.value})`)}
      value={selectedOption}
      onChange={(_, newValue) => {
        if (!newValue) {
          onChange('')
        } else if (typeof newValue === 'string') {
          onChange(newValue)
        } else {
          onChange(newValue.value)
        }
      }}
      inputValue={selectedOption ? `${selectedOption.label} (${selectedOption.value})` : value}
      onInputChange={(_, newInput, reason) => {
        if (reason === 'input') {
          onChange(newInput)
        }
      }}
      freeSolo
      renderInput={(params) => (
        <TextField {...params} label={label} placeholder={placeholder} />
      )}
      sx={{ minWidth: 180, ...sx }}
    />
  )
}
