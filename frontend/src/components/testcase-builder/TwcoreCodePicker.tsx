import { useState, useMemo } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  TextField,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Typography,
  Box,
  Button,
  Stack,
  Chip,
  InputAdornment,
  CircularProgress,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon, Search as SearchIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useTwcoreCatalog, useTwcoreFullCatalog } from '../../hooks/useTwcoreCatalog'
import type { TwcoreCatalogEntry, TwcoreCode } from '../../types/authoring'

interface TwcoreCodePickerProps {
  open: boolean
  onClose: () => void
  onSelect: (coding: { system: string; code: string; display: string }) => void
  resourceType?: string
}

export default function TwcoreCodePicker({ open, onClose, onSelect, resourceType }: TwcoreCodePickerProps) {
  const { t } = useTranslation('measures')
  const [search, setSearch] = useState('')

  const filteredQuery = useTwcoreCatalog(resourceType)
  const fullQuery = useTwcoreFullCatalog(!resourceType)
  const { data: catalog, isLoading } = resourceType ? filteredQuery : fullQuery

  const filtered = useMemo(() => {
    if (!catalog) return []
    if (!search.trim()) return catalog
    const q = search.toLowerCase()
    return catalog
      .map((entry: TwcoreCatalogEntry) => {
        const filteredCategories = entry.categories
          .map((cat) => ({
            ...cat,
            codes: cat.codes.filter(
              (c: TwcoreCode) =>
                c.code.toLowerCase().includes(q) ||
                c.display.toLowerCase().includes(q) ||
                c.displayZh.toLowerCase().includes(q)
            ),
          }))
          .filter((cat) => cat.codes.length > 0)
        if (filteredCategories.length === 0) return null
        return { ...entry, categories: filteredCategories }
      })
      .filter(Boolean) as TwcoreCatalogEntry[]
  }, [catalog, search])

  const handleSelect = (system: string, code: TwcoreCode) => {
    onSelect({ system, code: code.code, display: code.display || code.displayZh })
    onClose()
    setSearch('')
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {t('testCaseBuilder.twcore.title')}
        {resourceType && (
          <Chip label={resourceType} size="small" sx={{ ml: 1 }} variant="outlined" />
        )}
      </DialogTitle>
      <DialogContent>
        <TextField
          fullWidth
          size="small"
          placeholder={t('testCaseBuilder.twcore.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
          sx={{ mb: 2, mt: 0.5 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />

        {isLoading && (
          <Box sx={{ textAlign: 'center', py: 3 }}>
            <CircularProgress size={28} />
          </Box>
        )}

        {!isLoading && filtered.length === 0 && (
          <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ py: 3 }}>
            {search ? t('testCaseBuilder.twcore.noMatchingCodes') : t('testCaseBuilder.twcore.noTerminology')}
          </Typography>
        )}

        {filtered.map((entry) => (
          <Accordion key={entry.url} disableGutters sx={{ '&:before': { display: 'none' } }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Box>
                <Typography variant="subtitle2">{entry.name}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-all' }}>
                  {entry.system}
                </Typography>
              </Box>
            </AccordionSummary>
            <AccordionDetails sx={{ pt: 0, maxHeight: 300, overflow: 'auto' }}>
              {entry.categories.map((cat) => (
                <Box key={cat.name} sx={{ mb: 1.5 }}>
                  {cat.name && (
                    <Typography variant="caption" fontWeight={600} color="primary.main" sx={{ mb: 0.5, display: 'block' }}>
                      {cat.name}
                    </Typography>
                  )}
                  <Stack spacing={0.5}>
                    {cat.codes.map((code) => (
                      <Box
                        key={code.code}
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: 1,
                          py: 0.25,
                          px: 1,
                          borderRadius: 1,
                          '&:hover': { bgcolor: 'action.hover' },
                        }}
                      >
                        <Box sx={{ minWidth: 0, flex: 1 }}>
                          <Typography variant="body2" fontWeight={500} noWrap>
                            {code.code}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {code.display}
                            {code.displayZh && code.displayZh !== code.display && ` / ${code.displayZh}`}
                          </Typography>
                        </Box>
                        <Button
                          size="small"
                          variant="outlined"
                          sx={{ flexShrink: 0, textTransform: 'none', minWidth: 48 }}
                          onClick={() => handleSelect(entry.system, code)}
                        >
                          {t('testCaseBuilder.twcore.use')}
                        </Button>
                      </Box>
                    ))}
                  </Stack>
                </Box>
              ))}
            </AccordionDetails>
          </Accordion>
        ))}
      </DialogContent>
    </Dialog>
  )
}
