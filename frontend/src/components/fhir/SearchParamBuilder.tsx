import { useState, useEffect } from 'react'
import {
  Stack,
  TextField,
  IconButton,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  ToggleButton,
  ToggleButtonGroup,
  Button,
  Typography,
  Box,
} from '@mui/material'
import { Delete as DeleteIcon, Add as AddIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { RESOURCE_SEARCH_PARAMS } from '../../utils/fhirBrowserUtils'

interface SearchParam {
  name: string
  value: string
}

interface SearchParamBuilderProps {
  resourceType: string
  value: string
  onChange: (value: string) => void
  mode: 'structured' | 'raw'
  onModeChange: (mode: 'structured' | 'raw') => void
}

function parseParamsString(raw: string): SearchParam[] {
  if (!raw.trim()) return [{ name: '', value: '' }]
  const pairs = raw.split('&').filter(Boolean)
  const result = pairs.map(pair => {
    const idx = pair.indexOf('=')
    if (idx === -1) return { name: pair, value: '' }
    return { name: pair.substring(0, idx), value: decodeURIComponent(pair.substring(idx + 1)) }
  })
  return result.length > 0 ? result : [{ name: '', value: '' }]
}

function serializeParams(params: SearchParam[]): string {
  return params
    .filter(p => p.name && p.value)
    .map(p => `${p.name}=${encodeURIComponent(p.value)}`)
    .join('&')
}

export default function SearchParamBuilder({
  resourceType,
  value,
  onChange,
  mode,
  onModeChange,
}: SearchParamBuilderProps) {
  const { t } = useTranslation('fhir')
  const [params, setParams] = useState<SearchParam[]>(() => parseParamsString(value))
  const availableParams = RESOURCE_SEARCH_PARAMS[resourceType] || [
    { name: '_id', type: 'token', label: 'ID' },
  ]

  // Re-parse when the parent prop `value` changes externally — e.g. a
  // history-replay calls `setSearchParams(entry.params)` on the parent and
  // the structured rows must reflect the new params. To avoid feedback with
  // the local onChange path, skip when the incoming value already matches
  // the serialized local state.
  useEffect(() => {
    if (mode !== 'structured') return
    if (serializeParams(params) === value) return
    setParams(parseParamsString(value))
    // `params` is intentionally excluded — including it creates the very
    // loop we're avoiding (every setParams triggers re-run).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, mode])

  const handleModeChange = (_: unknown, newMode: 'structured' | 'raw' | null) => {
    if (!newMode) return
    if (newMode === 'structured') {
      setParams(parseParamsString(value))
    } else {
      onChange(serializeParams(params))
    }
    onModeChange(newMode)
  }

  const updateParam = (index: number, field: 'name' | 'value', newVal: string) => {
    const updated = [...params]
    updated[index] = { ...updated[index], [field]: newVal }
    setParams(updated)
    onChange(serializeParams(updated))
  }

  const addParam = () => {
    setParams([...params, { name: '', value: '' }])
  }

  const removeParam = (index: number) => {
    const updated = params.filter((_, i) => i !== index)
    const result = updated.length > 0 ? updated : [{ name: '', value: '' }]
    setParams(result)
    onChange(serializeParams(result))
  }

  return (
    <Box>
      <Stack direction="row" spacing={1} alignItems="center" mb={1}>
        <Typography variant="caption" color="text.secondary">{t('searchParams.title')}</Typography>
        <ToggleButtonGroup
          value={mode}
          exclusive
          onChange={handleModeChange}
          size="small"
          sx={{ '& .MuiToggleButton-root': { py: 0, px: 1, fontSize: '0.7rem', textTransform: 'none' } }}
        >
          <ToggleButton value="structured">{t('searchParams.structured')}</ToggleButton>
          <ToggleButton value="raw">{t('searchParams.raw')}</ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {mode === 'raw' ? (
        <TextField
          value={value}
          onChange={(e) => onChange(e.target.value)}
          size="small"
          fullWidth
          placeholder={t('searchParams.rawPlaceholder')}
          helperText={t('searchParams.rawHelperText')}
        />
      ) : (
        <Stack spacing={1}>
          {params.map((param, idx) => (
            <Stack key={idx} direction="row" spacing={1} alignItems="center">
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>{t('searchParams.parameterLabel')}</InputLabel>
                <Select
                  value={param.name}
                  onChange={(e) => updateParam(idx, 'name', e.target.value)}
                  label={t('searchParams.parameterLabel')}
                >
                  {availableParams.map(p => (
                    <MenuItem key={p.name} value={p.name}>{p.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                value={param.value}
                onChange={(e) => updateParam(idx, 'value', e.target.value)}
                size="small"
                fullWidth
                placeholder={param.name ? t('searchParams.valueForParam', { name: param.name }) : t('searchParams.valuePlaceholder')}
              />
              <IconButton size="small" onClick={() => removeParam(idx)} color="error" aria-label={t('searchParams.removeParameter')}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
          <Button
            size="small"
            startIcon={<AddIcon />}
            onClick={addParam}
            sx={{ alignSelf: 'flex-start', textTransform: 'none' }}
          >
            {t('searchParams.addParameter')}
          </Button>
        </Stack>
      )}
    </Box>
  )
}
