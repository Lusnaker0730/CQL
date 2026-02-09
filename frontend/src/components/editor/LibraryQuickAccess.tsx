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
  Chip,
  Tabs,
  Tab,
} from '@mui/material'
import {
  Star as StarIcon,
  StarBorder as StarBorderIcon,
  History as HistoryIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  DeleteSweep as ClearIcon,
  LocalHospital as TwcdiIcon,
  Public as PublicIcon,
  Group as SharedIcon,
  Lock as PrivateIcon,
} from '@mui/icons-material'
import { useState } from 'react'
import { useLibraryHistory } from '../../hooks/useLibraryHistory'
import { useLibraries } from '../../hooks/useCql'
import { useDispatch } from 'react-redux'
import { setCqlContent } from '../../store/editorSlice'
import { cqlApi } from '../../api'
import { TWCDI_TEMPLATES } from '../../constants/twcdiTemplates'

const ACCESS_ICON = {
  private: <PrivateIcon sx={{ fontSize: 12 }} />,
  shared: <SharedIcon sx={{ fontSize: 12 }} />,
  public: <PublicIcon sx={{ fontSize: 12 }} />,
}

export default function LibraryQuickAccess() {
  const { recent, favoritesList, toggleFavorite, isFavorite, clearRecent } = useLibraryHistory()
  const dispatch = useDispatch()
  const [favoritesOpen, setFavoritesOpen] = useState(true)
  const [recentOpen, setRecentOpen] = useState(true)
  const [twcdiOpen, setTwcdiOpen] = useState(false)
  const [browseOpen, setBrowseOpen] = useState(false)
  const [browseFilter, setBrowseFilter] = useState(0) // 0=All, 1=My, 2=Shared, 3=Public

  const { data: allLibraries = [] } = useLibraries()

  const currentUser = (() => {
    try { return JSON.parse(localStorage.getItem('user') || '{}').username || '' }
    catch { return '' }
  })()

  const filteredLibraries = allLibraries.filter((lib) => {
    if (browseFilter === 1) return lib.ownerUsername === currentUser
    if (browseFilter === 2) return lib.sharedWith?.includes(currentUser) || lib.accessLevel === 'shared'
    if (browseFilter === 3) return lib.accessLevel === 'public'
    return true
  })

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
        {favoritesList.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
            No favorites yet. Star a library to add it here.
          </Typography>
        ) : (
          <List dense disablePadding>
            {favoritesList.map((item) => (
              <ListItemButton key={item.libraryId} onClick={() => handleLoadLibrary(String(item.libraryId))} sx={{ py: 0.25 }}>
                <ListItemIcon sx={{ minWidth: 28 }}>
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation()
                      toggleFavorite(String(item.libraryId))
                    }}
                  >
                    <StarIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                  </IconButton>
                </ListItemIcon>
                <ListItemText
                  primary={item.libraryName}
                  secondary={`v${item.libraryVersion}`}
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

      <Divider />

      {/* Browse Libraries with filter tabs */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.5, cursor: 'pointer' }}
        onClick={() => setBrowseOpen(!browseOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <SharedIcon sx={{ fontSize: 16, color: 'primary.main' }} />
          <Typography variant="subtitle2">Browse</Typography>
          <Chip label={filteredLibraries.length} size="small" sx={{ height: 16, fontSize: '0.65rem' }} />
        </Stack>
        {browseOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </Stack>
      <Collapse in={browseOpen}>
        <Tabs
          value={browseFilter}
          onChange={(_, v) => setBrowseFilter(v)}
          variant="fullWidth"
          sx={{
            minHeight: 28,
            '& .MuiTab-root': { minHeight: 28, py: 0, fontSize: '0.7rem', minWidth: 0 },
          }}
        >
          <Tab label="All" />
          <Tab label="My" />
          <Tab label="Shared" />
          <Tab label="Public" />
        </Tabs>
        {filteredLibraries.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1, display: 'block' }}>
            No libraries found.
          </Typography>
        ) : (
          <List dense disablePadding sx={{ maxHeight: 200, overflow: 'auto' }}>
            {filteredLibraries.map((lib) => (
              <ListItemButton key={lib.id} onClick={() => handleLoadLibrary(lib.id)} sx={{ py: 0.25 }}>
                <ListItemIcon sx={{ minWidth: 24 }}>
                  {ACCESS_ICON[lib.accessLevel as keyof typeof ACCESS_ICON] || ACCESS_ICON.private}
                </ListItemIcon>
                <ListItemText
                  primary={lib.name}
                  secondary={`v${lib.version} - ${lib.status}`}
                  primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                  secondaryTypographyProps={{ variant: 'caption', noWrap: true }}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </Collapse>

      <Divider />

      {/* TWCDI Templates */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.5, cursor: 'pointer' }}
        onClick={() => setTwcdiOpen(!twcdiOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <TwcdiIcon sx={{ fontSize: 16, color: 'primary.main' }} />
          <Typography variant="subtitle2">TWCDI Templates</Typography>
        </Stack>
        {twcdiOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </Stack>
      <Collapse in={twcdiOpen}>
        <List dense disablePadding>
          {TWCDI_TEMPLATES.map((template) => (
            <ListItemButton
              key={template.name}
              onClick={() => dispatch(setCqlContent(template.cql))}
              sx={{ py: 0.25 }}
            >
              <ListItemText
                primary={template.name}
                secondary={template.description}
                primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                secondaryTypographyProps={{ variant: 'caption', noWrap: true }}
              />
            </ListItemButton>
          ))}
        </List>
      </Collapse>
    </Paper>
  )
}
