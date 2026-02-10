import { useCallback } from 'react'
import { Box, Typography, Stack } from '@mui/material'
import ConjunctionTypeSelect from './ConjunctionTypeSelect'
import ArtifactElement from './ArtifactElement'
import ElementSelect from '../element-select/ElementSelect'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'

interface ConjunctionGroupProps {
  group: ConjunctionGroupType
  treeName: string
  depth?: number
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onUpdateGroup: (updated: ConjunctionGroupType) => void
  onAddElement: (element: ElementInstance) => void
  onRemoveElement: (uniqueId: string) => void
  onUpdateElement: (uniqueId: string, updated: Partial<ElementInstance>) => void
}

export default function ConjunctionGroup({
  group,
  treeName,
  depth = 0,
  templates,
  modifiers,
  onUpdateGroup,
  onAddElement,
  onRemoveElement,
  onUpdateElement,
}: ConjunctionGroupProps) {
  const handleConjunctionChange = useCallback(
    (conjType: string) => {
      onUpdateGroup({
        ...group,
        id: conjType,
        name: conjType,
      })
    },
    [group, onUpdateGroup]
  )

  const handleAddElement = useCallback(
    (element: ElementInstance) => {
      onAddElement(element)
    },
    [onAddElement]
  )

  const isRoot = depth === 0
  const conjunctionLabel = group.id === 'Or' ? 'Or' : 'And'
  const borderColor = group.id === 'Or' ? '#E67E22' : '#0D7377'

  return (
    <Box
      sx={{
        border: isRoot ? 'none' : `2px solid ${borderColor}`,
        borderRadius: isRoot ? 0 : 2,
        p: isRoot ? 0 : 2,
        ml: isRoot ? 0 : 2,
        position: 'relative',
        backgroundColor: isRoot ? 'transparent' : 'action.hover',
      }}
    >
      {/* Header */}
      <Stack direction="row" alignItems="center" spacing={1} mb={1}>
        <ConjunctionTypeSelect
          value={conjunctionLabel}
          onChange={handleConjunctionChange}
        />
        {group.childInstances.length > 0 && (
          <Typography variant="caption" color="text.secondary">
            {group.childInstances.length} element{group.childInstances.length !== 1 ? 's' : ''}
          </Typography>
        )}
      </Stack>

      {/* Children */}
      {group.childInstances.length === 0 ? (
        <Box
          sx={{
            py: 4,
            px: 2,
            textAlign: 'center',
            border: '2px dashed',
            borderColor: 'divider',
            borderRadius: 2,
            mb: 2,
          }}
        >
          <Typography variant="body2" color="text.secondary" mb={1}>
            No elements added to {treeName.toLowerCase()} yet.
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Use the &quot;Add Element&quot; button below to start building your logic.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={1} mb={2}>
          {group.childInstances.map((child, index) => (
            <Box key={child.uniqueId}>
              {child.conjunction ? (
                <ConjunctionGroup
                  group={{
                    id: child.type || child.name,
                    name: child.name,
                    conjunction: true,
                    returnType: child.returnType,
                    childInstances: child.childInstances || [],
                  }}
                  treeName={child.name}
                  depth={depth + 1}
                  templates={templates}
                  modifiers={modifiers}
                  onUpdateGroup={(updated) =>
                    onUpdateElement(child.uniqueId, {
                      childInstances: updated.childInstances,
                      type: updated.id,
                      name: updated.name,
                    })
                  }
                  onAddElement={(el) => {
                    const updatedChildren = [...(child.childInstances || []), el]
                    onUpdateElement(child.uniqueId, { childInstances: updatedChildren })
                  }}
                  onRemoveElement={(uid) => {
                    const updatedChildren = (child.childInstances || []).filter((c) => c.uniqueId !== uid)
                    onUpdateElement(child.uniqueId, { childInstances: updatedChildren })
                  }}
                  onUpdateElement={(uid, updates) => {
                    const updatedChildren = (child.childInstances || []).map((c) =>
                      c.uniqueId === uid ? { ...c, ...updates } : c
                    )
                    onUpdateElement(child.uniqueId, { childInstances: updatedChildren })
                  }}
                />
              ) : (
                <ArtifactElement
                  element={child}
                  modifiers={modifiers}
                  onUpdate={(updates) => onUpdateElement(child.uniqueId, updates)}
                  onRemove={() => onRemoveElement(child.uniqueId)}
                />
              )}
              {index < group.childInstances.length - 1 && (
                <Box sx={{ textAlign: 'center', py: 0.5 }}>
                  <Typography
                    variant="caption"
                    sx={{
                      px: 1.5,
                      py: 0.25,
                      borderRadius: 1,
                      backgroundColor: borderColor,
                      color: 'white',
                      fontWeight: 600,
                      fontSize: '0.7rem',
                    }}
                  >
                    {conjunctionLabel.toUpperCase()}
                  </Typography>
                </Box>
              )}
            </Box>
          ))}
        </Stack>
      )}

      {/* Add Element */}
      <ElementSelect
        templates={templates}
        onSelect={handleAddElement}
      />
    </Box>
  )
}
