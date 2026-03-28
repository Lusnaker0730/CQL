import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, TextField, Chip, IconButton, Tooltip,
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  Table, TableHead, TableBody, TableRow, TableCell, CircularProgress,
  InputAdornment, Tabs, Tab, Accordion, AccordionSummary, AccordionDetails,
} from '@mui/material'
import {
  FormatListBulleted as VsIcon, Add as AddIcon, Delete as DeleteIcon,
  Close as CloseIcon, CheckCircle as AuthIcon, Search as SearchIcon,
  ExpandMore as ExpandMoreIcon, Public as TwcoreIcon,
} from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import ChooseCodeDialog from './ChooseCodeDialog'
import { useSearchValueSets } from '../../../hooks/useTerminology'
import { useTwcoreCatalog } from '../../../hooks/useTwcoreCatalog'
import type { ValueSetReference, CodeReference, ElementField } from '../../../types/authoring'

interface ValueSetFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  selectPath?: string
  field?: ElementField
  onFieldUpdate?: (updates: Partial<ElementField>) => void
  resourceType?: string
}

export default function ValueSetField({
  label,
  value,
  onChange,
  field,
  onFieldUpdate,
  resourceType,
}: ValueSetFieldProps) {
  const { t } = useTranslation('authoring')
  const [codeDialogOpen, setCodeDialogOpen] = useState(false)
  const [vsDialogOpen, setVsDialogOpen] = useState(false)

  const valueSets: ValueSetReference[] = field?.valueSets || []
  const codes: CodeReference[] = field?.codes || []
  const hasRichData = valueSets.length > 0 || codes.length > 0

  // Derive resourceType from field.type if not provided
  const effectiveResourceType = resourceType || deriveResourceType(field?.type)

  const handleAddValueSet = (vs: ValueSetReference) => {
    if (onFieldUpdate) {
      onFieldUpdate({ valueSets: [...valueSets, vs] })
    }
  }

  const handleRemoveValueSet = (index: number) => {
    if (onFieldUpdate) {
      const updated = [...valueSets]
      updated.splice(index, 1)
      onFieldUpdate({ valueSets: updated })
    }
  }

  const handleAddCode = (code: CodeReference) => {
    if (onFieldUpdate) {
      onFieldUpdate({ codes: [...codes, code] })
    }
  }

  const handleRemoveCode = (index: number) => {
    if (onFieldUpdate) {
      const updated = [...codes]
      updated.splice(index, 1)
      onFieldUpdate({ codes: updated })
    }
  }

  if (!onFieldUpdate) {
    return (
      <Box>
        <TextField
          label={label}
          size="small"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          fullWidth
          placeholder={t('valueSetField.oidPlaceholder')}
        />
      </Box>
    )
  }

  return (
    <Box>
      {/* VSAC Status + Action Buttons */}
      <Stack direction="row" spacing={1} alignItems="center" mb={1.5}>
        <Chip
          icon={<AuthIcon sx={{ fontSize: 16 }} />}
          label={t('valueSetField.vsacAuthenticated')}
          size="small"
          variant="outlined"
          sx={{ color: 'text.secondary', borderColor: 'divider' }}
        />
        <GradientButton size="small" startIcon={<VsIcon />} onClick={() => setVsDialogOpen(true)}>
          {t('valueSetField.addValueSet')}
        </GradientButton>
        <GradientButton size="small" startIcon={<AddIcon />} onClick={() => setCodeDialogOpen(true)}>
          {t('valueSetField.addCode')}
        </GradientButton>
      </Stack>

      {/* Listed Value Sets */}
      {valueSets.length > 0 && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 0.5, display: 'block' }}>
            {t('valueSetField.valueSets')}
          </Typography>
          <Stack spacing={0.5}>
            {valueSets.map((vs, i) => (
              <Stack key={`vs-${i}`} direction="row" alignItems="center" spacing={1}
                sx={{ py: 0.5, px: 1, backgroundColor: 'action.hover', borderRadius: 1 }}
              >
                <VsIcon fontSize="small" color="primary" />
                <Typography variant="body2" sx={{ flex: 1 }}>
                  {vs.name}{' '}
                  <Typography component="span" variant="caption" color="text.secondary">({vs.oid})</Typography>
                </Typography>
                <Tooltip title={t('valueSetField.remove')}>
                  <IconButton size="small" onClick={() => handleRemoveValueSet(i)} aria-label={t('valueSetField.removeValueSet')}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            ))}
          </Stack>
        </Box>
      )}

      {/* Listed Codes */}
      {codes.length > 0 && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 0.5, display: 'block' }}>
            {t('valueSetField.codes')}
          </Typography>
          <Stack spacing={0.5}>
            {codes.map((c, i) => (
              <Stack key={`code-${i}`} direction="row" alignItems="center" spacing={1}
                sx={{ py: 0.5, px: 1, backgroundColor: 'action.hover', borderRadius: 1 }}
              >
                <Chip label={c.codeSystem.name} size="small" variant="outlined" sx={{ fontSize: '0.7rem' }} />
                <Typography variant="body2" fontWeight={600}>{c.code}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>{c.display}</Typography>
                <Tooltip title={t('valueSetField.remove')}>
                  <IconButton size="small" onClick={() => handleRemoveCode(i)} aria-label={t('valueSetField.removeCode')}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            ))}
          </Stack>
        </Box>
      )}

      {/* Empty state with legacy OID input */}
      {!hasRichData && (
        <TextField
          label={label}
          size="small"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          fullWidth
          placeholder={t('valueSetField.oidDirectPlaceholder')}
          sx={{ mt: 0.5 }}
        />
      )}

      <ChooseCodeDialog open={codeDialogOpen} onClose={() => setCodeDialogOpen(false)} onSelect={handleAddCode} resourceType={effectiveResourceType} />
      <AddValueSetDialog
        open={vsDialogOpen}
        onClose={() => setVsDialogOpen(false)}
        onAdd={handleAddValueSet}
        onAddCode={handleAddCode}
        resourceType={effectiveResourceType}
      />
    </Box>
  )
}

