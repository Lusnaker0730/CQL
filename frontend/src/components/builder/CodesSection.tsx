import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
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
import { alpha } from '@mui/material/styles'
import {
  Add as AddIcon,
  Search as SearchIcon,
  LocalLibrary as BrowseIcon,
  ExpandMore as ExpandMoreIcon,
  MedicalServices as FhirIcon,
} from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import TwcoreBrowser from './TwcoreBrowser'
import { useLookupCode, useSearchCodes } from '../../hooks/useTerminology'
import { ALL_CODE_SYSTEMS, CODE_SYSTEM_GROUPS, findCodeSystemByUrl, findCodeSystemByLabel } from '../../constants/codeSystems'
import type { PredefinedCodeSystem } from '../../constants/codeSystems'
import type { TwcoreCatalogEntry } from '../../types/authoring'
import { escapeCqlString, escapeCqlIdentifier } from '../../utils/cqlString'

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

type BrowseMode = 'manual' | 'fhir-codes' | 'twcore'

export default function CodesSection({ codes, onInsert, onDelete, onGoTo, onEdit }: CodesSectionProps) {
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [browseMode, setBrowseMode] = useState<BrowseMode>('manual')
  const [systemUrl, setSystemUrl] = useState('')
  const [systemAlias, setSystemAlias] = useState('')
  const [codeValue, setCodeValue] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [searchText, setSearchText] = useState('')
  const debouncedSearch = useDebouncedValue(searchText, SEARCH_DEBOUNCE_CODE_MS)
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')
  const lookupMutation = useLookupCode()
  const { data: searchResults, isFetching: isSearching, isError: isSearchError } = useSearchCodes(systemUrl, debouncedSearch)

  const handleSystemChange = (url: string) => {
    setSystemUrl(url)
    const match = findCodeSystemByUrl(url)
    setSystemAlias(match?.label || '')
    setSearchText('')
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
  }

  const handleAdd = () => {
    if (!systemUrl || !codeValue || !systemAlias) return
    const aliasIdent = escapeCqlIdentifier(systemAlias)
    const codeIdent = escapeCqlIdentifier(displayName || codeValue)
    const csSnippet = `codesystem "${aliasIdent}": '${escapeCqlString(systemUrl)}'`
    const codeSnippet = `code "${codeIdent}": '${escapeCqlString(codeValue)}' from "${aliasIdent}"${displayName ? ` display '${escapeCqlString(displayName)}'` : ''}`
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
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  const handleTwcoreCodeClick = (entry: TwcoreCatalogEntry, code: { code: string; display: string; displayZh: string }) => {
    // Derive a system alias from the entry system URL
    const knownSystem = findCodeSystemByUrl(entry.system)
    const alias = knownSystem?.label || entry.name
    const displayLabel = code.displayZh ? `${code.display} (${code.displayZh})` : code.display
    const aliasIdent = escapeCqlIdentifier(alias)
    const codeIdent = escapeCqlIdentifier(displayLabel)
    const csSnippet = `codesystem "${aliasIdent}": '${escapeCqlString(entry.system)}'`
    const codeSnippet = `code "${codeIdent}": '${escapeCqlString(code.code)}' from "${aliasIdent}" display '${escapeCqlString(code.display)}'`
    setPreviewSnippet(`${csSnippet}\n${codeSnippet}`)
  }

  const handleFhirCodeClick = (sys: PredefinedCodeSystem, code: { code: string; display: string; displayZh: string }) => {
    const aliasIdent = escapeCqlIdentifier(sys.alias)
    const displayLabel = code.displayZh ? `${code.display} (${code.displayZh})` : code.display
    const codeIdent = escapeCqlIdentifier(displayLabel)
    const csSnippet = `codesystem "${aliasIdent}": '${escapeCqlString(sys.url)}'`
    const codeSnippet = `code "${codeIdent}": '${escapeCqlString(code.code)}' from "${aliasIdent}" display '${escapeCqlString(code.display)}'`
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
          {t('common.noItemsFound', { type: t('sections.codes').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Code' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.03), borderRadius: 1 }}>
          {!editingItem && (
            <ToggleButtonGroup
              value={browseMode}
              exclusive
              onChange={(_, val) => { if (val) { setBrowseMode(val); setPreviewSnippet('') } }}
              size="small"
              fullWidth
            >
              <ToggleButton value="manual" sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
                <SearchIcon sx={{ fontSize: 16, mr: 0.5 }} />
                {t('codes.manualSearch')}
              </ToggleButton>
              <ToggleButton value="fhir-codes" sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
                <FhirIcon sx={{ fontSize: 16, mr: 0.5 }} />
                {t('codes.fhirCodes')}
              </ToggleButton>
              <ToggleButton value="twcore" sx={{ textTransform: 'none', fontSize: '0.75rem' }}>
                <BrowseIcon sx={{ fontSize: 16, mr: 0.5 }} />
                {t('codes.browseTwcore')}
              </ToggleButton>
            </ToggleButtonGroup>
          )}

          {(browseMode === 'manual' || editingItem) && (
            <>
              <TextField
                select
                size="small"
                label={t('codes.codeSystem')}
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
                label={t('codes.systemAlias')}
                value={systemAlias}
                onChange={(e) => setSystemAlias(e.target.value)}
              />

              <Stack direction="row" spacing={1}>
                <TextField
                  size="small"
                  label={t('codes.code')}
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
                  {lookupMutation.isPending ? <CircularProgress size={16} /> : t('codes.lookup')}
                </Button>
              </Stack>

              <TextField
                size="small"
                label={t('codes.searchByText')}
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                placeholder={t('codes.searchPlaceholder')}
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
                  {t('codes.searchFailed')}
                </Alert>
              )}
              {searchResults && searchResults.length === 0 && debouncedSearch.length >= 2 && !isSearching && !isSearchError && (
                <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
                  {t('codes.noResults')}
                </Typography>
              )}

              <TextField
                size="small"
                label={t('codes.displayName')}
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />

              {lookupMutation.isError && (
                <Alert severity="warning" sx={{ py: 0 }}>
                  {t('codes.lookupFailed')}
                </Alert>
              )}
            </>
          )}

          {browseMode === 'fhir-codes' && !editingItem && (
            <Paper variant="outlined" sx={{ maxHeight: 360, overflow: 'auto' }}>
              {CODE_SYSTEM_GROUPS.map((group) => (
                <Accordion key={group.label} disableGutters elevation={0} sx={{ '&:before': { display: 'none' } }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 36, '& .MuiAccordionSummary-content': { my: 0.5 } }}>
                    <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.85rem' }}>
                      {group.label}
                      <Typography component="span" sx={{ ml: 0.5, color: 'text.secondary', fontSize: '0.8rem' }}>
                        ({group.labelZh})
                      </Typography>
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails sx={{ p: 0 }}>
                    {group.systems.map((sys) => (
                      <Accordion key={sys.url} disableGutters elevation={0} sx={{ ml: 1, '&:before': { display: 'none' } }}>
                        <AccordionSummary expandIcon={<ExpandMoreIcon sx={{ fontSize: 16 }} />} sx={{ minHeight: 32, '& .MuiAccordionSummary-content': { my: 0.25 } }}>
                          <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                            {sys.label}
                            <Typography component="span" sx={{ ml: 0.5, color: 'text.secondary', fontSize: '0.75rem' }}>
                              ({sys.labelZh})
                            </Typography>
                          </Typography>
                        </AccordionSummary>
                        <AccordionDetails sx={{ pt: 0, pb: 0.5, pl: 1 }}>
                          <Stack direction="row" flexWrap="wrap" gap={0.5}>
                            {sys.codes.map((c) => (
                              <Chip
                                key={c.code}
                                label={`${c.code} — ${c.display} (${c.displayZh})`}
                                size="small"
                                variant="outlined"
                                onClick={() => handleFhirCodeClick(sys, c)}
                                sx={{ fontSize: '0.75rem', cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
                              />
                            ))}
                          </Stack>
                        </AccordionDetails>
                      </Accordion>
                    ))}
                  </AccordionDetails>
                </Accordion>
              ))}
            </Paper>
          )}

          {browseMode === 'twcore' && !editingItem && (
            <TwcoreBrowser
              emptyMessage={t('codes.noTwcoreEntries')}
              onCodeClick={handleTwcoreCodeClick}
            />
          )}

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? t('common.update') : t('common.insert')}
            />
          ) : (browseMode === 'manual' || editingItem) ? (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd}
                disabled={!systemUrl || !codeValue || !systemAlias}>
                {editingItem ? t('common.previewUpdate') : t('common.previewInsert')}
              </Button>
              <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
            </Stack>
          ) : (
            <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title={t('common.deleteElement')}
        itemName={deleteTarget || ''}
        message={t('common.deleteConfirm', { name: deleteTarget })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
