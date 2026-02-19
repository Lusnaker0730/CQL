import { useState, useCallback, useMemo, memo } from 'react'
import { Box, Typography, Stack, TextField, InputAdornment, Chip } from '@mui/material'
import { FilterList as FilterIcon, RemoveCircleOutline as ExcludeIcon, Search as SearchIcon, Clear as ClearIcon } from '@mui/icons-material'
import ConjunctionTypeSelect from './ConjunctionTypeSelect'
import ArtifactElement from './ArtifactElement'
import ElementSelect from '../element-select/ElementSelect'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'
import type { DynamicEntry } from '../element-select/ElementSelectDropdown'
import { generateId } from '../../../utils/validation'
import { CONJUNCTION_COLOR_AND, CONJUNCTION_COLOR_OR } from '../../../constants/authoringConstants'

function elementMatchesFilter(element: ElementInstance, term: string): boolean {
  const name = element.fields?.find((f) => f.id === 'element_name')?.value as string
  const displayName = name || element.name
  if (displayName?.toLowerCase().includes(term)) return true
  if (element.type?.toLowerCase().includes(term)) return true
  if (element.returnType?.toLowerCase().replace(/_/g, ' ').includes(term)) return true
  if (element.conjunction && element.childInstances) {
    return element.childInstances.some((child) => elementMatchesFilter(child, term))
  }
  return false
}

interface ConjunctionGroupProps {
  group: ConjunctionGroupType
  treeName: string
  depth?: number
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  dynamicEntries?: DynamicEntry[]
  searchFilter?: string
  onUpdateGroup: (updated: ConjunctionGroupType) => void
  onAddElement: (element: ElementInstance) => void
  onRemoveElement: (uniqueId: string) => void
  onUpdateElement: (uniqueId: string, updated: Partial<ElementInstance>) => void
  onOutdentElement?: (element: ElementInstance) => void
}

