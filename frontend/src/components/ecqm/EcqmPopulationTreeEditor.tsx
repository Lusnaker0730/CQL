import { memo, useCallback } from 'react'
import { Box, Typography, Paper } from '@mui/material'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import ConjunctionGroup from '../authoring/builder/ConjunctionGroup'

interface Props {
  label: string
  required?: boolean
  tree: ConjunctionGroupType
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onUpdateTree: (updated: ConjunctionGroupType) => void
}

const EcqmPopulationTreeEditor = memo(function EcqmPopulationTreeEditor({
  label, required, tree, templates, modifiers, onUpdateTree,
}: Props) {
  const handleUpdateGroup = useCallback((updated: ConjunctionGroupType) => {
    onUpdateTree(updated)
  }, [onUpdateTree])

  const handleAddElement = useCallback((element: ElementInstance) => {
    onUpdateTree({
      ...tree,
      childInstances: [...tree.childInstances, element],
    })
  }, [tree, onUpdateTree])

  const handleRemoveElement = useCallback((uniqueId: string) => {
    onUpdateTree({
      ...tree,
      childInstances: tree.childInstances.filter((c) => c.uniqueId !== uniqueId),
    })
  }, [tree, onUpdateTree])

  const handleUpdateElement = useCallback((uniqueId: string, updated: Partial<ElementInstance>) => {
    onUpdateTree({
      ...tree,
      childInstances: tree.childInstances.map((c) =>
        c.uniqueId === uniqueId ? { ...c, ...updated } : c
      ),
    })
  }, [tree, onUpdateTree])

  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
        {label} {required && <Typography component="span" color="error">*</Typography>}
      </Typography>
      <Box sx={{ ml: 1 }}>
        <ConjunctionGroup
          group={tree}
          treeName={label.replace(/\s/g, '')}
          templates={templates}
          modifiers={modifiers}
          onUpdateGroup={handleUpdateGroup}
          onAddElement={handleAddElement}
          onRemoveElement={handleRemoveElement}
          onUpdateElement={handleUpdateElement}
        />
      </Box>
    </Paper>
  )
})

export default EcqmPopulationTreeEditor
