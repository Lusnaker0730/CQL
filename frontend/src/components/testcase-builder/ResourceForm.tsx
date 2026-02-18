import { useState, useCallback } from 'react'
import {
  Box,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItemButton,
  ListItemText,
  Checkbox,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon, Add as AddIcon } from '@mui/icons-material'
import CardListSkeleton from '../common/CardListSkeleton'
import { STRINGS } from './constants'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import { useFhirMetadata } from '../../hooks/useFhirMetadata'
import { ResourceTypeProvider } from '../../contexts/ResourceTypeContext'
import ResourceFormHeader from './ResourceFormHeader'
import ElementField from './ElementField'
import type { ElementMetadata } from '../../types'

interface ResourceFormProps {
  onDirty: () => void
}

export default function ResourceForm({ onDirty }: ResourceFormProps) {
  const { state, dispatch } = useBundleBuilder()
  const activeEntry = state.entries.find((e) => e.id === state.activeEntryId)
  const { data: metadata, isLoading } = useFhirMetadata(activeEntry?.resourceType)
  const [addAttrOpen, setAddAttrOpen] = useState(false)
  const [selectedAttrs, setSelectedAttrs] = useState<Set<string>>(new Set())

  const handleFieldChange = useCallback(
    (path: string, value: unknown) => {
      if (!activeEntry) return
      const fieldName = path.split('.').slice(1).join('.')
      const newData = { ...activeEntry.resourceData }

      if (value === undefined || value === null || value === '') {
        delete newData[fieldName]
      } else {
        newData[fieldName] = value
      }

      dispatch({
        type: 'UPDATE_ENTRY',
        payload: { id: activeEntry.id, resourceData: newData },
      })
      onDirty()
    },
    [activeEntry, dispatch, onDirty]
  )

  const handleIdChange = useCallback(
    (newId: string) => {
      if (!activeEntry) return
      dispatch({
        type: 'UPDATE_ENTRY',
        payload: { id: activeEntry.id, resourceData: { ...activeEntry.resourceData, id: newId } },
      })
      onDirty()
    },
    [activeEntry, dispatch, onDirty]
  )

  if (!activeEntry) {
    return (
      <Box sx={{ p: 3, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          {STRINGS.selectResource}
        </Typography>
      </Box>
    )
  }

  if (isLoading) {
    return (
      <Box sx={{ p: 3 }}>
        <CardListSkeleton count={4} />
      </Box>
    )
  }

  const elements = metadata?.elements || []
  const requiredElements = elements.filter((el) => el.isRequired)
  const filledOptionalNames = new Set(
    Object.keys(activeEntry.resourceData).filter(
      (k) => k !== 'id' && !requiredElements.some((r) => r.name === k)
    )
  )
  const visibleOptional = elements.filter(
    (el) => !el.isRequired && filledOptionalNames.has(el.name)
  )
  const hiddenOptional = elements.filter(
    (el) => !el.isRequired && !filledOptionalNames.has(el.name)
  )

  const handleAddAttributes = () => {
    if (!activeEntry) return
    const newData = { ...activeEntry.resourceData }
    selectedAttrs.forEach((name) => {
      const el = elements.find((e) => e.name === name)
      if (el && !(name in newData)) {
        newData[name] = el.isArray ? [] : (el.type === 'boolean' ? false : '')
      }
    })
    dispatch({
      type: 'UPDATE_ENTRY',
      payload: { id: activeEntry.id, resourceData: newData },
    })
    onDirty()
    setSelectedAttrs(new Set())
    setAddAttrOpen(false)
  }

  const getFieldValue = (el: ElementMetadata) => {
    return activeEntry.resourceData[el.name]
  }

  return (
    <ResourceTypeProvider value={activeEntry.resourceType}>
    <Box>
      <ResourceFormHeader
        resourceType={activeEntry.resourceType}
        resourceId={(activeEntry.resourceData.id as string) || activeEntry.id}
        onIdChange={handleIdChange}
      />

      {requiredElements.length > 0 && (
        <Accordion defaultExpanded disableGutters sx={{ '&:before': { display: 'none' } }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="subtitle2">
              {STRINGS.requiredFields}
            </Typography>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            {requiredElements.map((el) => (
              <ElementField
                key={el.name}
                element={el}
                path={`${activeEntry.resourceType}.${el.name}`}
                value={getFieldValue(el)}
                onChange={(val) => handleFieldChange(`${activeEntry.resourceType}.${el.name}`, val)}
                depth={0}
              />
            ))}
          </AccordionDetails>
        </Accordion>
      )}

      {visibleOptional.length > 0 && (
        <Accordion defaultExpanded disableGutters sx={{ '&:before': { display: 'none' } }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="subtitle2">
              {STRINGS.optionalFields}
            </Typography>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            {visibleOptional.map((el) => (
              <ElementField
                key={el.name}
                element={el}
                path={`${activeEntry.resourceType}.${el.name}`}
                value={getFieldValue(el)}
                onChange={(val) => handleFieldChange(`${activeEntry.resourceType}.${el.name}`, val)}
                depth={0}
              />
            ))}
          </AccordionDetails>
        </Accordion>
      )}

      <Box sx={{ mt: 1 }}>
        <Button
          size="small"
          startIcon={<AddIcon />}
          onClick={() => setAddAttrOpen(true)}
          disabled={hiddenOptional.length === 0}
          sx={{ textTransform: 'none' }}
        >
          {STRINGS.addAttribute} ({hiddenOptional.length} available)
        </Button>
      </Box>

      <Dialog open={addAttrOpen} onClose={() => setAddAttrOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{STRINGS.addAttributes}</DialogTitle>
        <DialogContent>
          <List dense>
            {hiddenOptional.map((el) => (
              <ListItemButton
                key={el.name}
                onClick={() => {
                  const next = new Set(selectedAttrs)
                  if (next.has(el.name)) next.delete(el.name)
                  else next.add(el.name)
                  setSelectedAttrs(next)
                }}
              >
                <Checkbox
                  checked={selectedAttrs.has(el.name)}
                  size="small"
                  sx={{ p: 0.5, mr: 1 }}
                />
                <ListItemText
                  primary={el.name}
                  secondary={
                    [el.type, el.isArray ? '(array)' : '', el.description || '']
                      .filter(Boolean)
                      .join(' ')
                  }
                  primaryTypographyProps={{ variant: 'body2' }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItemButton>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddAttrOpen(false)}>{STRINGS.cancel}</Button>
          <Button
            variant="contained"
            disabled={selectedAttrs.size === 0}
            onClick={handleAddAttributes}
          >
            Add ({selectedAttrs.size})
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
    </ResourceTypeProvider>
  )
}
