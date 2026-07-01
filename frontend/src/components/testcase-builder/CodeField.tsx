import { useState, useEffect, useCallback } from 'react'
import {
  Autocomplete, TextField, Typography, Box, IconButton, Tooltip, Stack, Alert,
  FormControl, InputLabel, Select, MenuItem, FormHelperText,
} from '@mui/material'
import { MenuBook as MenuBookIcon, Search as SearchIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { useCurrentResourceType } from '../../contexts/ResourceTypeContext'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import TwcoreCodePicker from './TwcoreCodePicker'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
import type { ElementMetadata, CodeSearchResult } from '../../types'

interface CodeFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

export default function CodeField({ element, value, onChange }: CodeFieldProps) {
  const { t } = useTranslation('measures')
  const [inputValue, setInputValue] = useState(String(value || ''))
  const [twcoreOpen, setTwcoreOpen] = useState(false)

  // Sync local input when value changes externally
  useEffect(() => {
    setInputValue(String(value || ''))
  }, [value])
  const resourceType = useCurrentResourceType()
  const { openDrawer } = useTerminologyDrawer()
  const debouncedInput = useDebouncedValue(inputValue, SEARCH_DEBOUNCE_CODE_MS)

  const hasBinding = !!element.bindingValueSetUrl
  const isRequiredBinding = element.bindingStrength === 'required' && element.boundCodes.length > 0

  const { data: options = [], error } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', element.bindingValueSetUrl, debouncedInput],
    queryFn: () => fhirApi.searchCodes(element.bindingValueSetUrl || '', debouncedInput, 20),
    enabled: hasBinding && !isRequiredBinding && debouncedInput.length >= 1,
    staleTime: 30_000,
  })

  const renderOption = useCallback(
    (props: React.HTMLAttributes<HTMLLIElement>, option: string | CodeSearchResult) => (
      <Box component="li" {...props} key={typeof option === 'string' ? option : option.code}>
        <Typography variant="body2" sx={{
          fontWeight: 500
        }}>
          {typeof option === 'string' ? option : option.code}
        </Typography>
        {typeof option !== 'string' && (
          <Typography variant="caption" sx={{
            color: "text.secondary"
          }}>
            {option.display}
          </Typography>
        )}
      </Box>
    ),
    [],
  )

  const errorAlert = error ? (
    <Alert severity="error" variant="outlined" sx={{ mt: 0.5, py: 0, fontSize: '0.7rem' }}>
      {t('testCaseBuilder.fields.codeSearchError', {
        message: error instanceof Error ? error.message : String(error),
      })}
    </Alert>
  ) : null

  const searchButton = (
    <Tooltip title={t('testCaseBuilder.fields.searchTerminology')}>
      <IconButton
        size="small"
        onClick={() => openDrawer({
          tab: 0,
          system: element.bindingValueSetUrl || '',
          onSelect: (coding) => {
            onChange(coding.code)
            setInputValue(coding.code)
          },
        })}
        aria-label={t('testCaseBuilder.fields.searchTerminology')}
      >
        <SearchIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  )

  const twcoreButton = (
    <Tooltip title={t('testCaseBuilder.fields.browseTwcore')}>
      <IconButton size="small" onClick={() => setTwcoreOpen(true)} aria-label={t('testCaseBuilder.fields.browseTwcoreAria')}>
        <MenuBookIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  )

  const twcorePicker = twcoreOpen ? (
    <TwcoreCodePicker
      open={twcoreOpen}
      onClose={() => setTwcoreOpen(false)}
      onSelect={(selected) => {
        onChange(selected.code)
        setInputValue(selected.code)
      }}
      resourceType={resourceType}
    />
  ) : null

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
          <FormHelperText>{t('testCaseBuilder.fields.boundTo', { url: element.bindingValueSetUrl })}</FormHelperText>
        )}
      </FormControl>
    )
  }

  if (!hasBinding) {
    return (
      <Box sx={{ mb: 1 }}>
        <Stack direction="row" spacing={0.5} sx={{
          alignItems: "flex-start"
        }}>
          <TextField
            label={element.name}
            size="small"
            fullWidth
            value={value ?? ''}
            onChange={(e) => onChange(e.target.value || undefined)}
            required={element.isRequired}
            helperText={element.description || undefined}
          />
          {searchButton}
          {twcoreButton}
          {twcorePicker}
        </Stack>
        {errorAlert}
      </Box>
    );
  }

  return (
    <Box sx={{ mb: 1 }}>
      <Stack direction="row" spacing={0.5} sx={{
        alignItems: "flex-start"
      }}>
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
          renderOption={renderOption}
          renderInput={(params) => (
            <TextField
              {...params}
              label={element.name}
              size="small"
              fullWidth
              required={element.isRequired}
              helperText={element.description || t('testCaseBuilder.fields.boundTo', { url: element.bindingValueSetUrl })}
            />
          )}
          sx={{ flex: 1 }}
        />
        {searchButton}
        {twcoreButton}
        {twcorePicker}
      </Stack>
      {errorAlert}
    </Box>
  );
}
