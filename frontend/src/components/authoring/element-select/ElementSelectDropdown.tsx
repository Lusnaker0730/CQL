import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  InputAdornment,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
} from '@mui/material'
import {
  Search as SearchIcon,
  ExpandMore as ExpandMoreIcon,
} from '@mui/icons-material'
import type { FormTemplateCategory, FormTemplate } from '../../../types/authoring'

export interface DynamicEntry {
  id: string
  name: string
  description: string
  returnType: string
  /** Source category label */
  category: string
  /** Extra metadata to identify the source */
  sourceType: 'baseElement' | 'parameter' | 'externalCql'
  sourceId: string
  /** For external CQL: library name */
  libraryName?: string
}

interface ElementSelectDropdownProps {
  templates: FormTemplateCategory[]
  dynamicEntries?: DynamicEntry[]
  twcoreMode?: boolean
  onSelect: (template: FormTemplate) => void
  onSelectDynamic?: (entry: DynamicEntry) => void
}

export default function ElementSelectDropdown({ templates, dynamicEntries, twcoreMode, onSelect, onSelectDynamic }: ElementSelectDropdownProps) {
  const { t } = useTranslation('authoring')
  const [search, setSearch] = useState('')

  const visibleCategories = templates
    .filter((cat) => !cat.suppress && cat.entries.length > 0)
    .map((cat) => ({
      ...cat,
      entries: cat.entries.filter((entry) => {
        if (entry.suppress) return false
        // Hide twcoreOnly templates when TWCORE mode is off
        if (entry.twcoreOnly && !twcoreMode) return false
        if (!search) return true
        return entry.name.toLowerCase().includes(search.toLowerCase())
      }),
    }))
    .filter((cat) => cat.entries.length > 0)

  // Group dynamic entries by category
  const dynamicCategories: Record<string, DynamicEntry[]> = {}
  if (dynamicEntries) {
    for (const entry of dynamicEntries) {
      if (search && !entry.name.toLowerCase().includes(search.toLowerCase())) continue
      if (!dynamicCategories[entry.category]) dynamicCategories[entry.category] = []
      dynamicCategories[entry.category].push(entry)
    }
  }

  const hasDynamicEntries = Object.keys(dynamicCategories).length > 0
  const isEmpty = visibleCategories.length === 0 && !hasDynamicEntries

  return (
    <Box sx={{ width: 320, maxHeight: 400, overflow: 'auto' }}>
      <Box sx={{ p: 1, position: 'sticky', top: 0, backgroundColor: 'background.paper', zIndex: 1 }}>
        <TextField
          size="small"
          fullWidth
          placeholder={t('elementSelect.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {isEmpty ? (
        <Box sx={{ p: 2, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            {t('elementSelect.noElements')}
          </Typography>
        </Box>
      ) : (
        <>
          {visibleCategories.map((category) => (
            <Accordion
              key={category.id}
              disableGutters
              elevation={0}
              defaultExpanded={visibleCategories.length === 1 || !!search}
              sx={{ '&:before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 36, py: 0 }}>
                <Typography variant="subtitle2">{category.name}</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <List dense disablePadding>
                  {category.entries.map((entry) => (
                    <ListItemButton
                      key={entry.id}
                      onClick={() => onSelect(entry)}
                      sx={{ pl: 4 }}
                    >
                      <ListItemText
                        primary={
                          entry.twcoreOnly ? (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <span>{entry.name}</span>
                              <Chip label="TW" size="small" color="info" sx={{ fontSize: '0.6rem', height: 18 }} />
                            </Box>
                          ) : entry.name
                        }
                        secondary={t(`elementDescriptions.${entry.id}`, { defaultValue: '' }) || entry.returnType?.replace(/_/g, ' ') || category.name}
                        primaryTypographyProps={{ variant: 'body2' }}
                        secondaryTypographyProps={{ variant: 'caption' }}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </AccordionDetails>
            </Accordion>
          ))}

          {/* Dynamic categories: Base Elements, Parameters, External CQL */}
          {Object.entries(dynamicCategories).map(([catName, entries]) => (
            <Accordion
              key={`dynamic-${catName}`}
              disableGutters
              elevation={0}
              defaultExpanded={!!search}
              sx={{ '&:before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 36, py: 0 }}>
                <Typography variant="subtitle2">{catName}</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <List dense disablePadding>
                  {entries.map((entry) => (
                    <ListItemButton
                      key={entry.id}
                      onClick={() => onSelectDynamic?.(entry)}
                      sx={{ pl: 4 }}
                    >
                      <ListItemText
                        primary={
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <span>{entry.name}</span>
                            {entry.libraryName && (
                              <Chip label={entry.libraryName} size="small" sx={{ fontSize: '0.65rem', height: 18 }} variant="outlined" />
                            )}
                          </Box>
                        }
                        secondary={entry.description}
                        primaryTypographyProps={{ variant: 'body2' }}
                        secondaryTypographyProps={{ variant: 'caption' }}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </AccordionDetails>
            </Accordion>
          ))}
        </>
      )}
    </Box>
  )
}

