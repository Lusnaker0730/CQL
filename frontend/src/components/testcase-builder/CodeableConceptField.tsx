import { useState } from 'react'
import { Box, TextField, Typography, Autocomplete, Button, IconButton } from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../../api'
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
  bindingUrl,
}: {
  coding: Coding
  onChange: (coding: Coding) => void
  onRemove: () => void
  bindingUrl?: string | null
}) {
  const [searchText, setSearchText] = useState(coding.code || '')

  const { data: options = [] } = useQuery<CodeSearchResult[]>({
    queryKey: ['code-search', bindingUrl || coding.system, searchText],
    queryFn: () => fhirApi.searchCodes(bindingUrl || coding.system || '', searchText, 20),
    enabled: (!!bindingUrl || !!coding.system) && searchText.length >= 1,
    staleTime: 30_000,
  })

  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', mb: 1 }}>
      <TextField
        label="system"
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
          <TextField {...params} label="code" size="small" />
        )}
        sx={{ flex: 1 }}
      />
      <TextField
        label="display"
        size="small"
        value={coding.display || ''}
        onChange={(e) => onChange({ ...coding, display: e.target.value })}
        sx={{ flex: 1 }}
      />
      <IconButton size="small" onClick={onRemove} sx={{ mt: 0.5 }}>
        <DeleteIcon fontSize="small" />
      </IconButton>
    </Box>
  )
}

export default function CodeableConceptField({ element, value, onChange }: CodeableConceptFieldProps) {
  const cc = (value as CodeableConcept) || {}
  const codings = cc.coding || []

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
          bindingUrl={element.bindingValueSetUrl}
        />
      ))}

      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <Button size="small" startIcon={<AddIcon />} onClick={addCoding} sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
          Add Coding
        </Button>
      </Box>

      <TextField
        label="text"
        size="small"
        fullWidth
        value={cc.text || ''}
        onChange={(e) => onChange({ ...cc, text: e.target.value || undefined })}
        sx={{ mt: 1 }}
      />
    </Box>
  )
}
