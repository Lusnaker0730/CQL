import { useState } from 'react'
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
  ListItemIcon,
  Typography,
  Stack,
  Divider,
  CircularProgress,
} from '@mui/material'
import {
  Star as StarIcon,
  History as HistoryIcon,
  Search as SearchIcon,
} from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { cqlApi } from '../../api'
import { useLibraryHistory } from '../../hooks/useLibraryHistory'

interface LibraryPickerProps {
  open: boolean
  onClose: () => void
  onSelect: (cqlContent: string) => void
}

export default function LibraryPicker({ open, onClose, onSelect }: LibraryPickerProps) {
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const { favoritesList, recentList } = useLibraryHistory()

  const { data: searchResults = [], isFetching } = useQuery({
    queryKey: ['library-search', search],
    queryFn: () => cqlApi.getLibraries(search || undefined),
    enabled: search.length > 0,
  })

  const handleSelect = async (libraryId: string) => {
    setLoading(true)
    try {
      const library = await cqlApi.getLibrary(libraryId)
      onSelect(library.cqlContent)
      onClose()
    } catch {
      // Library may no longer exist
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Load from Library</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {/* Favorites section */}
          {favoritesList.length > 0 && (
            <>
              <Stack direction="row" alignItems="center" spacing={0.5}>
                <StarIcon sx={{ fontSize: 18, color: 'warning.main' }} />
                <Typography variant="subtitle2">Favorites</Typography>
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
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItemButton>
                ))}
              </List>
              <Divider />
            </>
          )}

          {/* Recent section */}
          {recentList.length > 0 && (
            <>
              <Stack direction="row" alignItems="center" spacing={0.5}>
                <HistoryIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="subtitle2">Recent</Typography>
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
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItemButton>
                ))}
              </List>
              <Divider />
            </>
          )}

          {/* Search */}
          <Stack direction="row" alignItems="center" spacing={0.5}>
            <SearchIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
            <Typography variant="subtitle2">Search Libraries</Typography>
          </Stack>
          <TextField
            size="small"
            fullWidth
            placeholder="Search by name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            InputProps={{ startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary', fontSize: 18 }} /> }}
          />
          {isFetching && <CircularProgress size={20} />}
          {search.length > 0 && searchResults.length > 0 && (
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
                    primaryTypographyProps={{ variant: 'body2' }}
                    secondaryTypographyProps={{ variant: 'caption' }}
                  />
                </ListItemButton>
              ))}
            </List>
          )}
          {search.length > 0 && !isFetching && searchResults.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              No libraries found.
            </Typography>
          )}

          {loading && (
            <Stack direction="row" spacing={1} alignItems="center">
              <CircularProgress size={16} />
              <Typography variant="body2" color="text.secondary">Loading library...</Typography>
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
      </DialogActions>
    </Dialog>
  )
}
