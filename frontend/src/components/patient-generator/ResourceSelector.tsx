import { useState, useMemo, useCallback } from 'react'
import {
  Box,
  Typography,
  TextField,
  Checkbox,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  FormControlLabel,
  Button,
  Stack,
  InputAdornment,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the Proxy-based mock
// pattern that previously worked).
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import type { CodingItem } from '../../config/twcore/types'

interface CategoryData<T extends CodingItem> {
  name: string
  items: T[]
}

interface ResourceSelectorProps<T extends CodingItem> {
  title: string
  categories: Record<string, CategoryData<T>>
  selectedCodes: string[]
  onSelectionChange: (codes: string[]) => void
}

export default function ResourceSelector<T extends CodingItem>({
  title,
  categories,
  selectedCodes,
  onSelectionChange,
}: ResourceSelectorProps<T>) {
  const { t } = useTranslation('patientGenerator')
  const [search, setSearch] = useState('')

  const selectedSet = useMemo(() => new Set(selectedCodes), [selectedCodes])

  const filteredCategories = useMemo(() => {
    if (!search.trim()) return categories
    const query = search.toLowerCase()
    const result: Record<string, CategoryData<T>> = {}
    for (const [key, cat] of Object.entries(categories)) {
      const filtered = cat.items.filter(
        (item) =>
          item.display.toLowerCase().includes(query) ||
          item.code.toLowerCase().includes(query),
      )
      if (filtered.length > 0) {
        result[key] = { name: cat.name, items: filtered }
      }
    }
    return result
  }, [categories, search])

  const handleToggle = useCallback(
    (code: string) => {
      const next = selectedSet.has(code)
        ? selectedCodes.filter((c) => c !== code)
        : [...selectedCodes, code]
      onSelectionChange(next)
    },
    [selectedCodes, selectedSet, onSelectionChange],
  )

  // selectAll should respect the search filter: when the user has typed
  // "diabetes" they expect "Select All" to grab the diabetes-related items
  // currently shown, not silently include hidden/unrelated categories.
  const visibleCodes = useMemo(
    () => Object.values(filteredCategories).flatMap((cat) => cat.items.map((i) => i.code)),
    [filteredCategories],
  )

  // selectAll merges (so you can stack multiple search-filtered sets without
  // losing previously selected items in other categories).
  const handleSelectAllVisible = useCallback(() => {
    const merged = Array.from(new Set([...selectedCodes, ...visibleCodes]))
    onSelectionChange(merged)
  }, [selectedCodes, visibleCodes, onSelectionChange])

  // deselectAll likewise scopes to what's visible — clears just the filtered
  // items rather than wiping unrelated categories the user had picked.
  const handleDeselectAllVisible = useCallback(() => {
    const visibleSet = new Set(visibleCodes)
    onSelectionChange(selectedCodes.filter((c) => !visibleSet.has(c)))
  }, [selectedCodes, visibleCodes, onSelectionChange])

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}
      >
        <Typography variant="subtitle1" fontWeight={600}>
          {title}
        </Typography>
        <Chip
          label={t('custom.selectedCount', { count: selectedCodes.length })}
          size="small"
          color="primary"
          variant="outlined"
        />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
        <TextField
          size="small"
          placeholder={t('custom.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ flex: 1 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
              </InputAdornment>
            ),
          }}
        />
        <Button size="small" onClick={handleSelectAllVisible}>
          {t('custom.selectAll')}
        </Button>
        <Button size="small" onClick={handleDeselectAllVisible}>
          {t('custom.deselectAll')}
        </Button>
      </Stack>

      {Object.entries(filteredCategories).map(([key, cat]) => (
        <Accordion key={key} disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" fontWeight={500}>
                {t(`categories.${key}`, cat.name)}
              </Typography>
              <Chip
                label={`${cat.items.filter((i) => selectedSet.has(i.code)).length}/${cat.items.length}`}
                size="small"
                variant="outlined"
                sx={{ height: 20, '& .MuiChip-label': { px: 0.75, fontSize: '0.7rem' } }}
              />
            </Stack>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            {cat.items.map((item) => (
              <FormControlLabel
                key={item.code}
                sx={{ display: 'flex', mr: 0 }}
                control={
                  <Checkbox
                    size="small"
                    checked={selectedSet.has(item.code)}
                    onChange={() => handleToggle(item.code)}
                  />
                }
                label={
                  <Typography variant="body2">
                    {item.display}
                    <Typography
                      component="span"
                      variant="caption"
                      sx={{ ml: 0.5, color: 'text.secondary' }}
                    >
                      ({item.code})
                    </Typography>
                  </Typography>
                }
              />
            ))}
          </AccordionDetails>
        </Accordion>
      ))}
    </Box>
  )
}
