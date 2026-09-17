import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  List,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  Typography,
  Stack,
  Divider,
  CircularProgress,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import StarIcon from '@mui/icons-material/Star'
import HistoryIcon from '@mui/icons-material/History'
import SearchIcon from '@mui/icons-material/Search'
import { useQuery } from '@tanstack/react-query'
import { cqlApi } from '../../api'
import { useLibraryHistory } from '../../hooks/useLibraryHistory'
import { useNotification } from '../../hooks/useNotification'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
import { STALE_30S } from '../../constants/queryConstants'

interface LibraryPickerProps {
  open: boolean
  onClose: () => void
  onSelect: (cqlContent: string) => void
}

export default function LibraryPicker({ open, onClose, onSelect }: LibraryPickerProps) {
  const { t } = useTranslation('editor')
  const { showNotification } = useNotification()
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const { favoritesList, recentList } = useLibraryHistory()

  // Guards post-await setState calls when the user closes the dialog mid-flight.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // Race-guard: only the most-recent click should dispatch onSelect. Without
  // this, a user clicking library A then quickly switching to B can have A's
  // (slower) response win and silently overwrite the editor with the wrong CQL.
  const requestTokenRef = useRef(0)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_GENERAL_MS)
    return () => clearTimeout(timer)
  }, [search])

  const { data: searchResults = [], isFetching, isError: searchError } = useQuery({
    queryKey: ['library-search', debouncedSearch],
    queryFn: () => cqlApi.getLibraries(debouncedSearch || undefined),
    enabled: debouncedSearch.length > 0,
    staleTime: STALE_30S,
  })

  const handleSelect = async (libraryId: string) => {
    const token = ++requestTokenRef.current
    setLoading(true)
    try {
      const library = await cqlApi.getLibrary(libraryId)
      // Stale response from a superseded click — drop it silently.
      if (token !== requestTokenRef.current) return
      if (!isMountedRef.current) return
      onSelect(library.cqlContent)
      onClose()
    } catch {
      if (token !== requestTokenRef.current) return
      if (!isMountedRef.current) return
      showNotification(t('libraryPicker.loadFailed'), 'error')
    } finally {
      if (token === requestTokenRef.current && isMountedRef.current) {
        setLoading(false)
      }
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('libraryPicker.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {/* Favorites section */}
          {favoritesList.length > 0 && (
            <>
              <Stack direction="row" spacing={0.5} sx={{
                alignItems: "center"
              }}>
                <StarIcon sx={{ fontSize: 18, color: 'warning.main' }} />
                <Typography variant="subtitle2">{t('libraryPicker.favorites')}</Typography>
              </Stack>
              <List dense disablePadding>
                {favoritesList.map((item) => (
                  <ListItemButton
                    key={item.libraryId}
                    onClick={() => handleSelect(item.libraryId)}
                    disabled={loading}
                  >
                    <ListItemIcon sx={{ minWidth: 32 }}>
                      <StarIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={item.libraryName}
                      secondary={`v${item.libraryVersion}`}
                      slotProps={{
                        primary: { variant: 'body2' },
                        secondary: { variant: 'caption' }
                      }} />
                  </ListItemButton>
                ))}
              </List>
              <Divider />
            </>
          )}

          {/* Recent section */}
          {recentList.length > 0 && (
            <>
              <Stack direction="row" spacing={0.5} sx={{
                alignItems: "center"
              }}>
                <HistoryIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="subtitle2">{t('libraryPicker.recent')}</Typography>
              </Stack>
              <List dense disablePadding>
                {recentList.map((item) => (
                  <ListItemButton
                    key={item.libraryId}
                    onClick={() => handleSelect(item.libraryId)}
                    disabled={loading}
                  >
                    <ListItemIcon sx={{ minWidth: 32 }}>
                      <HistoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={item.libraryName}
                      secondary={`v${item.libraryVersion}`}
                      slotProps={{
                        primary: { variant: 'body2' },
                        secondary: { variant: 'caption' }
                      }} />
                  </ListItemButton>
                ))}
              </List>
              <Divider />
            </>
          )}

          {/* Search */}
          <Stack direction="row" spacing={0.5} sx={{
            alignItems: "center"
          }}>
            <SearchIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
            <Typography variant="subtitle2">{t('libraryPicker.searchLibraries')}</Typography>
          </Stack>
          <TextField
            size="small"
            fullWidth
            placeholder={t('libraryPicker.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            slotProps={{
              input: { startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary', fontSize: 18 }} /> }
            }}
          />
          {isFetching && <CircularProgress size={20} />}
          {searchError && (
            <Alert severity="error">{t('libraryPicker.searchFailed')}</Alert>
          )}
          {debouncedSearch.length > 0 && searchResults.length > 0 && (
            <List dense disablePadding sx={{ maxHeight: 200, overflow: 'auto' }}>
              {searchResults.map((lib) => (
                <ListItemButton
                  key={lib.id}
                  onClick={() => handleSelect(lib.id)}
                  disabled={loading}
                >
                  <ListItemText
                    primary={lib.name}
                    secondary={`v${lib.version}`}
                    slotProps={{
                      primary: { variant: 'body2' },
                      secondary: { variant: 'caption' }
                    }} />
                </ListItemButton>
              ))}
            </List>
          )}
          {debouncedSearch.length > 0 && !isFetching && searchResults.length === 0 && (
            <Typography variant="body2" sx={{
              color: "text.secondary"
            }}>
              {t('libraryPicker.noLibraries')}
            </Typography>
          )}

          {loading && (
            <Stack direction="row" spacing={1} sx={{
              alignItems: "center"
            }}>
              <CircularProgress size={16} />
              <Typography variant="body2" sx={{
                color: "text.secondary"
              }}>{t('libraryPicker.loading')}</Typography>
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.cancel')}</Button>
      </DialogActions>
    </Dialog>
  );
}
