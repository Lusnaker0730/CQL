import { useState, useMemo } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  CircularProgress,
  Collapse,
  Table,
  TableBody,
  TableRow,
  TableCell,
  IconButton,
  ToggleButtonGroup,
  ToggleButton,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  List,
  ListItemButton,
  ListItemText,
} from '@mui/material'
import { Add as AddIcon, ExpandMore, ExpandLess, Search as SearchIcon, LocalLibrary as BrowseIcon } from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'
import { useTwcoreFullCatalog } from '../../hooks/useTwcoreCatalog'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from './ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'

interface ValueSetSectionProps {
  valueSets: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

/**
 * Parse a valueset string like: "Diabetes": 'http://cts.nlm.nih.gov/...'
 */
function parseValueSet(raw: string): { name: string; url: string } | null {
  const m = raw.match(/^"([^"]+)":\s*'([^']+)'/)
  if (m) return { name: m[1], url: m[2] }
  return null
}

type BrowseMode = 'vsac' | 'twcore'

export default function ValueSetSection({ valueSets, onInsert, onDelete, onGoTo, onEdit }: ValueSetSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [browseMode, setBrowseMode] = useState<BrowseMode>('vsac')
  const [searchTerm, setSearchTerm] = useState('')
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null)
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')
  const [twcoreFilter, setTwcoreFilter] = useState('')
  const [expandedEntry, setExpandedEntry] = useState<string | null>(null)

  const { data: searchResults = [], isLoading: isSearching } = useSearchValueSets(
    browseMode === 'vsac' && searchTerm.length >= 2 ? searchTerm : undefined
  )
  const expandMutation = useExpandValueSet()
  const { data: twcoreCatalog = [], isLoading: isTwcoreLoading } = useTwcoreFullCatalog()

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

  const handleExpand = (url: string) => {
    if (expandedUrl === url) {
      setExpandedUrl(null)
      return
    }
    setExpandedUrl(url)
    expandMutation.mutate({ url })
  }

  const handleInsertValueSet = (name: string, url: string) => {
    const snippet = `valueset "${name}": '${url}'`
    setPreviewSnippet(snippet)
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
      setEditingItem(null)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (raw: string) => {
    const parsed = parseValueSet(raw)
    if (!parsed) return
    setEditingItem(parsed.name)
    setEditName(parsed.name)
    setEditUrl(parsed.url)
    setShowForm(true)
  }

  const handleManualSave = () => {
    if (!editName.trim() || !editUrl.trim()) return
    const snippet = `valueset "${editName}": '${editUrl}'`
    setPreviewSnippet(snippet)
  }

  const resetForm = () => {
    setShowForm(false)
    setBrowseMode('vsac')
    setSearchTerm('')
    setEditingItem(null)
    setEditName('')
    setEditUrl('')
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
    const displayLabel = code.displayZh ? `${code.display} (${code.displayZh})` : code.display
    const name = `${displayLabel} [${code.code}]`
    handleInsertValueSet(name, entry.system)
  }

  return (
    <Stack spacing={0.5}>
      {valueSets.length > 0 ? (
        valueSets.map((vs, idx) => {
          const parsed = parseValueSet(vs)
          const name = parsed?.name || vs
          return (
            <ElementListItem
              key={idx}
              label={name}
              secondaryLabel={parsed?.url}
              onGoTo={() => onGoTo?.(name)}
              onEdit={() => handleStartEdit(vs)}
              onDelete={() => setDeleteTarget(name)}
            />
          )
        })
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No value sets found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add ValueSet
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          {editingItem && (
            <>
              <TextField
                size="small"
                label="ValueSet Name"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
              />
              <TextField
                size="small"
                label="URL / OID"
                value={editUrl}
                onChange={(e) => setEditUrl(e.target.value)}
              />
              {previewSnippet ? (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel="Update"
                />
              ) : (
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={handleManualSave} disabled={!editName.trim() || !editUrl.trim()}>
                    Preview Update
                  </Button>
                  <Button size="small" onClick={resetForm}>Cancel</Button>
                </Stack>
              )}
            </>
          )}

          {!editingItem && (
            <>
              <ToggleButtonGroup
                value={browseMode}
                exclusive
                onChange={(_, val) => { if (val) setBrowseMode(val) }}
                size="small"
                fullWidth
              >
                <ToggleButton value="vsac" sx={{ textTransform: 'none', fontSize: '0.8rem' }}>
                  <SearchIcon sx={{ fontSize: 16, mr: 0.5 }} />
                  Search VSAC
                </ToggleButton>
                <ToggleButton value="twcore" sx={{ textTransform: 'none', fontSize: '0.8rem' }}>
                  <BrowseIcon sx={{ fontSize: 16, mr: 0.5 }} />
                  Browse TWCORE
                </ToggleButton>
              </ToggleButtonGroup>

              {browseMode === 'vsac' && (
                <>
                  <TextField
                    size="small"
                    label="Search VSAC ValueSets"
                    placeholder="e.g. Diabetes, HbA1c..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    InputProps={{
                      endAdornment: isSearching ? <CircularProgress size={16} /> : null,
                    }}
                  />

                  {searchResults.length > 0 && (
                    <Stack spacing={0.5} sx={{ maxHeight: 250, overflow: 'auto' }}>
                      {searchResults.slice(0, 20).map((vs) => (
                        <Stack key={vs.url} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 0.75 }}>
                          <Stack direction="row" justifyContent="space-between" alignItems="center">
                            <Typography variant="body2" fontWeight={500} sx={{ fontSize: '0.8rem', flex: 1 }}>
                              {vs.title || vs.name}
                            </Typography>
                            <Stack direction="row" spacing={0.5}>
                              <IconButton size="small" onClick={() => handleExpand(vs.url)} aria-label="Toggle value set codes">
                                {expandedUrl === vs.url ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                              </IconButton>
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => handleInsertValueSet(vs.title || vs.name, vs.url)}
                                sx={{ minWidth: 0, px: 1, fontSize: '0.7rem' }}
                              >
                                Insert
                              </Button>
                            </Stack>
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            URL: {vs.url}
                          </Typography>

                          <Collapse in={expandedUrl === vs.url}>
                            {expandMutation.isPending && expandedUrl === vs.url ? (
                              <CircularProgress size={16} sx={{ m: 1 }} />
                            ) : expandMutation.data?.expansion?.contains ? (
                              <Table size="small" sx={{ mt: 0.5 }}>
                                <TableBody>
                                  {expandMutation.data.expansion.contains.slice(0, 10).map((code, i) => (
                                    <TableRow key={i}>
                                      <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>{code.code}</TableCell>
                                      <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>{code.display}</TableCell>
                                    </TableRow>
                                  ))}
                                  {expandMutation.data.expansion.contains.length > 10 && (
                                    <TableRow>
                                      <TableCell colSpan={2} sx={{ py: 0.25, fontSize: '0.75rem', fontStyle: 'italic' }}>
                                        ...and {expandMutation.data.expansion.contains.length - 10} more
                                      </TableCell>
                                    </TableRow>
                                  )}
                                </TableBody>
                              </Table>
                            ) : null}
                          </Collapse>
                        </Stack>
                      ))}
                    </Stack>
                  )}
                </>
              )}

              {browseMode === 'twcore' && (
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

              {previewSnippet && (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel="Insert"
                />
              )}

              <Button size="small" onClick={resetForm}>
                Cancel
              </Button>
            </>
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
