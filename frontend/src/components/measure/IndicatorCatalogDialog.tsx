import { useState } from 'react'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  MenuItem,
  Stack,
  Chip,
  Typography,
  CircularProgress,
  Box,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { indicatorApi } from '../../api/indicatorApi'
import type { IndicatorCatalogEntry } from '../../types'

interface IndicatorCatalogDialogProps {
  open: boolean
  onClose: () => void
  onSelect: (entry: IndicatorCatalogEntry) => void
  sourceFilter?: string
}

const SOURCES = ['MOH', 'NHIA_P4P', 'DRG', 'CMS']

export default function IndicatorCatalogDialog({
  open,
  onClose,
  onSelect,
  sourceFilter,
}: IndicatorCatalogDialogProps) {
  const { t } = useTranslation('measures')
  const [search, setSearch] = useState('')
  const [source, setSource] = useState(sourceFilter || '')

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_GENERAL_MS)

  const { data: indicators = [], isLoading } = useQuery({
    queryKey: ['indicators', source, debouncedSearch],
    queryFn: () =>
      indicatorApi.search({
        source: source || undefined,
        search: debouncedSearch || undefined,
      }),
    enabled: open,
  })

  const handleSelect = (entry: IndicatorCatalogEntry) => {
    onSelect(entry)
    onClose()
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('indicators.catalogTitle')}</DialogTitle>
      <DialogContent>
        <Stack direction="row" spacing={2} sx={{ mb: 2, mt: 1 }}>
          <TextField
            size="small"
            label={t('indicators.search')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            sx={{ flex: 1 }}
          />
          <TextField
            size="small"
            select
            label={t('indicators.source')}
            value={source}
            onChange={(e) => setSource(e.target.value)}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">{t('indicators.allSources')}</MenuItem>
            {SOURCES.map((s) => (
              <MenuItem key={s} value={s}>
                {s}
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={32} />
          </Box>
        ) : indicators.length === 0 ? (
          <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            {t('indicators.noResults')}
          </Typography>
        ) : (
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>{t('indicators.code')}</TableCell>
                  <TableCell>{t('indicators.name')}</TableCell>
                  <TableCell>{t('indicators.source')}</TableCell>
                  <TableCell>{t('indicators.category')}</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {indicators.map((ind) => (
                  <TableRow
                    key={`${ind.code}-${ind.source}`}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => handleSelect(ind)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {ind.code}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{ind.name}</Typography>
                      {ind.nameEn && (
                        <Typography variant="caption" color="text.secondary">
                          {ind.nameEn}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip label={ind.source} size="small" />
                    </TableCell>
                    <TableCell>{ind.category}</TableCell>
                    <TableCell>
                      <Button size="small" onClick={() => handleSelect(ind)}>
                        {t('indicators.select')}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('indicators.close')}</Button>
      </DialogActions>
    </Dialog>
  )
}
