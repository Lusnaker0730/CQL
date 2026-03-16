import { useState, useMemo } from 'react'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  List,
  ListItemButton,
  ListItemText,
  Typography,
  CircularProgress,
  Box,
  Chip,
  InputAdornment,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { cqlApi } from '../../api'
import type { CqlLibrary } from '../../types'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'

interface ImportCqlFromLibraryDialogProps {
  open: boolean
  onClose: () => void
  onImport: (cqlContent: string) => void
  hasExistingContent: boolean
}

export default function ImportCqlFromLibraryDialog({
  open,
  onClose,
  onImport,
  hasExistingContent,
}: ImportCqlFromLibraryDialogProps) {
  const { t } = useTranslation('measures')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<CqlLibrary | null>(null)

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_GENERAL_MS)

  const { data: libraries, isLoading } = useQuery({
    queryKey: ['cql-libraries', debouncedSearch],
    queryFn: () => cqlApi.getLibraries(debouncedSearch || undefined),
    enabled: open,
  })

  const filteredLibraries = useMemo(() => {
    if (!libraries) return []
    if (!search) return libraries
    const lower = search.toLowerCase()
    return libraries.filter(
      (lib) =>
        lib.name.toLowerCase().includes(lower) ||
        (lib.description && lib.description.toLowerCase().includes(lower))
    )
  }, [libraries, search])

  const handleImport = () => {
    if (!selected) return
    if (hasExistingContent) {
      if (!window.confirm(t('cql.importDialog.confirmOverwrite'))) return
    }
    onImport(selected.cqlContent)
    handleClose()
  }

  const handleClose = () => {
    setSearch('')
    setSelected(null)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('cql.importDialog.title')}</DialogTitle>
      <DialogContent>
        <TextField
          fullWidth
          size="small"
          placeholder={t('cql.importDialog.search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ mb: 1, mt: 1 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={32} />
          </Box>
        ) : filteredLibraries.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            {t('cql.importDialog.noLibraries')}
          </Typography>
        ) : (
          <List sx={{ maxHeight: 350, overflow: 'auto' }}>
            {filteredLibraries.map((lib) => (
              <ListItemButton
                key={lib.id}
                selected={selected?.id === lib.id}
                onClick={() => setSelected(lib)}
              >
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body2" fontWeight="medium">
                        {lib.name}
                      </Typography>
                      <Chip
                        label={t('cql.importDialog.version', { version: lib.version })}
                        size="small"
                        variant="outlined"
                        sx={{ height: 20, fontSize: '0.7rem' }}
                      />
                    </Box>
                  }
                  secondary={lib.description || undefined}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('cql.importDialog.cancel')}</Button>
        <Button variant="contained" disabled={!selected} onClick={handleImport}>
          {t('cql.importDialog.import')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
