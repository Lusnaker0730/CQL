import { useState, useEffect, useRef } from 'react'
import {
  Box, TextField, Typography, Autocomplete, Button, IconButton, Tooltip,
  FormControl, InputLabel, Select, MenuItem,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon, MenuBook as MenuBookIcon, Search as SearchIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { useCurrentResourceType } from '../../contexts/ResourceTypeContext'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'
import TwcoreCodePicker from './TwcoreCodePicker'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
import type { ElementMetadata, CodeSearchResult } from '../../types'

interface Coding {
  system?: string
  code?: string
  display?: string
}

interface CodeableConcept {
  coding?: Coding[]
  text?: string
}

interface CodeableConceptFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
}

function CodingField({
  coding,
  onChange,
  onRemove,
  onTwcoreBrowse,
  onTerminologySearch,
  bindingUrl,
}: {
  coding: Coding
  onChange: (coding: Coding) => void
  onRemove: () => void
  onTwcoreBrowse: () => void
  onTerminologySearch: () => void
  bindingUrl?: string | null
}) {
  const { t } = useTranslation('measures')
  const [searchText, setSearchText] = useState(coding.code || '')
  const [debouncedSearch, setDebouncedSearch] = useState(searchText)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  // Sync local input when coding.code changes externally (e.g. TWCORE picker, terminology drawer)
  useEffect(() => {
    setSearchText(coding.code || '')
  }, [coding.code])

  useEffect(() => {
    debounceRef.current = setTimeout(() => setDebouncedSearch(searchText), SEARCH_DEBOUNCE_CODE_MS)
    return () => clearTimeout(debounceRef.current)
  }, [searchText])

  const { data: options = [] } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', bindingUrl || coding.system, debouncedSearch],
    queryFn: () => fhirApi.searchCodes(bindingUrl || coding.system || '', debouncedSearch, 20),
    enabled: (!!bindingUrl || !!coding.system) && debouncedSearch.length >= 1,
    staleTime: 30_000,
  })

  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', mb: 1 }}>
      <TextField
        label={t('testCaseBuilder.fields.system')}
        size="small"
        value={coding.system || ''}
        onChange={(e) => onChange({ ...coding, system: e.target.value })}
        sx={{ flex: 1, fontSize: '0.8rem' }}
      />
      <Autocomplete
        freeSolo
        options={options}
        getOptionLabel={(opt) =>
          typeof opt === 'string' ? opt : `${opt.code} — ${opt.display}`
        }
        inputValue={searchText}
        onInputChange={(_, v) => setSearchText(v)}
        onChange={(_, newVal) => {
          if (typeof newVal === 'string') {
            onChange({ ...coding, code: newVal })
          } else if (newVal) {
            onChange({ ...coding, code: newVal.code, display: newVal.display, system: newVal.system || coding.system })
          }
        }}
        renderInput={(params) => (
          <TextField {...params} label={t('testCaseBuilder.fields.code')} size="small" />
        )}
        sx={{ flex: 1 }}
      />
      <TextField
        label={t('testCaseBuilder.fields.display')}
        size="small"
        value={coding.display || ''}
        onChange={(e) => onChange({ ...coding, display: e.target.value })}
        sx={{ flex: 1 }}
      />
      <Tooltip title={t('testCaseBuilder.fields.searchTerminology')}>
        <IconButton size="small" onClick={onTerminologySearch} sx={{ mt: 0.5 }} aria-label={t('testCaseBuilder.fields.searchTerminology')}>
          <SearchIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t('testCaseBuilder.fields.browseTwcore')}>
        <IconButton size="small" onClick={onTwcoreBrowse} sx={{ mt: 0.5 }} aria-label={t('testCaseBuilder.fields.browseTwcoreAria')}>
          <MenuBookIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <IconButton size="small" onClick={onRemove} sx={{ mt: 0.5 }} aria-label={t('testCaseBuilder.fields.removeCoding')}>
        <DeleteIcon fontSize="small" />
      </IconButton>
    </Box>
  )
}

