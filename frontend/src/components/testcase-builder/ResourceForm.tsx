import { useState, useCallback, useMemo } from 'react'
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
  Alert,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon, Add as AddIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import CardListSkeleton from '../common/CardListSkeleton'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import { useFhirMetadata } from '../../hooks/useFhirMetadata'
import { ResourceTypeProvider } from '../../contexts/ResourceTypeContext'
import ResourceFormHeader from './ResourceFormHeader'
import ElementField from './ElementField'
import { getDefaultValue } from '../../utils/fhirDefaults'
import {
  buildChoiceFieldName,
  detectChoiceType,
  listChoiceFieldNames,
} from '../../utils/fhirChoice'
import type { ElementMetadata } from '../../types'

interface ResourceFormProps {
  onDirty: () => void
}

export default function ResourceForm({ onDirty }: ResourceFormProps) {
  const { t } = useTranslation('measures')
  const { state, dispatch } = useBundleBuilder()
  const activeEntry = state.entries.find((e) => e.id === state.activeEntryId)
  const { data: metadata, isLoading, isError } = useFhirMetadata(activeEntry?.resourceType)
  const [addAttrOpen, setAddAttrOpen] = useState(false)
  const [selectedAttrs, setSelectedAttrs] = useState<Set<string>>(new Set())

  const elements = useMemo<ElementMetadata[]>(() => metadata?.elements || [], [metadata])

  // Lookup: any flattened choice key (e.g. "valueQuantity") → its base element name.
  // Drives both the "is this key a choice variant?" check and the cleanup-on-swap logic.
  const choiceVariantToBaseName = useMemo(() => {
    const map = new Map<string, string>()
    for (const el of elements) {
      if (el.isChoiceType && el.choiceTypes) {
        for (const variant of listChoiceFieldNames(el.name, el.choiceTypes)) {
          map.set(variant, el.name)
        }
      }
    }
    return map
  }, [elements])

  const handleFieldChange = useCallback(
    (path: string, value: unknown, choiceFieldName?: string) => {
      if (!activeEntry) return
      const baseName = path.split('.').slice(1).join('.')
      const newData = { ...activeEntry.resourceData }

      if (choiceFieldName) {
        // Choice type: delete only the *known* variants for this base element.
        // Looking up the base name from the chosen variant keeps us safe even if
        // someone later adds a non-choice sibling that happens to share the prefix
        // (e.g. `valueSet` next to `value[x]`).
        const baseElementName = choiceVariantToBaseName.get(choiceFieldName) ?? baseName
        const baseElement = elements.find((e) => e.name === baseElementName)
        if (baseElement?.choiceTypes) {
          for (const variant of listChoiceFieldNames(baseElementName, baseElement.choiceTypes)) {
            delete newData[variant]
          }
        }
        delete newData[baseElementName]

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
    [activeEntry, dispatch, onDirty, elements, choiceVariantToBaseName]
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

  const requiredElements = elements.filter((el) => el.isRequired)

  // Build set of element names that have data (including choice type variants)
  // NOTE: useMemo MUST be called before any early returns to satisfy Rules of Hooks
  const { visibleOptional, hiddenOptional } = useMemo(() => {
    if (!activeEntry) return { visibleOptional: [] as ElementMetadata[], hiddenOptional: [] as ElementMetadata[] }
    const dataKeys = Object.keys(activeEntry.resourceData)

    const filledNames = new Set<string>()
    for (const key of dataKeys) {
      if (key === 'id') continue
      const directEl = elements.find((e) => e.name === key)
      if (directEl && !directEl.isRequired) {
        filledNames.add(directEl.name)
        continue
      }
      if (!directEl) {
        const elName = choiceVariantToBaseName.get(key)
        if (elName) {
          const choiceEl = elements.find((e) => e.name === elName)
          if (choiceEl && !choiceEl.isRequired) filledNames.add(elName)
        }
      }
    }

    return {
      visibleOptional: elements.filter((el) => !el.isRequired && filledNames.has(el.name)),
      hiddenOptional: elements.filter((el) => !el.isRequired && !filledNames.has(el.name)),
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps -- only re-compute when resourceData changes, not the whole entry
  }, [elements, choiceVariantToBaseName, activeEntry?.resourceData])

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

  if (isError) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" variant="outlined">
          {t('testCaseBuilder.metadataLoadError', { resourceType: activeEntry.resourceType })}
        </Alert>
      </Box>
    )
  }

  const handleAddAttributes = () => {
    if (!activeEntry) return
    const newData = { ...activeEntry.resourceData }
    selectedAttrs.forEach((name) => {
      const el = elements.find((e) => e.name === name)
      if (el && !(name in newData)) {
        newData[name] = el.isArray ? [] : getDefaultValue(el)
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
        const key = buildChoiceFieldName(el.name, ct)
        if (key in activeEntry.resourceData) {
          return activeEntry.resourceData[key]
        }
      }
    }
    return activeEntry.resourceData[el.name]
  }

  const getSelectedChoiceType = (el: ElementMetadata): string | undefined => {
    if (!el.isChoiceType || !el.choiceTypes) return undefined
    return detectChoiceType(el.name, el.choiceTypes, activeEntry.resourceData)
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
          aria-label={t('testCaseBuilder.addAttributeAria', { count: hiddenOptional.length })}
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
