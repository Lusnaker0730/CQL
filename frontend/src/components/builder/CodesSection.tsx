import { useState, useEffect, useMemo } from 'react'
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
  ToggleButtonGroup,
  ToggleButton,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
} from '@mui/material'
import { Add as AddIcon, ExpandMore, Search as SearchIcon, LocalLibrary as BrowseIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import { useLookupCode, useSearchCodes } from '../../hooks/useTerminology'
import { useTwcoreFullCatalog } from '../../hooks/useTwcoreCatalog'
import { ALL_CODE_SYSTEMS, findCodeSystemByUrl, findCodeSystemByLabel } from '../../constants/codeSystems'

interface CodesSectionProps {
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}


/**
 * Parse a code string like: "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
 */
function parseCode(raw: string): { name: string; code: string; system: string; display: string } | null {
  const m = raw.match(/^"([^"]+)":\s*'([^']+)'\s+from\s+"([^"]+)"(?:\s+display\s+'([^']*)')?/)
  if (m) return { name: m[1], code: m[2], system: m[3], display: m[4] || '' }
  return null
}

type BrowseMode = 'manual' | 'twcore'

export default function CodesSection({ codes, onInsert, onDelete, onGoTo, onEdit }: CodesSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [browseMode, setBrowseMode] = useState<BrowseMode>('manual')
  const [systemUrl, setSystemUrl] = useState('')
  const [systemAlias, setSystemAlias] = useState('')
  const [codeValue, setCodeValue] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [searchText, setSearchText] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')
  const [twcoreFilter, setTwcoreFilter] = useState('')
  const [expandedEntry, setExpandedEntry] = useState<string | null>(null)

  const lookupMutation = useLookupCode()
  const { data: searchResults, isFetching: isSearching, isError: isSearchError } = useSearchCodes(systemUrl, debouncedSearch)
  const { data: twcoreCatalog = [], isLoading: isTwcoreLoading } = useTwcoreFullCatalog()

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText), 500)
    return () => clearTimeout(timer)
  }, [searchText])

  const filteredCatalog = useMemo(() => {
    if (!twcoreFilter.trim()) return twcoreCatalog
    const lower = twcoreFilter.toLowerCase()
    return twcoreCatalog
      .map((entry) => {
        const filteredCategories = entry.categories
          .map((cat) => ({
            ...cat,
            codes: cat.codes.filter(
              (c) =>
                c.code.toLowerCase().includes(lower) ||
                c.display.toLowerCase().includes(lower) ||
                c.displayZh.toLowerCase().includes(lower)
            ),
          }))
          .filter((cat) => cat.codes.length > 0 || cat.name.toLowerCase().includes(lower))
        if (
          filteredCategories.length > 0 ||
          entry.name.toLowerCase().includes(lower) ||
          entry.resourceType.toLowerCase().includes(lower)
        ) {
          return { ...entry, categories: filteredCategories.length > 0 ? filteredCategories : entry.categories }
        }
        return null
      })
      .filter(Boolean) as typeof twcoreCatalog
  }, [twcoreCatalog, twcoreFilter])

  const handleSystemChange = (url: string) => {
    setSystemUrl(url)
    const match = findCodeSystemByUrl(url)
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
    const sys = findCodeSystemByLabel(parsed.system)
    setSystemUrl(sys?.url || '')
    setSystemAlias(parsed.system)
    setCodeValue(parsed.code)
    setDisplayName(parsed.display)
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setBrowseMode('manual')
    setSystemUrl('')
    setSystemAlias('')
    setCodeValue('')
    setDisplayName('')
    setSearchText('')
    setDebouncedSearch('')
    setEditingItem(null)
    setPreviewSnippet('')
    setTwcoreFilter('')
    setExpandedEntry(null)
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  const handleTwcoreCodeClick = (entry: typeof twcoreCatalog[0], code: { code: string; display: string; displayZh: string }) => {
    // Derive a system alias from the entry system URL
    const knownSystem = findCodeSystemByUrl(entry.system)
    const alias = knownSystem?.label || entry.name
    const displayLabel = code.displayZh ? `${code.display} (${code.displayZh})` : code.display
    const csSnippet = `codesystem "${alias}": '${entry.system}'`
    const codeSnippet = `code "${displayLabel}": '${code.code}' from "${alias}" display '${code.display}'`
    setPreviewSnippet(`${csSnippet}\n${codeSnippet}`)
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
          {!editingItem && (
            <ToggleButtonGroup
              value={browseMode}
              exclusive
              onChange={(_, val) => { if (val) setBrowseMode(val) }}
              size="small"
              fullWidth
            >
              <ToggleButton value="manual" sx={{ textTransform: 'none', fontSize: '0.8rem' }}>
                <SearchIcon sx={{ fontSize: 16, mr: 0.5 }} />
                Manual / Search
              </ToggleButton>
              <ToggleButton value="twcore" sx={{ textTransform: 'none', fontSize: '0.8rem' }}>
                <BrowseIcon sx={{ fontSize: 16, mr: 0.5 }} />
                Browse TWCORE
              </ToggleButton>
            </ToggleButtonGroup>
          )}

          {(browseMode === 'manual' || editingItem) && (
            <>
              <TextField
                select
                size="small"
                label="Code System"
                value={systemUrl}
                onChange={(e) => handleSystemChange(e.target.value)}
              >
                {ALL_CODE_SYSTEMS.map((cs) => (
                  <MenuItem key={cs.url} value={cs.url}>
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
            </>
          )}

          {browseMode === 'twcore' && !editingItem && (
            <>
              <TextField
                size="small"
                label="Filter TWCORE entries"
                placeholder="e.g. diabetes, 血壓..."
                value={twcoreFilter}
                onChange={(e) => setTwcoreFilter(e.target.value)}
              />

              {isTwcoreLoading ? (
                <Stack alignItems="center" py={1}>
                  <CircularProgress size={20} />
                </Stack>
              ) : filteredCatalog.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
                  No matching TWCORE entries
                </Typography>
              ) : (
                <Stack sx={{ maxHeight: 300, overflow: 'auto' }}>
                  {filteredCatalog.map((entry) => (
                    <Accordion
                      key={entry.name}
                      expanded={expandedEntry === entry.name}
                      onChange={(_, isExpanded) => setExpandedEntry(isExpanded ? entry.name : null)}
                      disableGutters
                      elevation={0}
                      sx={{ '&:before': { display: 'none' }, bgcolor: 'transparent' }}
                    >
                      <AccordionSummary expandIcon={<ExpandMore sx={{ fontSize: 16 }} />} sx={{ minHeight: 32, px: 0.5, '& .MuiAccordionSummary-content': { my: 0.25 } }}>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          <Typography variant="body2" fontWeight={500} sx={{ fontSize: '0.8rem' }}>
                            {entry.name}
                          </Typography>
                          <Chip label={entry.resourceType} size="small" sx={{ height: 18, fontSize: '0.65rem' }} />
                        </Stack>
                      </AccordionSummary>
                      <AccordionDetails sx={{ px: 0.5, py: 0 }}>
                        {entry.categories.map((cat) => (
                          <Accordion
                            key={cat.name}
                            disableGutters
                            elevation={0}
                            sx={{ '&:before': { display: 'none' }, bgcolor: 'transparent' }}
                          >
                            <AccordionSummary expandIcon={<ExpandMore sx={{ fontSize: 14 }} />} sx={{ minHeight: 28, px: 0.5, '& .MuiAccordionSummary-content': { my: 0.15 } }}>
                              <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
                                {cat.name} ({cat.codes.length})
                              </Typography>
                            </AccordionSummary>
                            <AccordionDetails sx={{ px: 0, py: 0 }}>
                              <List dense disablePadding>
                                {cat.codes.map((code) => (
                                  <ListItemButton
                                    key={code.code}
                                    onClick={() => handleTwcoreCodeClick(entry, code)}
                                    sx={{ py: 0.15, px: 1 }}
                                  >
                                    <ListItemText
                                      primary={
                                        <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
                                          <Typography component="span" sx={{ fontFamily: 'monospace', fontWeight: 600, fontSize: '0.75rem' }}>
                                            {code.code}
                                          </Typography>
                                          {' '}{code.display}
                                          {code.displayZh && (
                                            <Typography component="span" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
                                              {' '}({code.displayZh})
                                            </Typography>
                                          )}
                                        </Typography>
                                      }
                                    />
                                  </ListItemButton>
                                ))}
                              </List>
                            </AccordionDetails>
                          </Accordion>
                        ))}
                      </AccordionDetails>
                    </Accordion>
                  ))}
                </Stack>
              )}
            </>
          )}

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? 'Update' : 'Insert'}
            />
          ) : (browseMode === 'manual' || editingItem) ? (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd}
                disabled={!systemUrl || !codeValue || !systemAlias}>
                Preview {editingItem ? 'Update' : 'Insert'}
              </Button>
              <Button size="small" onClick={resetForm}>Cancel</Button>
            </Stack>
          ) : (
            <Button size="small" onClick={resetForm}>Cancel</Button>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title="Delete Element"
        itemName={deleteTarget || ''}
        message={`Are you sure you want to delete "${deleteTarget}"? This will remove the corresponding lines from the CQL editor.`}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
