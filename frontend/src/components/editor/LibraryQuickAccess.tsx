import {
  Paper,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  IconButton,
  Stack,
  Divider,
  Collapse,
  Box,
} from '@mui/material'
import {
  Star as StarIcon,
  StarBorder as StarBorderIcon,
  History as HistoryIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  DeleteSweep as ClearIcon,
} from '@mui/icons-material'
import { useState } from 'react'
import { useLibraryHistory } from '../../hooks/useLibraryHistory'
import { useDispatch } from 'react-redux'
import { setCqlContent } from '../../store/editorSlice'
import { cqlApi } from '../../api'

export default function LibraryQuickAccess() {
  const { recent, toggleFavorite, isFavorite, clearRecent } = useLibraryHistory()
  const dispatch = useDispatch()
  const [favoritesOpen, setFavoritesOpen] = useState(true)
  const [recentOpen, setRecentOpen] = useState(true)

  const favoriteItems = recent.filter((r) => isFavorite(r.id))

  const handleLoadLibrary = async (id: string) => {
    try {
      const library = await cqlApi.getLibrary(id)
      dispatch(setCqlContent(library.cqlContent))
    } catch {
      // Library may no longer exist
    }
  }

  return (
    <Paper
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '3px solid',
        borderLeftColor: 'warning.main',
        overflow: 'auto',
      }}
    >
      <Box
        sx={{
          p: 1,
          px: 1.5,
          background: 'linear-gradient(135deg, rgba(237,108,2,0.06) 0%, rgba(255,152,0,0.03) 100%)',
          borderBottom: '1px solid',
          borderColor: 'rgba(237,108,2,0.1)',
        }}
      >
        <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'secondary.main' }}>
          Quick Access
        </Typography>
      </Box>

      {/* Favorites */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.5, cursor: 'pointer' }}
        onClick={() => setFavoritesOpen(!favoritesOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <StarIcon sx={{ fontSize: 16, color: 'warning.main' }} />
          <Typography variant="subtitle2">Favorites</Typography>
        </Stack>
        {favoritesOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </Stack>
      <Collapse in={favoritesOpen}>
        {favoriteItems.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
            No favorites yet. Star a library to add it here.
          </Typography>
        ) : (
          <List dense disablePadding>
            {favoriteItems.map((item) => (
              <ListItemButton key={item.id} onClick={() => handleLoadLibrary(item.id)} sx={{ py: 0.25 }}>
                <ListItemIcon sx={{ minWidth: 28 }}>
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation()
                      toggleFavorite(item.id)
                    }}
                  >
                    <StarIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                  </IconButton>
                </ListItemIcon>
                <ListItemText
                  primary={item.name}
                  secondary={`v${item.version}`}
                  primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </Collapse>

      <Divider />

      {/* Recent */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.5, cursor: 'pointer' }}
        onClick={() => setRecentOpen(!recentOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <HistoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
          <Typography variant="subtitle2">Recent</Typography>
        </Stack>
        <Stack direction="row" alignItems="center" spacing={0.5}>
          {recent.length > 0 && (
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                clearRecent()
              }}
              title="Clear recent"
            >
              <ClearIcon sx={{ fontSize: 16 }} />
            </IconButton>
          )}
          {recentOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </Stack>
      </Stack>
      <Collapse in={recentOpen}>
        {recent.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
            No recent libraries.
          </Typography>
        ) : (
          <List dense disablePadding>
            {recent.map((item) => (
              <ListItemButton key={item.id} onClick={() => handleLoadLibrary(item.id)} sx={{ py: 0.25 }}>
                <ListItemIcon sx={{ minWidth: 28 }}>
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation()
                      toggleFavorite(item.id)
                    }}
                  >
                    {isFavorite(item.id) ? (
                      <StarIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                    ) : (
                      <StarBorderIcon sx={{ fontSize: 16 }} />
                    )}
                  </IconButton>
                </ListItemIcon>
                <ListItemText
                  primary={item.name}
                  secondary={`v${item.version}`}
                  primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </Collapse>
    </Paper>
  )
}
