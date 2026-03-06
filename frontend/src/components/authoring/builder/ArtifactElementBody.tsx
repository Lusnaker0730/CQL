import { useState } from 'react'
import {
  Box, Stack, Typography, Chip, Divider, FormControl, InputLabel, Select, MenuItem,
  Dialog, DialogTitle, DialogContent, DialogActions, Button, IconButton, TextField,
} from '@mui/material'
import {
  Build as ModifierIcon, Close as CloseIcon, Check as CheckIcon, Handyman as BuildIcon,
  ArrowForward as ArrowIcon, ChevronLeft as BackIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import StringField from '../fields/StringField'
import NumberField from '../fields/NumberField'
import TextAreaField from '../fields/TextAreaField'
import ValueSetField from '../fields/ValueSetField'
import ExpressionPhrase from './ExpressionPhrase'
import ModifierCard from './ModifierCard'
import GradientButton from '../../common/GradientButton'
import CustomModifierBuilder from './CustomModifierBuilder'
import type { ElementInstance, ElementField, Modifier, ModifierDefinition } from '../../../types/authoring'
import { getEffectiveReturnType as getEffectiveRT } from '../../../utils/modifierUtils'

const DEMOGRAPHIC_SELECT_OPTIONS: Record<string, Array<{ value: string; label: string }>> = {
  'demographics/units_of_time': [
    { value: 'year', label: 'Year' },
    { value: 'month', label: 'Month' },
    { value: 'week', label: 'Week' },
    { value: 'day', label: 'Day' },
    { value: 'hour', label: 'Hour' },
  ],
  'demographics/gender': [
    { value: 'male', label: 'Male' },
    { value: 'female', label: 'Female' },
    { value: 'other', label: 'Other' },
    { value: 'unknown', label: 'Unknown' },
  ],
}

interface ArtifactElementBodyProps {
  element: ElementInstance
  modifiers: ModifierDefinition[]
  hideElementName?: boolean
  onUpdate: (updates: Partial<ElementInstance>) => void
}

export default function ArtifactElementBody({
  element,
  modifiers: allModifiers,
  hideElementName,
  onUpdate,
}: ArtifactElementBodyProps) {
  const { t } = useTranslation('authoring')
  const [modifierDialogOpen, setModifierDialogOpen] = useState(false)
  const [customBuilderOpen, setCustomBuilderOpen] = useState(false)
  const currentReturnType = getEffectiveReturnType(element)
  const applicableModifiers = allModifiers.filter(
    (m) =>
      m.inputTypes.includes(currentReturnType) &&
      !(element.suppressedModifiers || []).includes(m.id) &&
      !(element.modifiers || []).some((em) => em.id === m.id)
  )

  const handleFieldChange = (fieldId: string, value: unknown) => {
    const updatedFields = (element.fields || []).map((f) =>
      f.id === fieldId ? { ...f, value } : f
    )
    onUpdate({ fields: updatedFields })
  }

  const handleAddModifier = (modDef: ModifierDefinition) => {
    const newModifier: Modifier = {
      id: modDef.id,
      name: modDef.name,
      inputTypes: modDef.inputTypes,
      returnType: modDef.returnType,
      cqlTemplate: modDef.cqlTemplate,
      cqlLibraryFunction: modDef.cqlLibraryFunction,
      values: modDef.values ? { ...modDef.values } : undefined,
    }
    onUpdate({ modifiers: [...(element.modifiers || []), newModifier] })
  }

  const handleRemoveModifier = (index: number) => {
    const updated = [...(element.modifiers || [])]
    updated.splice(index, 1)
    // Remove subsequent incompatible modifiers from the chain
    const cleaned = validateModifierChain(element.returnType, updated)
    onUpdate({ modifiers: cleaned })
  }

  const handleUpdateModifier = (index: number, values: Record<string, unknown>) => {
    const updated = [...(element.modifiers || [])]
    updated[index] = { ...updated[index], values }
    onUpdate({ modifiers: updated })
  }

  const canHaveModifiers = !(element as ElementInstance & { cannotHaveModifiers?: boolean }).cannotHaveModifiers

  return (
    <Box>
      {/* Natural Language Summary */}
      <ExpressionPhrase element={element} variant="full" />

      {/* Element Fields */}
      <Stack spacing={2} my={2}>
        {(element.fields || [])
          .filter((field) => !(hideElementName && field.id === 'element_name'))
          .map((field) => (
          <FieldRenderer
            key={field.id}
            field={field}
            elementType={element.type}
            onChange={(value) => handleFieldChange(field.id, value)}
            onFieldUpdate={(updates) => {
              const updatedFields = (element.fields || []).map((f) =>
                f.id === field.id ? { ...f, ...updates } : f
              )
              onUpdate({ fields: updatedFields })
            }}
          />
        ))}
      </Stack>

      {/* Return Type */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 2 }}>
        <Typography variant="body2" fontWeight={600} color="text.secondary">
          {t('elementBody.returnType')}
        </Typography>
        <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />
        <Typography variant="body2">
          {formatReturnType(currentReturnType)}
        </Typography>
      </Box>

      {/* Modifiers */}
      {element.modifiers && element.modifiers.length > 0 && (
        <>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" color="text.secondary" mb={1}>
            {t('elementBody.modifiers')}
          </Typography>
          <Stack spacing={1} mb={1}>
            {element.modifiers.map((mod, i) => (
              <ModifierCard
                key={`${mod.id}-${i}`}
                modifier={mod}
                onRemove={() => handleRemoveModifier(i)}
                onUpdateValues={(values) => handleUpdateModifier(i, values)}
              />
            ))}
          </Stack>
        </>
      )}

      {/* Add Modifiers Buttons */}
      {canHaveModifiers && (
        <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
          {applicableModifiers.length > 0 && (
            <GradientButton
              onClick={() => setModifierDialogOpen(true)}
              size="small"
              startIcon={<ModifierIcon />}
            >
              {t('elementBody.selectModifiers')}
            </GradientButton>
          )}
          {currentReturnType.startsWith('list_of_') && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<BuildIcon />}
              onClick={() => setCustomBuilderOpen(true)}
            >
              {t('elementBody.buildNewModifier')}
            </Button>
          )}
        </Stack>
      )}

      {/* Modifier Selection Dialog */}
      <SelectModifiersDialog
        open={modifierDialogOpen}
        onClose={() => setModifierDialogOpen(false)}
        element={element}
        allModifiers={allModifiers}
        onAdd={handleAddModifier}
        onRemove={handleRemoveModifier}
      />

      {/* Custom Modifier Builder Dialog */}
      <CustomModifierBuilder
        open={customBuilderOpen}
        onClose={() => setCustomBuilderOpen(false)}
        inputType={currentReturnType}
        onAdd={(mod) => {
          onUpdate({ modifiers: [...(element.modifiers || []), mod] })
          setCustomBuilderOpen(false)
        }}
      />
    </Box>
  )
}

