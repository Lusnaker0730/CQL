import { useState, useCallback, useMemo, memo } from 'react'
import { Box, Typography, Stack, TextField, InputAdornment, Chip } from '@mui/material'
import { FilterList as FilterIcon, RemoveCircleOutline as ExcludeIcon, Search as SearchIcon, Clear as ClearIcon } from '@mui/icons-material'
import ConjunctionTypeSelect from './ConjunctionTypeSelect'
import ArtifactElement from './ArtifactElement'
import ElementSelect from '../element-select/ElementSelect'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'

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
  searchFilter?: string
  onUpdateGroup: (updated: ConjunctionGroupType) => void
  onAddElement: (element: ElementInstance) => void
  onRemoveElement: (uniqueId: string) => void
  onUpdateElement: (uniqueId: string, updated: Partial<ElementInstance>) => void
}

const ConjunctionGroup = memo(function ConjunctionGroup({
  group,
  treeName,
  depth = 0,
  templates,
  modifiers,
  searchFilter,
  onUpdateGroup,
  onAddElement,
  onRemoveElement,
  onUpdateElement,
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

  const isRoot = depth === 0
  const conjunctionLabel = group.id === 'Or' ? 'Or' : 'And'
  const borderColor = group.id === 'Or' ? '#E67E22' : '#0D7377'

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
                />
              ) : (
                <ArtifactElement
                  element={child}
                  modifiers={modifiers}
                  onUpdate={(updates) => onUpdateElement(child.uniqueId, updates)}
                  onRemove={() => onRemoveElement(child.uniqueId)}
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
        onSelect={handleAddElement}
      />
    </Box>
  )
})

export default ConjunctionGroup
