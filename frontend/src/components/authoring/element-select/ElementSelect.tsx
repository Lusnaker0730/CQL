import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Popover } from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import ElementSelectDropdown from './ElementSelectDropdown'
import type { DynamicEntry } from './ElementSelectDropdown'
import type { FormTemplateCategory, FormTemplate, ElementInstance } from '../../../types/authoring'
import { generateId } from '../../../utils/validation'

interface ElementSelectProps {
  templates: FormTemplateCategory[]
  dynamicEntries?: DynamicEntry[]
  onSelect: (element: ElementInstance) => void
}

export default function ElementSelect({ templates, dynamicEntries, onSelect }: ElementSelectProps) {
  const { t } = useTranslation('authoring')
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null)

  const handleSelect = (template: FormTemplate) => {
    const newElement: ElementInstance = {
      uniqueId: generateId(),
      type: template.id,
      name: template.name,
      returnType: template.returnType,
      fields: (template.fields || []).map((f) => ({
        id: f.id,
        type: f.type,
        name: f.name,
        value: f.value ?? undefined,
        static: f.static,
      })),
      modifiers: [],
      suppressedModifiers: template.suppressedModifiers,
      conjunction: template.conjunction,
      childInstances: template.conjunction ? [] : undefined,
    }
    onSelect(newElement)
    setAnchorEl(null)
  }

  const handleSelectDynamic = (entry: DynamicEntry) => {
    const newElement: ElementInstance = {
      uniqueId: generateId(),
      type: entry.sourceType === 'baseElement' ? 'baseElementRef'
        : entry.sourceType === 'parameter' ? 'parameterRef'
        : 'externalCqlRef',
      name: entry.name,
      returnType: entry.returnType,
      fields: [
        { id: 'element_name', type: 'string', name: 'Element Name', value: entry.name },
        { id: 'reference_id', type: 'string', name: 'Reference ID', value: entry.sourceId, static: true },
        ...(entry.libraryName
          ? [{ id: 'library_name', type: 'string', name: 'Library', value: entry.libraryName, static: true }]
          : []),
      ],
      modifiers: [],
    }
    onSelect(newElement)
    setAnchorEl(null)
  }

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        startIcon={<AddIcon />}
        onClick={(e) => setAnchorEl(e.currentTarget)}
      >
        {t('elementSelect.addElement')}
      </Button>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        <ElementSelectDropdown
          templates={templates}
          dynamicEntries={dynamicEntries}
          onSelect={handleSelect}
          onSelectDynamic={handleSelectDynamic}
        />
      </Popover>
    </>
  )
}