// ----- Modifier Selection Dialog -----

interface SelectModifiersDialogProps {
  open: boolean
  onClose: () => void
  element: ElementInstance
  allModifiers: ModifierDefinition[]
  onAdd: (mod: ModifierDefinition) => void
  onRemove: (index: number) => void
}

function SelectModifiersDialog({
  open,
  onClose,
  element,
  allModifiers,
  onAdd,
  onRemove,
}: SelectModifiersDialogProps) {
  const { t } = useTranslation('authoring')
  const [selectedModId, setSelectedModId] = useState<string>('')
  const [filterText, setFilterText] = useState('')

  const currentReturnType = getEffectiveReturnType(element)
  const applicableModifiers = allModifiers.filter(
    (m) =>
      m.inputTypes.includes(currentReturnType) &&
      !(element.suppressedModifiers || []).includes(m.id) &&
      !(element.modifiers || []).some((em) => em.id === m.id)
  )

  const selectedMod = applicableModifiers.find((m) => m.id === selectedModId)

  const filteredModifiers = filterText
    ? applicableModifiers.filter((m) => m.name.toLowerCase().includes(filterText.toLowerCase()))
    : applicableModifiers

  const handleAdd = () => {
    if (selectedMod) {
      onAdd(selectedMod)
      setSelectedModId('')
      setFilterText('')
    }
  }

  const handleClose = () => {
    setSelectedModId('')
    setFilterText('')
    onClose()
  }

  const chain = element.modifiers || []

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {t('elementBody.selectModifiersTitle')}
        <IconButton onClick={handleClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {/* Current expression summary */}
        <ExpressionPhrase element={element} variant="full" />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, my: 2 }}>
          <Typography variant="body2" fontWeight={600}>{t('elementBody.returnType')}</Typography>
          <Typography variant="body2">
            {formatReturnType(element.returnType)}
            {chain.length > 0 && (
              <>
                {' '}<ArrowIcon sx={{ fontSize: 14, verticalAlign: 'middle', mx: 0.5 }} />{' '}
                {formatReturnType(currentReturnType)}
              </>
            )}
          </Typography>
        </Box>

        {/* Current modifier chain */}
        {chain.length > 0 && (
          <>
            <Divider sx={{ my: 1.5 }}>
              <Chip label={t('elementBody.withModifiers')} size="small" color="primary" />
            </Divider>
            <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 1.5, flexWrap: 'wrap', gap: 0.5 }}>
              <BackIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              {chain.map((mod, i) => (
                <Box key={`${mod.id}-${i}`} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                  <Chip
                    label={mod.name}
                    size="small"
                    onDelete={() => onRemove(i)}
                    sx={{ fontWeight: 600 }}
                  />
                  {i < chain.length - 1 && (
                    <ArrowIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                  )}
                </Box>
              ))}
            </Stack>
          </>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Search filter */}
        {applicableModifiers.length > 5 && (
          <TextField
            fullWidth
            size="small"
            placeholder={t('elementBody.filterModifiers')}
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            sx={{ mb: 1 }}
          />
        )}

        {/* Modifier dropdown */}
        {applicableModifiers.length > 0 ? (
          <FormControl fullWidth size="small">
            <InputLabel>{t('elementBody.selectModifierLabel')}</InputLabel>
            <Select
              value={selectedModId}
              label={t('elementBody.selectModifierLabel')}
              onChange={(e) => setSelectedModId(e.target.value)}
            >
              {filteredModifiers.map((mod) => (
                <MenuItem key={mod.id} value={mod.id}>
                  <Box>
                    <Typography variant="body2">{mod.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatReturnType(mod.inputTypes[0])} → {formatReturnType(mod.returnType)}
                    </Typography>
                  </Box>
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
            {t('elementBody.noMoreModifiers')}
          </Typography>
        )}

        {selectedMod && (
          <Box sx={{ mt: 2, p: 2, backgroundColor: 'action.hover', borderRadius: 1, border: 1, borderColor: 'divider' }}>
            <Typography variant="subtitle2" gutterBottom>{selectedMod.name}</Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom
              dangerouslySetInnerHTML={{ __html: t('elementBody.modAccepts', { input: selectedMod.inputTypes.map(formatReturnType).join(', '), output: formatReturnType(selectedMod.returnType), interpolation: { escapeValue: true } }) }}
            />
            {selectedMod.cqlLibraryFunction && (
              <Typography variant="caption" sx={{ fontFamily: 'monospace', display: 'block', mt: 1, color: 'primary.main' }}>
                {t('elementBody.modCqlLabel', { fn: selectedMod.cqlLibraryFunction })}
              </Typography>
            )}
            {selectedMod.values && Object.keys(selectedMod.values).length > 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                {t('elementBody.modRequires', { fields: Object.keys(selectedMod.values).join(', ') })}
              </Typography>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('common:actions.close')}</Button>
        <Button
          variant="contained"
          onClick={handleAdd}
          disabled={!selectedMod}
        >
          {t('common:actions.add')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// ----- Field Renderer -----

function FieldRenderer({
  field,
  elementType,
  onChange,
  onFieldUpdate,
}: {
  field: ElementField
  elementType?: string
  onChange: (value: unknown) => void
  onFieldUpdate: (updates: Partial<ElementField>) => void
}) {
  const { t } = useTranslation('authoring')
  if (field.static) {
    return (
      <Typography variant="body2" color="text.secondary">
        {field.name}: {String(field.value || '')}
      </Typography>
    )
  }

  // Add guidance for element_name and comment fields
  const getFieldHints = (f: ElementField): { placeholder?: string; helperText?: string } => {
    if (f.id === 'element_name') {
      return {
        placeholder: t('elementBody.elementNamePlaceholder'),
        helperText: t('elementBody.elementNameHelper'),
      }
    }
    if (f.id === 'comment') {
      return {
        placeholder: t('elementBody.commentPlaceholder'),
        helperText: t('elementBody.commentHelper'),
      }
    }
    return {}
  }

  const hints = getFieldHints(field)

  switch (field.type) {
    case 'string':
      return (
        <StringField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
          placeholder={hints.placeholder}
          helperText={hints.helperText}
        />
      )
    case 'textarea':
      return (
        <TextAreaField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
          placeholder={hints.placeholder}
          helperText={hints.helperText}
        />
      )
    case 'number':
      return (
        <NumberField
          label={field.name}
          value={field.value as number | undefined}
          onChange={onChange}
        />
      )
    case 'valueset': {
      const selectOptions = field.select ? DEMOGRAPHIC_SELECT_OPTIONS[field.select] : undefined
      if (selectOptions) {
        return (
          <FormControl size="small" fullWidth>
            <InputLabel>{field.name}</InputLabel>
            <Select
              value={(field.value as string) || ''}
              label={field.name}
              onChange={(e) => onChange(e.target.value)}
            >
              {selectOptions.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        )
      }
      return (
        <ValueSetField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
          selectPath={field.select}
        />
      )
    }
    default:
      // VSAC-based types and others: show as value set field with rich editing
      if (field.type?.endsWith('_vsac')) {
        // Derive FHIR resource type from element type (e.g. GenericObservation_vsac -> Observation)
        const resourceType = elementType?.replace('Generic', '').replace('_vsac', '')
        return (
          <ValueSetField
            label={field.name}
            value={(field.value as string) || ''}
            onChange={onChange}
            selectPath={field.id}
            field={field}
            onFieldUpdate={onFieldUpdate}
            resourceType={resourceType}
          />
        )
      }
      return (
        <StringField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
        />
      )
  }
}

// ----- Helpers -----

function getEffectiveReturnType(element: ElementInstance): string {
  return getEffectiveRT(element.returnType, element.modifiers)
}

function formatReturnType(returnType: string): string {
  return returnType
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

/**
 * Validates a modifier chain after a removal. Keeps modifiers only while
 * input types remain compatible with the previous output type.
 */
function validateModifierChain(baseReturnType: string, modifiers: Modifier[]): Modifier[] {
  const result: Modifier[] = []
  let currentType = baseReturnType
  for (const mod of modifiers) {
    if (mod.inputTypes.includes(currentType)) {
      result.push(mod)
      currentType = mod.returnType
    }
    // skip incompatible modifiers silently (they were broken by the removal)
  }
  return result
}
