import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Typography,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  List,
  ListItemButton,
  ListItemText,
} from '@mui/material'
import { ExpandMore } from '@mui/icons-material'
import { useFilteredTwcoreCatalog } from '../../hooks/useFilteredTwcoreCatalog'
import type { TwcoreCatalogEntry } from '../../types/authoring'

interface TwcoreBrowserProps {
  emptyMessage: string
  onCodeClick: (entry: TwcoreCatalogEntry, code: { code: string; display: string; displayZh: string }) => void
}

export default function TwcoreBrowser({ emptyMessage, onCodeClick }: TwcoreBrowserProps) {
  const { t } = useTranslation('builder')
  const [filter, setFilter] = useState('')
  const [expandedEntry, setExpandedEntry] = useState<string | null>(null)
  const { filteredCatalog, isLoading } = useFilteredTwcoreCatalog(filter)

  return (
    <>
      <TextField
        size="small"
        label={t('codes.filterTwcore')}
        placeholder={t('codes.filterTwcorePlaceholder')}
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
      />

      {isLoading ? (
        <Stack alignItems="center" py={1}>
          <CircularProgress size={20} />
        </Stack>
      ) : filteredCatalog.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
          {emptyMessage}
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
                            onClick={() => onCodeClick(entry, code)}
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
  )
}
