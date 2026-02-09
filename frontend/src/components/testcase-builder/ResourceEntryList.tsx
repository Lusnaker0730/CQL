import { useState } from 'react'
import {
  Box,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  IconButton,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Chip,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Person,
  LocalHospital,
  MedicalServices,
  Info as InfoIcon,
} from '@mui/icons-material'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'

const RESOURCE_ICONS: Record<string, React.ReactNode> = {
  Patient: <Person />,
  Encounter: <LocalHospital />,
}
const DEFAULT_ICON = <MedicalServices />

interface ResourceEntryListProps {
  onDirty: () => void
}

export default function ResourceEntryList({ onDirty }: ResourceEntryListProps) {
  const { state, dispatch } = useBundleBuilder()
  const [deleteId, setDeleteId] = useState<string | null>(null)

  const handleDelete = () => {
    if (deleteId) {
      dispatch({ type: 'REMOVE_ENTRY', payload: deleteId })
      onDirty()
      setDeleteId(null)
    }
  }

  if (state.entries.length === 0) {
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <InfoIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
        <Typography variant="body2" color="text.secondary">
          No resources yet. Start by adding a Patient resource.
        </Typography>
      </Box>
    )
  }

  return (
    <>
      <List dense disablePadding>
        {state.entries.map((entry) => (
          <ListItemButton
            key={entry.id}
            selected={state.activeEntryId === entry.id}
            onClick={() => dispatch({ type: 'SET_ACTIVE_ENTRY', payload: entry.id })}
            sx={{ borderRadius: 1, mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 36 }}>
              {RESOURCE_ICONS[entry.resourceType] || DEFAULT_ICON}
            </ListItemIcon>
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip
                    label={entry.resourceType}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.75rem', height: 22 }}
                  />
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {(entry.resourceData.id as string) || entry.id}
                  </Typography>
                </Box>
              }
            />
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                setDeleteId(entry.id)
              }}
              sx={{ ml: 1 }}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </ListItemButton>
        ))}
      </List>

      <Dialog open={!!deleteId} onClose={() => setDeleteId(null)}>
        <DialogTitle>Delete Resource</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to remove this resource from the test case bundle?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteId(null)}>Cancel</Button>
          <Button color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
