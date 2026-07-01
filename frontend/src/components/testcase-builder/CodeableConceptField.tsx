import { useState, useEffect, useCallback } from 'react'
import {
  Box, TextField, Autocomplete, Button, IconButton, Tooltip, Alert, Typography,
  FormControl, InputLabel, Select, MenuItem,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon, MenuBook as MenuBookIcon, Search as SearchIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { useCurrentResourceType } from '../../contexts/ResourceTypeContext'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { asObject } from '../../utils/fhirGuards'
import TwcoreCodePicker from './TwcoreCodePicker'
import FieldWrapper from './FieldWrapper'
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

// Hoisted sx — these objects don't depend on props/state, so allocating once
// at module load avoids re-creating them on every render of every CodingField.
const CODING_ROW_SX = { display: 'flex', gap: 1, alignItems: 'flex-start', mb: 1 }
const SYSTEM_FIELD_SX = { flex: 1, fontSize: '0.8rem' }
const FLEX_1 = { flex: 1 }
const ICON_TOP_SX = { mt: 0.5 }
const ADD_CODING_BUTTON_SX = { textTransform: 'none', fontSize: '0.75rem' }
const ADD_CODING_BOX_SX = { display: 'flex', gap: 1, alignItems: 'center' }
const TEXT_FIELD_SX = { mt: 1 }
const FORM_CONTROL_SX = { mb: 1 }
const ALERT_SX = { mt: 0.5, py: 0, fontSize: '0.7rem' }

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
  const debouncedSearch = useDebouncedValue(searchText, SEARCH_DEBOUNCE_CODE_MS)

  // Sync local input when coding.code changes externally (e.g. TWCORE picker, terminology drawer)
  useEffect(() => {
    setSearchText(coding.code || '')
  }, [coding.code])

  const { data: options = [], error } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', bindingUrl || coding.system, debouncedSearch],
    queryFn: () => fhirApi.searchCodes(bindingUrl || coding.system || '', debouncedSearch, 20),
    enabled: (!!bindingUrl || !!coding.system) && debouncedSearch.length >= 1,
    staleTime: 30_000,
  })

  const getOptionLabel = useCallback(
    (opt: string | CodeSearchResult) =>
      typeof opt === 'string' ? opt : `${opt.code} — ${opt.display}`,
    [],
  )

  return (
    <Box>
      <Box sx={CODING_ROW_SX}>
        <TextField
          label={t('testCaseBuilder.fields.system')}
          size="small"
          value={coding.system || ''}
          onChange={(e) => onChange({ ...coding, system: e.target.value })}
          sx={SYSTEM_FIELD_SX}
        />
        <Autocomplete
          freeSolo
          options={options}
          getOptionLabel={getOptionLabel}
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
          sx={FLEX_1}
        />
        <TextField
          label={t('testCaseBuilder.fields.display')}
          size="small"
          value={coding.display || ''}
          onChange={(e) => onChange({ ...coding, display: e.target.value })}
          sx={FLEX_1}
        />
        <Tooltip title={t('testCaseBuilder.fields.searchTerminology')}>
          <IconButton size="small" onClick={onTerminologySearch} sx={ICON_TOP_SX} aria-label={t('testCaseBuilder.fields.searchTerminology')}>
            <SearchIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('testCaseBuilder.fields.browseTwcore')}>
          <IconButton size="small" onClick={onTwcoreBrowse} sx={ICON_TOP_SX} aria-label={t('testCaseBuilder.fields.browseTwcoreAria')}>
            <MenuBookIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <IconButton size="small" onClick={onRemove} sx={ICON_TOP_SX} aria-label={t('testCaseBuilder.fields.removeCoding')}>
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Box>
      {error && (
        <Alert severity="error" variant="outlined" sx={ALERT_SX}>
          {t('testCaseBuilder.fields.codeSearchError', {
            message: error instanceof Error ? error.message : String(error),
          })}
        </Alert>
      )}
    </Box>
  )
}

export default function CodeableConceptField({ element, value, onChange }: CodeableConceptFieldProps) {
  const { t } = useTranslation('measures')
  const cc = asObject(value) as CodeableConcept
  const codings: Coding[] = Array.isArray(cc.coding) ? cc.coding : []
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
      <FormControl fullWidth size="small" required={element.isRequired} sx={FORM_CONTROL_SX}>
        <InputLabel>{element.name}</InputLabel>
        <Select
          value={selectedCode}
          onChange={(e) => handleSelect(e.target.value)}
          label={element.name}
        >
          <MenuItem value=""><em>{t('testCaseBuilder.fields.selectCode')}</em></MenuItem>
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
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
      {codings.map((coding, i) => (
        // Stable-ish key: prefer a content-derived signature so add/remove
        // doesn't re-mount unrelated rows. Fall back to index for empty new rows.
        (<CodingField
          key={`${coding.system ?? ''}|${coding.code ?? ''}|${i}`}
          coding={coding}
          onChange={(c) => updateCoding(i, c)}
          onRemove={() => removeCoding(i)}
          onTwcoreBrowse={() => handleTwcoreBrowse(i)}
          onTerminologySearch={() => handleTerminologySearch(i)}
          bindingUrl={element.bindingValueSetUrl}
        />)
      ))}
      {codings.length === 0 && (
        <Typography
          variant="caption"
          sx={{
            color: "text.disabled",
            display: 'block',
            mb: 0.5
          }}>
          {t('testCaseBuilder.fields.noCodings')}
        </Typography>
      )}
      <Box sx={ADD_CODING_BOX_SX}>
        <Button size="small" startIcon={<AddIcon />} onClick={addCoding} sx={ADD_CODING_BUTTON_SX}>
          {t('testCaseBuilder.fields.addCoding')}
        </Button>
      </Box>
      <TextField
        label={t('testCaseBuilder.fields.text')}
        size="small"
        fullWidth
        value={cc.text || ''}
        onChange={(e) => onChange({ ...cc, text: e.target.value || undefined })}
        sx={TEXT_FIELD_SX}
      />
      {twcoreOpen && (
        <TwcoreCodePicker
          open={twcoreOpen}
          onClose={() => setTwcoreOpen(false)}
          onSelect={handleTwcoreSelect}
          resourceType={resourceType}
        />
      )}
    </FieldWrapper>
  );
}
