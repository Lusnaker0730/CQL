import { Box, Stack, Typography, Chip, Divider } from '@mui/material'
import StringField from '../fields/StringField'
import NumberField from '../fields/NumberField'
import TextAreaField from '../fields/TextAreaField'
import ValueSetField from '../fields/ValueSetField'
import ModifierCard from './ModifierCard'
import type { ElementInstance, ElementField, Modifier, ModifierDefinition } from '../../../types/authoring'

interface ArtifactElementBodyProps {
  element: ElementInstance
  modifiers: ModifierDefinition[]
  onUpdate: (updates: Partial<ElementInstance>) => void
}

export default function ArtifactElementBody({
  element,
  modifiers: allModifiers,
  onUpdate,
}: ArtifactElementBodyProps) {
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
    onUpdate({ modifiers: updated })
  }

  const handleUpdateModifier = (index: number, values: Record<string, unknown>) => {
    const updated = [...(element.modifiers || [])]
    updated[index] = { ...updated[index], values }
    onUpdate({ modifiers: updated })
  }

  return (
    <Box>
      {/* Element Fields */}
      <Stack spacing={2} mb={2}>
        {(element.fields || []).map((field) => (
          <FieldRenderer
            key={field.id}
            field={field}
            onChange={(value) => handleFieldChange(field.id, value)}
          />
        ))}
      </Stack>

      {/* Modifiers */}
      {element.modifiers && element.modifiers.length > 0 && (
        <>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" color="text.secondary" mb={1}>
            Modifiers
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

      {/* Add Modifier Button */}
      {!(element as ElementInstance & { cannotHaveModifiers?: boolean }).cannotHaveModifiers && applicableModifiers.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <ModifierPicker
            applicableModifiers={applicableModifiers}
            onSelect={handleAddModifier}
          />
        </Box>
      )}
    </Box>
  )
}

function FieldRenderer({
  field,
  onChange,
}: {
  field: ElementField
  onChange: (value: unknown) => void
}) {
  if (field.static) {
    return (
      <Typography variant="body2" color="text.secondary">
        {field.name}: {String(field.value || '')}
      </Typography>
    )
  }

  switch (field.type) {
    case 'string':
      return (
        <StringField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
        />
      )
    case 'textarea':
      return (
        <TextAreaField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
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
    case 'valueset':
      return (
        <ValueSetField
          label={field.name}
          value={(field.value as string) || ''}
          onChange={onChange}
          selectPath={field.id}
        />
      )
    default:
      // VSAC-based types and others: show as value set field
      if (field.type?.endsWith('_vsac')) {
        return (
          <ValueSetField
            label={field.name}
            value={(field.value as string) || ''}
            onChange={onChange}
            selectPath={field.id}
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

function ModifierPicker({
  applicableModifiers,
  onSelect,
}: {
  applicableModifiers: ModifierDefinition[]
  onSelect: (mod: ModifierDefinition) => void
}) {
  return (
    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
      <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center', mr: 0.5 }}>
        Add modifier:
      </Typography>
      {applicableModifiers.slice(0, 8).map((mod) => (
        <Chip
          key={mod.id}
          label={mod.name}
          size="small"
          variant="outlined"
          onClick={() => onSelect(mod)}
          sx={{ cursor: 'pointer', fontSize: '0.75rem' }}
        />
      ))}
      {applicableModifiers.length > 8 && (
        <Chip
          label={`+${applicableModifiers.length - 8} more`}
          size="small"
          variant="outlined"
          sx={{ fontSize: '0.75rem' }}
        />
      )}
    </Stack>
  )
}

function getEffectiveReturnType(element: ElementInstance): string {
  if (element.modifiers && element.modifiers.length > 0) {
    return element.modifiers[element.modifiers.length - 1].returnType
  }
  return element.returnType
}
