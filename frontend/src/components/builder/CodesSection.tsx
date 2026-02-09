import { useState, useEffect } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  List,
  ListItem,
  ListItemText,
  ListItemButton,
  Alert,
  CircularProgress,
  Collapse,
  Paper,
} from '@mui/material'
import { Add as AddIcon, Search as SearchIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useLookupCode, useSearchCodes } from '../../hooks/useTerminology'

interface CodesSectionProps {
  codes: string[]
  onInsert: (cqlSnippet: string) => void
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

export default function CodesSection({ codes, onInsert }: CodesSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [systemUrl, setSystemUrl] = useState('')
  const [systemAlias, setSystemAlias] = useState('')
  const [codeValue, setCodeValue] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [showSearch, setShowSearch] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  const lookupMutation = useLookupCode()
  const { data: searchResults, isFetching: isSearching } = useSearchCodes(systemUrl, debouncedSearch)

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
    setShowSearch(false)
    setSearchText('')
    setDebouncedSearch('')
  }

  const handleAdd = () => {
    if (!systemUrl || !codeValue || !systemAlias) return
    const csSnippet = `codesystem "${systemAlias}": '${systemUrl}'`
    const codeSnippet = `code "${displayName || codeValue}": '${codeValue}' from "${systemAlias}"${displayName ? ` display '${displayName}'` : ''}`
    onInsert(`${csSnippet}\n${codeSnippet}`)
    resetForm()
  }

  const resetForm = () => {
    setShowForm(false)
    setSystemUrl('')
    setSystemAlias('')
    setCodeValue('')
    setDisplayName('')
    setShowSearch(false)
    setSearchText('')
    setDebouncedSearch('')
  }

  return (
    <Stack spacing={1}>
      {codes.length > 0 ? (
        <List dense disablePadding>
          {codes.map((code, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                    {code}
                  </Typography>
                }
              />
            </ListItem>
          ))}
        </List>
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
            <Button
              size="small"
              variant="outlined"
              onClick={() => setShowSearch(!showSearch)}
              disabled={!systemUrl}
              startIcon={<SearchIcon />}
              sx={{ minWidth: 80 }}
            >
              Search
            </Button>
          </Stack>

          <Collapse in={showSearch}>
            <Stack spacing={1} sx={{ mt: 0.5 }}>
              <TextField
                size="small"
                label="Search by text"
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder='e.g., "diabetes"'
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
              {searchResults && searchResults.length === 0 && debouncedSearch.length >= 2 && !isSearching && (
                <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
                  No results found
                </Typography>
              )}
            </Stack>
          </Collapse>

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

          <Stack direction="row" spacing={1}>
            <GradientButton onClick={handleAdd}
              disabled={!systemUrl || !codeValue || !systemAlias}>
              Insert
            </GradientButton>
            <Button size="small" onClick={resetForm}>Cancel</Button>
          </Stack>
        </Stack>
      )}
    </Stack>
  )
}