function deriveResourceType(fieldType?: string): string | undefined {
  if (!fieldType) return undefined
  const base = fieldType.replace('_vsac', '')
  const typeMap: Record<string, string> = {
    observation: 'Observation',
    condition: 'Condition',
    medication: 'MedicationRequest',
    medicationStatement: 'MedicationStatement',
    medicationRequest: 'MedicationRequest',
    procedure: 'Procedure',
    encounter: 'Encounter',
    allergyIntolerance: 'AllergyIntolerance',
    immunization: 'Immunization',
    device: 'Device',
    serviceRequest: 'ServiceRequest',
  }
  return typeMap[base] || base.charAt(0).toUpperCase() + base.slice(1)
}

// ----- Add Value Set Dialog with VSAC Search + TWCORE Browse -----

function AddValueSetDialog({
  open,
  onClose,
  onAdd,
  onAddCode,
  resourceType,
}: {
  open: boolean
  onClose: () => void
  onAdd: (vs: ValueSetReference) => void
  onAddCode: (code: CodeReference) => void
  resourceType?: string
}) {
  const { t } = useTranslation('authoring')
  const [tab, setTab] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [oid, setOid] = useState('')
  const [name, setName] = useState('')
  const [twcoreFilter, setTwcoreFilter] = useState('')

  const { data: searchResults, isLoading: isSearching } = useSearchValueSets(
    tab === 0 ? searchQuery : undefined
  )

  const { data: twcoreCatalog, isLoading: isTwcoreLoading } = useTwcoreCatalog(
    tab === 2 ? resourceType : undefined
  )

  const filteredTwcore = useMemo(() => {
    if (!twcoreCatalog || twcoreCatalog.length === 0) return []
    const q = twcoreFilter.toLowerCase()
    if (!q) return twcoreCatalog
    return twcoreCatalog.map(entry => ({
      ...entry,
      categories: entry.categories.map(cat => ({
        ...cat,
        codes: cat.codes.filter(c =>
          c.code.toLowerCase().includes(q) ||
          c.display.toLowerCase().includes(q) ||
          c.displayZh.includes(twcoreFilter)
        ),
      })).filter(cat => cat.codes.length > 0),
    })).filter(entry => entry.categories.length > 0)
  }, [twcoreCatalog, twcoreFilter])

  const handleSelectFromSearch = (vs: { url: string; name: string; title: string }) => {
    onAdd({ oid: vs.url, name: vs.title || vs.name })
    handleReset()
    onClose()
  }

  const handleSelectTwcoreCode = (system: string, systemName: string, code: string, display: string, displayZh: string) => {
    onAddCode({
      code,
      codeSystem: { name: systemName, id: system },
      display: `${display} (${displayZh})`,
    })
    handleReset()
    onClose()
  }

  const handleAddCategory = (system: string, systemName: string, category: { name: string; codes: Array<{ code: string; display: string; displayZh: string }> }) => {
    for (const c of category.codes) {
      onAddCode({
        code: c.code,
        codeSystem: { name: systemName, id: system },
        display: `${c.display} (${c.displayZh})`,
      })
    }
    handleReset()
    onClose()
  }

  const handleManualAdd = () => {
    if (oid.trim()) {
      onAdd({ oid: oid.trim(), name: name.trim() || oid.trim() })
      handleReset()
      onClose()
    }
  }

  const handleReset = () => {
    setSearchQuery('')
    setOid('')
    setName('')
    setTwcoreFilter('')
    setTab(0)
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {t('valueSetField.addVsTitle')}
        <IconButton onClick={handleClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 3 }}>
        <Tab label={t('valueSetField.searchVsac')} />
        <Tab label={t('valueSetField.enterOid')} />
        <Tab
          label={t('valueSetField.browseTwcore')}
          icon={<TwcoreIcon sx={{ fontSize: 18 }} />}
          iconPosition="start"
          disabled={!resourceType}
        />
      </Tabs>

      <DialogContent sx={{ minHeight: 300 }}>
        {tab === 0 ? (
          <Box>
            <TextField
              fullWidth
              size="small"
              placeholder={t('valueSetField.searchVsPlaceholder')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              sx={{ mb: 2 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
                endAdornment: isSearching ? (
                  <InputAdornment position="end">
                    <CircularProgress size={18} />
                  </InputAdornment>
                ) : null,
              }}
            />

            {searchResults && searchResults.length > 0 ? (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>{t('valueSetField.colName')}</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>{t('valueSetField.colOid')}</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {searchResults.map((vs) => (
                    <TableRow
                      key={vs.url}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => handleSelectFromSearch(vs)}
                    >
                      <TableCell>
                        <Typography variant="body2">{vs.title || vs.name}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">{vs.url}</Typography>
                      </TableCell>
                      <TableCell>
                        <Button size="small" variant="outlined">{t('valueSetField.select')}</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : searchQuery.length >= 2 && !isSearching ? (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
                {t('valueSetField.noVsFound', { query: searchQuery })}
              </Typography>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
                {t('valueSetField.vsSearchHint')}
              </Typography>
            )}
          </Box>
        ) : tab === 1 ? (
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label={t('valueSetField.oidLabel')}
              size="small"
              value={oid}
              onChange={(e) => setOid(e.target.value)}
              fullWidth
              placeholder={t('valueSetField.oidFieldPlaceholder')}
            />
            <TextField
              label={t('valueSetField.displayNameLabel')}
              size="small"
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              placeholder={t('valueSetField.displayNamePlaceholder')}
              helperText={t('valueSetField.displayNameHelper')}
            />
          </Stack>
        ) : (
          /* Browse TWCORE tab */
          <Box>
            <TextField
              fullWidth
              size="small"
              placeholder={t('valueSetField.filterTwcore')}
              value={twcoreFilter}
              onChange={(e) => setTwcoreFilter(e.target.value)}
              sx={{ mb: 2 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
            />

            {isTwcoreLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress size={24} />
              </Box>
            ) : filteredTwcore.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
                {twcoreFilter
                  ? t('valueSetField.noCodesMatch', { filter: twcoreFilter })
                  : t('valueSetField.noTwcoreCodes', { type: resourceType })}
              </Typography>
            ) : (
              filteredTwcore.map((entry) => (
                <Box key={entry.resourceType} sx={{ mb: 1 }}>
                  <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ mb: 1, display: 'block' }}>
                    {entry.name}
                  </Typography>
                  {entry.categories.map((cat) => (
                    <Accordion key={cat.name} disableGutters variant="outlined" sx={{ '&:before': { display: 'none' }, mb: 0.5 }}>
                      <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 40, '& .MuiAccordionSummary-content': { my: 0.5 } }}>
                        <Stack direction="row" alignItems="center" spacing={1} sx={{ width: '100%' }}>
                          <Typography variant="body2" fontWeight={600}>{cat.name}</Typography>
                          <Chip label={t('valueSetField.codesCount', { count: cat.codes.length })} size="small" sx={{ fontSize: '0.7rem' }} />
                          <Box sx={{ flex: 1 }} />
                          <Button
                            size="small"
                            variant="text"
                            onClick={(e) => { e.stopPropagation(); handleAddCategory(entry.system, entry.name, cat) }}
                            sx={{ fontSize: '0.7rem', minWidth: 'auto' }}
                          >
                            {t('valueSetField.addAll')}
                          </Button>
                        </Stack>
                      </AccordionSummary>
                      <AccordionDetails sx={{ p: 0 }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell sx={{ fontWeight: 600, fontSize: '0.7rem', py: 0.5 }}>{t('valueSetField.colCode')}</TableCell>
                              <TableCell sx={{ fontWeight: 600, fontSize: '0.7rem', py: 0.5 }}>{t('valueSetField.colDisplay')}</TableCell>
                              <TableCell sx={{ fontWeight: 600, fontSize: '0.7rem', py: 0.5 }}>{t('valueSetField.colChinese')}</TableCell>
                              <TableCell sx={{ py: 0.5 }} />
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {cat.codes.map((c) => (
                              <TableRow
                                key={c.code}
                                hover
                                sx={{ cursor: 'pointer' }}
                                onClick={() => handleSelectTwcoreCode(entry.system, entry.name, c.code, c.display, c.displayZh)}
                              >
                                <TableCell sx={{ py: 0.5 }}>
                                  <Typography variant="body2" fontWeight={600} sx={{ fontSize: '0.8rem' }}>{c.code}</Typography>
                                </TableCell>
                                <TableCell sx={{ py: 0.5 }}>
                                  <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>{c.display}</Typography>
                                </TableCell>
                                <TableCell sx={{ py: 0.5 }}>
                                  <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.8rem' }}>{c.displayZh}</Typography>
                                </TableCell>
                                <TableCell sx={{ py: 0.5 }}>
                                  <Button size="small" variant="outlined" sx={{ fontSize: '0.7rem', minWidth: 'auto', py: 0 }}>{t('valueSetField.select')}</Button>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </AccordionDetails>
                    </Accordion>
                  ))}
                </Box>
              ))
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose}>{t('actions.cancel', { ns: 'common' })}</Button>
        {tab === 1 && (
          <Button variant="contained" onClick={handleManualAdd} disabled={!oid.trim()}>
            {t('valueSetField.add')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