export default function CodeableConceptField({ element, value, onChange }: CodeableConceptFieldProps) {
  const { t } = useTranslation('measures')
  const cc = (value as CodeableConcept) || {}
  const codings = cc.coding || []
  const resourceType = useCurrentResourceType()
  const [twcoreOpen, setTwcoreOpen] = useState(false)
  const [twcoreTargetIdx, setTwcoreTargetIdx] = useState<number>(0)
  const { openDrawer } = useTerminologyDrawer()

  // When boundCodes are available (required/extensible binding), show a simple dropdown
  const hasBoundCodes = element.boundCodes && element.boundCodes.length > 0
  if (hasBoundCodes) {
    const selectedCode = codings[0]?.code || ''
    const handleSelect = (code: string) => {
      if (!code) {
        onChange(undefined)
        return
      }
      onChange({
        coding: [{
          system: element.bindingCodeSystemUrl || element.bindingValueSetUrl || undefined,
          code,
          display: code.charAt(0).toUpperCase() + code.slice(1),
        }],
      })
    }
    return (
      <FormControl fullWidth size="small" required={element.isRequired} sx={{ mb: 1 }}>
        <InputLabel>{element.name}</InputLabel>
        <Select
          value={selectedCode}
          onChange={(e) => handleSelect(e.target.value)}
          label={element.name}
        >
          <MenuItem value=""><em>{t('testCaseBuilder.fields.selectCode', 'Select...')}</em></MenuItem>
          {element.boundCodes.map((code) => (
            <MenuItem key={code} value={code}>{code}</MenuItem>
          ))}
        </Select>
      </FormControl>
    )
  }

  const updateCoding = (index: number, coding: Coding) => {
    const newCodings = [...codings]
    newCodings[index] = coding
    onChange({ ...cc, coding: newCodings })
  }

  const addCoding = () => {
    onChange({ ...cc, coding: [...codings, { system: '', code: '', display: '' }] })
  }

  const removeCoding = (index: number) => {
    const newCodings = codings.filter((_, i) => i !== index)
    onChange({ ...cc, coding: newCodings.length > 0 ? newCodings : undefined })
  }

  const handleTwcoreBrowse = (index: number) => {
    setTwcoreTargetIdx(index)
    setTwcoreOpen(true)
  }

  const handleTwcoreSelect = (selected: { system: string; code: string; display: string }) => {
    updateCoding(twcoreTargetIdx, {
      ...codings[twcoreTargetIdx],
      system: selected.system,
      code: selected.code,
      display: selected.display,
    })
  }

  const handleTerminologySearch = (index: number) => {
    openDrawer({
      tab: 0,
      system: codings[index]?.system || element.bindingValueSetUrl || '',
      onSelect: (coding) => {
        updateCoding(index, {
          ...codings[index],
          system: coding.system,
          code: coding.code,
          display: coding.display,
        })
      },
    })
  }

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {element.name} {element.isRequired && '*'}
      </Typography>

      {codings.map((coding, i) => (
        <CodingField
          key={i}
          coding={coding}
          onChange={(c) => updateCoding(i, c)}
          onRemove={() => removeCoding(i)}
          onTwcoreBrowse={() => handleTwcoreBrowse(i)}
          onTerminologySearch={() => handleTerminologySearch(i)}
          bindingUrl={element.bindingValueSetUrl}
        />
      ))}

      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <Button size="small" startIcon={<AddIcon />} onClick={addCoding} sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
          {t('testCaseBuilder.fields.addCoding')}
        </Button>
      </Box>

      <TextField
        label={t('testCaseBuilder.fields.text')}
        size="small"
        fullWidth
        value={cc.text || ''}
        onChange={(e) => onChange({ ...cc, text: e.target.value || undefined })}
        sx={{ mt: 1 }}
      />

      {twcoreOpen && (
        <TwcoreCodePicker
          open={twcoreOpen}
          onClose={() => setTwcoreOpen(false)}
          onSelect={handleTwcoreSelect}
          resourceType={resourceType}
        />
      )}
    </Box>
  )
}