const ConjunctionGroup = memo(function ConjunctionGroup({
  group,
  treeName,
  depth = 0,
  templates,
  modifiers,
  dynamicEntries,
  searchFilter,
  onUpdateGroup,
  onAddElement,
  onRemoveElement,
  onUpdateElement,
  onOutdentElement,
}: ConjunctionGroupProps) {
  const [localSearch, setLocalSearch] = useState('')

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

  const handleIndent = useCallback(
    (uniqueId: string) => {
      const idx = group.childInstances.findIndex((c) => c.uniqueId === uniqueId)
      if (idx === -1) return
      const element = group.childInstances[idx]
      const newGroup: ElementInstance = {
        uniqueId: generateId(),
        type: 'And',
        name: 'And',
        conjunction: true,
        returnType: 'boolean',
        fields: [],
        modifiers: [],
        childInstances: [element],
      }
      const newChildren = [...group.childInstances]
      newChildren[idx] = newGroup
      onUpdateGroup({ ...group, childInstances: newChildren })
    },
    [group, onUpdateGroup]
  )

  const handleOutdentFromChild = useCallback(
    (element: ElementInstance, childGroupUniqueId: string) => {
      const groupIdx = group.childInstances.findIndex((c) => c.uniqueId === childGroupUniqueId)
      if (groupIdx === -1) return
      const childGroup = group.childInstances[groupIdx]
      const updatedChildInstances = (childGroup.childInstances || []).filter((c) => c.uniqueId !== element.uniqueId)

      const newChildren = [...group.childInstances]
      if (updatedChildInstances.length === 0) {
        // Replace the now-empty group with the element
        newChildren.splice(groupIdx, 1, element)
      } else {
        newChildren[groupIdx] = { ...childGroup, childInstances: updatedChildInstances }
        newChildren.splice(groupIdx + 1, 0, element)
      }
      onUpdateGroup({ ...group, childInstances: newChildren })
    },
    [group, onUpdateGroup]
  )

  const isRoot = depth === 0
  const conjunctionLabel = group.id === 'Or' ? 'Or' : 'And'
  const borderColor = group.id === 'Or' ? CONJUNCTION_COLOR_OR : CONJUNCTION_COLOR_AND

  const activeFilter = isRoot ? localSearch.trim().toLowerCase() : (searchFilter || '')
  const filteredChildren = useMemo(() => {
    if (!activeFilter) return group.childInstances
    return group.childInstances.filter((child) => elementMatchesFilter(child, activeFilter))
  }, [group.childInstances, activeFilter])

  const isFiltering = activeFilter.length > 0
  const hiddenCount = group.childInstances.length - filteredChildren.length

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

      {/* Search bar — root level only, when there are 3+ elements */}
      {isRoot && group.childInstances.length >= 3 && (
        <TextField
          size="small"
          placeholder="Filter elements by name, type..."
          value={localSearch}
          onChange={(e) => setLocalSearch(e.target.value)}
          fullWidth
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" sx={{ opacity: 0.5 }} />
              </InputAdornment>
            ),
            endAdornment: localSearch ? (
              <InputAdornment position="end">
                <ClearIcon
                  fontSize="small"
                  sx={{ cursor: 'pointer', opacity: 0.5, '&:hover': { opacity: 1 } }}
                  onClick={() => setLocalSearch('')}
                />
              </InputAdornment>
            ) : null,
          }}
          sx={{ mb: 1.5 }}
        />
      )}

      {/* Filter result indicator */}
      {isFiltering && hiddenCount > 0 && (
        <Chip
          label={`${filteredChildren.length} of ${group.childInstances.length} elements shown`}
          size="small"
          variant="outlined"
          sx={{ mb: 1, fontSize: '0.75rem' }}
        />
      )}

      {/* Children */}
      {group.childInstances.length === 0 ? (
        <Box
          sx={{
            py: 4,
            px: 3,
            textAlign: 'center',
            border: '2px dashed',
            borderColor: 'divider',
            borderRadius: 2,
            mb: 2,
            backgroundColor: 'action.hover',
          }}
        >
          {isRoot && (
            treeName === 'Inclusions' ? (
              <FilterIcon sx={{ fontSize: 40, color: 'primary.main', opacity: 0.5, mb: 1 }} />
            ) : treeName === 'Exclusions' ? (
              <ExcludeIcon sx={{ fontSize: 40, color: 'warning.main', opacity: 0.5, mb: 1 }} />
            ) : null
          )}
          <Typography variant="body2" color="text.secondary" fontWeight={500} mb={0.5}>
            No elements in {treeName.toLowerCase()}
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
            {treeName === 'Inclusions'
              ? 'Define who this artifact applies to. Add demographics, conditions, observations, or other criteria.'
              : treeName === 'Exclusions'
                ? 'Define who should be excluded. Patients matching these criteria will be removed from the population.'
                : 'Click "Add Element" below to start building logic for this group.'}
          </Typography>
        </Box>
      ) : filteredChildren.length === 0 ? (
        <Box sx={{ py: 2, textAlign: 'center', mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            No elements match "{isRoot ? localSearch : searchFilter}"
          </Typography>
        </Box>
      ) : (
        <Stack spacing={1} mb={2}>
          {filteredChildren.map((child, index) => (
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
                  dynamicEntries={dynamicEntries}
                  searchFilter={activeFilter}
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
                  onOutdentElement={(element) => handleOutdentFromChild(element, child.uniqueId)}
                />
              ) : (
                <ArtifactElement
                  element={child}
                  modifiers={modifiers}
                  onUpdate={(updates) => onUpdateElement(child.uniqueId, updates)}
                  onRemove={() => onRemoveElement(child.uniqueId)}
                  onIndent={() => handleIndent(child.uniqueId)}
                  onOutdent={onOutdentElement ? () => onOutdentElement(child) : undefined}
                />
              )}
              {index < filteredChildren.length - 1 && (
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
        dynamicEntries={dynamicEntries}
        onSelect={handleAddElement}
      />
    </Box>
  )
})

export default ConjunctionGroup
