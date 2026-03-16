import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
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
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { Add as AddIcon, ExpandMore, ExpandLess, Search as SearchIcon, LocalLibrary as BrowseIcon } from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import TwcoreBrowser from './TwcoreBrowser'
import type { TwcoreCatalogEntry } from '../../types/authoring'

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
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [browseMode, setBrowseMode] = useState<BrowseMode>('vsac')
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('')
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null)
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearchTerm(searchTerm), SEARCH_DEBOUNCE_CODE_MS)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const { data: searchResults = [], isLoading: isSearching } = useSearchValueSets(
    browseMode === 'vsac' && debouncedSearchTerm.length >= 2 ? debouncedSearchTerm : undefined
  )
  const expandMutation = useExpandValueSet()
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
    setDebouncedSearchTerm('')
    setEditingItem(null)
    setEditName('')
    setEditUrl('')
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  const handleTwcoreCodeClick = (entry: TwcoreCatalogEntry, code: { code: string; display: string; displayZh: string }) => {
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
          {t('common.noItemsFound', { type: t('sections.valueSets').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'ValueSet' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.03), borderRadius: 1 }}>
          {editingItem && (
            <>
              <TextField
                size="small"
                label={t('valueSets.name')}
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
              />
              <TextField
                size="small"
                label={t('valueSets.urlOid')}
                value={editUrl}
                onChange={(e) => setEditUrl(e.target.value)}
              />
              {previewSnippet ? (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel={t('common.update')}
                />
              ) : (
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={handleManualSave} disabled={!editName.trim() || !editUrl.trim()}>
                    {t('common.previewUpdate')}
                  </Button>
                  <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
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
                  {t('valueSets.searchVsac')}
                </ToggleButton>
                <ToggleButton value="twcore" sx={{ textTransform: 'none', fontSize: '0.8rem' }}>
                  <BrowseIcon sx={{ fontSize: 16, mr: 0.5 }} />
                  {t('valueSets.browseTwcore')}
                </ToggleButton>
              </ToggleButtonGroup>

              {browseMode === 'vsac' && (
                <>
                  <TextField
                    size="small"
                    label={t('valueSets.searchVsacTitle')}
                    placeholder={t('valueSets.searchVsacPlaceholder')}
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
                                {t('common.insert')}
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
                                        {t('valueSets.andMore', { count: expandMutation.data.expansion.contains.length - 10 })}
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
                <TwcoreBrowser
                  emptyMessage={t('valueSets.noTwcoreEntries')}
                  onCodeClick={handleTwcoreCodeClick}
                />
              )}

              {previewSnippet && (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel={t('common.insert')}
                />
              )}

              <Button size="small" onClick={resetForm}>
                {t('common.cancel')}
              </Button>
            </>
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
