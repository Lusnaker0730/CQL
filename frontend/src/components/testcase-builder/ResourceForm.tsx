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
import { useTranslation } from 'react-i18next'
import CardListSkeleton from '../common/CardListSkeleton'
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
  const { t } = useTranslation('measures')
  const { state, dispatch } = useBundleBuilder()
  const activeEntry = state.entries.find((e) => e.id === state.activeEntryId)
  const { data: metadata, isLoading } = useFhirMetadata(activeEntry?.resourceType)
  const [addAttrOpen, setAddAttrOpen] = useState(false)
  const [selectedAttrs, setSelectedAttrs] = useState<Set<string>>(new Set())

  const handleFieldChange = useCallback(
    (path: string, value: unknown, choiceFieldName?: string) => {
      if (!activeEntry) return
      const baseName = path.split('.').slice(1).join('.')
      const newData = { ...activeEntry.resourceData }

      if (choiceFieldName) {
        // Choice type: clean up old variants (keys starting with baseName + uppercase,
        // e.g. "valueQuantity", "valueString") and the base name itself
        for (const key of Object.keys(newData)) {
          if (key === baseName || (key.startsWith(baseName) && key.length > baseName.length && /[A-Z]/.test(key[baseName.length]))) {
            delete newData[key]
          }
        }

        if (value !== undefined && value !== null && value !== '') {
          newData[choiceFieldName] = value
        }
      } else {
        if (value === undefined || value === null || value === '') {
          delete newData[baseName]
        } else {
          newData[baseName] = value
        }
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
          {t('testCaseBuilder.selectResource')}
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
  // Build set of element names that have data (including choice type variants)
  const filledOptionalNames = new Set<string>()
  for (const key of Object.keys(activeEntry.resourceData)) {
    if (key === 'id') continue
    // Direct match: key equals an element name
    const directEl = elements.find((e) => e.name === key)
    if (directEl && !directEl.isRequired) {
      filledOptionalNames.add(directEl.name)
      continue
    }
    // Choice type variant: e.g., key "valueQuantity" matches element "value"
    if (!directEl) {
      const choiceEl = elements.find((e) =>
        e.isChoiceType && e.choiceTypes?.some((ct) => {
          const typedName = e.name + ct.charAt(0).toUpperCase() + ct.slice(1)
          return typedName === key
        })
      )
      if (choiceEl && !choiceEl.isRequired) {
        filledOptionalNames.add(choiceEl.name)
      }
    }
  }
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
    // For choice types, look for typed keys (e.g., "valueQuantity" for element "value")
    if (el.isChoiceType && el.choiceTypes) {
      for (const ct of el.choiceTypes) {
        const key = el.name + ct.charAt(0).toUpperCase() + ct.slice(1)
        if (key in activeEntry.resourceData) {
          return activeEntry.resourceData[key]
        }
      }
    }
    return activeEntry.resourceData[el.name]
  }

  /** Detect which choice type is currently stored (e.g., "Quantity" from key "valueQuantity") */
  const getSelectedChoiceType = (el: ElementMetadata): string | undefined => {
    if (!el.isChoiceType || !el.choiceTypes) return undefined
    for (const ct of el.choiceTypes) {
      const key = el.name + ct.charAt(0).toUpperCase() + ct.slice(1)
      if (key in activeEntry.resourceData) return ct
    }
    return undefined
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
              {t('testCaseBuilder.requiredFields')}
            </Typography>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            {requiredElements.map((el) => (
              <ElementField
                key={el.name}
                element={el}
                path={`${activeEntry.resourceType}.${el.name}`}
                value={getFieldValue(el)}
                onChange={(val, choiceFieldName) => handleFieldChange(`${activeEntry.resourceType}.${el.name}`, val, choiceFieldName)}
                initialChoiceType={getSelectedChoiceType(el)}
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
              {t('testCaseBuilder.optionalFields')}
            </Typography>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            {visibleOptional.map((el) => (
              <ElementField
                key={el.name}
                element={el}
                path={`${activeEntry.resourceType}.${el.name}`}
                value={getFieldValue(el)}
                onChange={(val, choiceFieldName) => handleFieldChange(`${activeEntry.resourceType}.${el.name}`, val, choiceFieldName)}
                initialChoiceType={getSelectedChoiceType(el)}
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
          {t('testCaseBuilder.addAttribute')} {t('testCaseBuilder.available', { count: hiddenOptional.length })}
        </Button>
      </Box>

      <Dialog open={addAttrOpen} onClose={() => setAddAttrOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('testCaseBuilder.addAttributes')}</DialogTitle>
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
                    [el.type, el.isArray ? t('testCaseBuilder.array') : '', el.description || '']
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
          <Button onClick={() => setAddAttrOpen(false)}>{t('testCaseBuilder.cancel')}</Button>
          <Button
            variant="contained"
            disabled={selectedAttrs.size === 0}
            onClick={handleAddAttributes}
          >
            {t('testCaseBuilder.addCount', { count: selectedAttrs.size })}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
    </ResourceTypeProvider>
  )
}
