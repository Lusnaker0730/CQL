import { useState, useEffect } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  ListItemButton,
  ListItemText,
  Alert,
  CircularProgress,
  Paper,
  List,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from './ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import { useLookupCode, useSearchCodes } from '../../hooks/useTerminology'

interface CodesSectionProps {
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

const COMMON_CODE_SYSTEMS = [
  { value: 'http://loinc.org', label: 'LOINC' },
  { value: 'http://snomed.info/sct', label: 'SNOMED CT' },
  { value: 'http://www.nlm.nih.gov/research/umls/rxnorm', label: 'RxNorm' },
  { value: 'http://hl7.org/fhir/sid/icd-10-cm', label: 'ICD-10-CM' },
  { value: 'http://www.ama-assn.org/go/cpt', label: 'CPT' },
  { value: 'http://terminology.hl7.org/CodeSystem/condition-clinical', label: 'Condition Clinical Status' },
  { value: 'http://terminology.hl7.org/CodeSystem/observation-category', label: 'Observation Category' },
]

/**
 * Parse a code string like: "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
 */
function parseCode(raw: string): { name: string; code: string; system: string; display: string } | null {
  const m = raw.match(/^"([^"]+)":\s*'([^']+)'\s+from\s+"([^"]+)"(?:\s+display\s+'([^']*)')?/)
  if (m) return { name: m[1], code: m[2], system: m[3], display: m[4] || '' }
  return null
}

export default function CodesSection({ codes, onInsert, onDelete, onGoTo, onEdit }: CodesSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [systemUrl, setSystemUrl] = useState('')
  const [systemAlias, setSystemAlias] = useState('')
  const [codeValue, setCodeValue] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [searchText, setSearchText] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const lookupMutation = useLookupCode()
  const { data: searchResults, isFetching: isSearching, isError: isSearchError } = useSearchCodes(systemUrl, debouncedSearch)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 500)
    return () => clearTimeout(timer)
  }, [searchText])

  const handleSystemChange = (url: string) => {
    setSystemUrl(url)
    const match = COMMON_CODE_SYSTEMS.find((s) => s.value === url)
    setSystemAlias(match?.label || '')
    setSearchText('')
    setDebouncedSearch('')
  }

  const handleLookup = () => {
    if (!systemUrl || !codeValue) return
    lookupMutation.mutate(
      { system: systemUrl, code: codeValue },
      {
        onSuccess: (result) => {
          if (result.display) {
            setDisplayName(result.display)
          }
        },
      }
    )
  }

  const handleSelectSearchResult = (code: string, display: string) => {
    setCodeValue(code)
    setDisplayName(display)
    setSearchText('')
    setDebouncedSearch('')
  }

  const handleAdd = () => {
    if (!systemUrl || !codeValue || !systemAlias) return
    const csSnippet = `codesystem "${systemAlias}": '${systemUrl}'`
    const codeSnippet = `code "${displayName || codeValue}": '${codeValue}' from "${systemAlias}"${displayName ? ` display '${displayName}'` : ''}`
    if (editingItem) {
      setPreviewSnippet(codeSnippet)
    } else {
      setPreviewSnippet(`${csSnippet}\n${codeSnippet}`)
    }
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (raw: string) => {
    const parsed = parseCode(raw)
    if (!parsed) return
    setEditingItem(parsed.name)
    // Find the system URL from the alias
    const sys = COMMON_CODE_SYSTEMS.find((s) => s.label === parsed.system)
    setSystemUrl(sys?.value || '')
    setSystemAlias(parsed.system)
    setCodeValue(parsed.code)
    setDisplayName(parsed.display)
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setSystemUrl('')
    setSystemAlias('')
    setCodeValue('')
    setDisplayName('')
    setSearchText('')
    setDebouncedSearch('')
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  return (
    <Stack spacing={0.5}>
      {codes.length > 0 ? (
        codes.map((code, idx) => {
          const parsed = parseCode(code)
          const name = parsed?.name || code
          return (
            <ElementListItem
              key={idx}
              label={name}
              secondaryLabel={parsed ? `${parsed.code} from ${parsed.system}` : undefined}
              onGoTo={() => onGoTo?.(name)}
              onEdit={() => handleStartEdit(code)}
              onDelete={() => setDeleteTarget(name)}
            />
          )
        })
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No codes found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add Code
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            select
            size="small"
            label="Code System"
            value={systemUrl}
            onChange={(e) => handleSystemChange(e.target.value)}
          >
            {COMMON_CODE_SYSTEMS.map((cs) => (
              <MenuItem key={cs.value} value={cs.value}>
                {cs.label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            size="small"
            label="System Alias"
            value={systemAlias}
            onChange={(e) => setSystemAlias(e.target.value)}
          />

          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label="Code"
              value={codeValue}
              onChange={(e) => setCodeValue(e.target.value)}
              fullWidth
            />
            <Button
              size="small"
              variant="outlined"
              onClick={handleLookup}
              disabled={!systemUrl || !codeValue || lookupMutation.isPending}
              sx={{ minWidth: 80 }}
            >
              {lookupMutation.isPending ? <CircularProgress size={16} /> : 'Lookup'}
            </Button>
          </Stack>

          <TextField
            size="small"
            label="Search by text"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder='e.g., "diabetes"'
            disabled={!systemUrl}
            InputProps={{
              endAdornment: isSearching ? <CircularProgress size={16} /> : null,
            }}
          />
          {searchResults && searchResults.length > 0 && (
            <Paper variant="outlined" sx={{ maxHeight: 180, overflow: 'auto' }}>
              <List dense disablePadding>
                {searchResults.map((r) => (
                  <ListItemButton
                    key={r.code}
                    onClick={() => handleSelectSearchResult(r.code, r.display)}
                    sx={{ py: 0.25 }}
                  >
                    <ListItemText
                      primary={
                        <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                          <Typography component="span" sx={{ fontFamily: 'monospace', fontWeight: 600, fontSize: '0.8rem' }}>
                            {r.code}
                          </Typography>
                          {' — '}{r.display}
                        </Typography>
                      }
                    />
                  </ListItemButton>
                ))}
              </List>
            </Paper>
          )}
          {debouncedSearch.length >= 2 && !isSearching && isSearchError && (
            <Alert severity="warning" sx={{ py: 0, fontSize: '0.8rem' }}>
              Search failed — terminology server may be unavailable. Try again later.
            </Alert>
          )}
          {searchResults && searchResults.length === 0 && debouncedSearch.length >= 2 && !isSearching && !isSearchError && (
            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
              No results found
            </Typography>
          )}

          <TextField
            size="small"
            label="Display Name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />

          {lookupMutation.isError && (
            <Alert severity="warning" sx={{ py: 0 }}>
              Code lookup failed — you can still enter manually.
            </Alert>
          )}

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? 'Update' : 'Insert'}
            />
          ) : (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd}
                disabled={!systemUrl || !codeValue || !systemAlias}>
                Preview {editingItem ? 'Update' : 'Insert'}
              </Button>
              <Button size="small" onClick={resetForm}>Cancel</Button>
            </Stack>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        name={deleteTarget || ''}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
