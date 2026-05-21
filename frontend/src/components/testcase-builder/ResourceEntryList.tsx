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
  Tooltip,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Info as InfoIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import { getResourceIcon } from './constants'

interface ResourceEntryListProps {
  onDirty: () => void
}

export default function ResourceEntryList({ onDirty }: ResourceEntryListProps) {
  const { t } = useTranslation('measures')
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
          {t('testCaseBuilder.noResourcesYet')}
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
              {getResourceIcon(entry.resourceType)}
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
                  <Tooltip title={(entry.resourceData.id as string) || entry.id}>
                    <Typography variant="caption" color="text.secondary" noWrap>
                      {(entry.resourceData.id as string) || entry.id}
                    </Typography>
                  </Tooltip>
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
              aria-label={t('testCaseBuilder.deleteResourceAria')}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </ListItemButton>
        ))}
      </List>

      <Dialog open={!!deleteId} onClose={() => setDeleteId(null)}>
        <DialogTitle>{t('testCaseBuilder.deleteResource')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('testCaseBuilder.deleteConfirm')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteId(null)}>{t('testCaseBuilder.cancel')}</Button>
          <Button color="error" onClick={handleDelete}>{t('testCaseBuilder.delete')}</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
