import { memo, useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, Stack, Typography, Paper } from '@mui/material'
import { LibraryBooks as LibraryIcon } from '@mui/icons-material'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import ConjunctionGroup from '../authoring/builder/ConjunctionGroup'
import LibraryDefinitionPicker, { type LibraryDefinitionReference } from '../cql-libraries/LibraryDefinitionPicker'

interface Props {
  label: string
  required?: boolean
  tree: ConjunctionGroupType
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onUpdateTree: (updated: ConjunctionGroupType) => void
  onLibraryDefinitionSelected?: (reference: LibraryDefinitionReference) => void
}

const EcqmPopulationTreeEditor = memo(function EcqmPopulationTreeEditor({
  label, required, tree, templates, modifiers, onUpdateTree, onLibraryDefinitionSelected,
}: Props) {
  const { t } = useTranslation('cqlLibraries')
  const [pickerOpen, setPickerOpen] = useState(false)

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

  const handleLibrarySelect = useCallback((reference: LibraryDefinitionReference) => {
    // Create an element instance representing the library definition reference
    const element: ElementInstance = {
      uniqueId: `libref-${Date.now().toString(36)}`,
      type: 'externalCqlElement',
      name: `${reference.alias}."${reference.definitionName}"`,
      returnType: 'boolean',
      fields: [
        { id: 'libraryName', type: 'string', name: 'Library Name', value: reference.libraryName, static: true },
        { id: 'libraryVersion', type: 'string', name: 'Library Version', value: reference.libraryVersion, static: true },
        { id: 'definitionName', type: 'string', name: 'Definition Name', value: reference.definitionName, static: true },
        { id: 'alias', type: 'string', name: 'Alias', value: reference.alias, static: true },
      ],
      modifiers: [],
    }
    onUpdateTree({
      ...tree,
      childInstances: [...tree.childInstances, element],
    })
    onLibraryDefinitionSelected?.(reference)
    setPickerOpen(false)
  }, [tree, onUpdateTree, onLibraryDefinitionSelected])

  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="subtitle2" fontWeight={600}>
          {label} {required && <Typography component="span" color="error">*</Typography>}
        </Typography>
        <Button
          size="small"
          startIcon={<LibraryIcon />}
          onClick={() => setPickerOpen(true)}
          sx={{ textTransform: 'none' }}
        >
          {t('picker.useLibraryDefinition')}
        </Button>
      </Stack>
      <Box sx={{ ml: 1 }}>
        <ConjunctionGroup
          group={tree}
          treeName={label.replace(/\s/g, '')}
          templates={templates}
          modifiers={modifiers}
          onUpdateGroup={onUpdateTree}
          onAddElement={handleAddElement}
          onRemoveElement={handleRemoveElement}
          onUpdateElement={handleUpdateElement}
        />
      </Box>
      <LibraryDefinitionPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={handleLibrarySelect}
      />
    </Paper>
  )
})

export default EcqmPopulationTreeEditor
