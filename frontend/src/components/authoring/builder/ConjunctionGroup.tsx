import { useState, useCallback, useMemo, memo } from 'react'
import { Box, Typography, Stack, TextField, InputAdornment, Chip, Button, Tooltip } from '@mui/material'
import { FilterList as FilterIcon, RemoveCircleOutline as ExcludeIcon, Search as SearchIcon, Clear as ClearIcon, AccountTree as SubGroupIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import ConjunctionConnector from './ConjunctionConnector'
import ArtifactElement from './ArtifactElement'
import ElementSelect from '../element-select/ElementSelect'
import type { ConjunctionGroup as ConjunctionGroupType, ElementInstance, FormTemplateCategory, ModifierDefinition, ConjunctionKind } from '../../../types/authoring'
import { elementToConjunctionGroup, createConjunctionElement } from '../../../types/authoring'
import type { DynamicEntry } from '../element-select/ElementSelectDropdown'
import { generateId } from '../../../utils/validation'
import { changeConnectorAt, addSubGroup, resolveKind, nextConjunction, conjColor } from '../../../utils/conjunctionTreeUtils'

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
  twcoreMode?: boolean
  hideElementName?: boolean
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
  twcoreMode,
  hideElementName,
  searchFilter,
  onUpdateGroup,
  onAddElement,
  onRemoveElement,
  onUpdateElement,
  onOutdentElement,
}: ConjunctionGroupProps) {
  const { t } = useTranslation('authoring')
  const [localSearch, setLocalSearch] = useState('')

  const isRoot = depth === 0
  const conjunctionId: ConjunctionKind = resolveKind(group.id)
  const borderColor = conjColor(conjunctionId)

  const activeFilter = isRoot ? localSearch.trim().toLowerCase() : (searchFilter || '')
  const filteredChildren = useMemo(() => {
    if (!activeFilter) return group.childInstances
    return group.childInstances.filter((child) => elementMatchesFilter(child, activeFilter))
  }, [group.childInstances, activeFilter])

  const isFiltering = activeFilter.length > 0
  const hiddenCount = group.childInstances.length - filteredChildren.length

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

  const handleIndent = useCallback(
    (uniqueId: string) => {
      const idx = group.childInstances.findIndex((c) => c.uniqueId === uniqueId)
      if (idx === -1) return
      const element = group.childInstances[idx]
      const newChildren = [...group.childInstances]
      newChildren[idx] = createConjunctionElement('And', [element], generateId())
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
        newChildren.splice(groupIdx, 1, element)
      } else {
        newChildren[groupIdx] = { ...childGroup, childInstances: updatedChildInstances }
        newChildren.splice(groupIdx + 1, 0, element)
      }
      onUpdateGroup({ ...group, childInstances: newChildren })
    },
    [group, onUpdateGroup]
  )

  const handleConnectorChange = useCallback(
    (filteredIndex: number, newConj: ConjunctionKind) => {
      // Map filtered index → real index via uniqueId lookup against current children
      const leftUniqueId = filteredChildren[filteredIndex]?.uniqueId
      if (!leftUniqueId) return
      const realIndex = group.childInstances.findIndex((c) => c.uniqueId === leftUniqueId)
      if (realIndex === -1) return

      onUpdateGroup(changeConnectorAt(group, realIndex, newConj))
    },
    [group, filteredChildren, onUpdateGroup]
  )

  const handleAddSubGroup = useCallback(() => {
    // Default to opposite boolean conjunction; Union/Intersect users can toggle via badge
    const oppositeConj: ConjunctionKind = conjunctionId === 'And' ? 'Or' : 'And'
    onUpdateGroup(addSubGroup(group, oppositeConj))
  }, [group, conjunctionId, onUpdateGroup])

  return (
    <Box
      sx={{
        border: isRoot ? 'none' : `2px solid ${borderColor}`,
        borderRadius: isRoot ? 0 : 2,
        p: isRoot ? 0 : 2,
        ml: isRoot ? 0 : 2,
        position: 'relative',
        backgroundColor: isRoot ? 'transparent' : `${borderColor}08`,
      }}
    >
      {/* Nested group: clickable badge chip to toggle conjunction type */}
      {!isRoot && (
        <Stack direction="row" alignItems="center" spacing={1} mb={1}>
          <Tooltip title={t(`conjunction.clickToChange${nextConjunction(conjunctionId)}`)}>
            <Chip
              label={conjunctionId.toUpperCase()}
              size="small"
              onClick={() => handleConjunctionChange(nextConjunction(conjunctionId))}
              sx={{
                height: 22,
                fontSize: '0.7rem',
                fontWeight: 700,
                backgroundColor: borderColor,
                color: 'white',
                cursor: 'pointer',
                '& .MuiChip-label': { px: 1 },
                '&:hover': { opacity: 0.85 },
              }}
            />
          </Tooltip>
          <Typography variant="caption" color="text.secondary">
            {t('conjunction.elementCount', { count: group.childInstances.length })}
          </Typography>
        </Stack>
      )}

      {/* Search bar — root level only, when there are 3+ elements */}
      {isRoot && group.childInstances.length >= 3 && (
        <TextField
          size="small"
          placeholder={t('conjunction.filterPlaceholder')}
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
          label={t('conjunction.filterResult', { shown: filteredChildren.length, total: group.childInstances.length })}
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
            {t('conjunction.noElements', { treeName: treeName.toLowerCase() })}
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
            {treeName === 'Inclusions'
              ? t('conjunction.inclusionHint')
              : treeName === 'Exclusions'
                ? t('conjunction.exclusionHint')
                : t('conjunction.genericHint')}
          </Typography>
        </Box>
      ) : filteredChildren.length === 0 ? (
        <Box sx={{ py: 2, textAlign: 'center', mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {t('conjunction.noFilterMatch', { filter: isRoot ? localSearch : searchFilter })}
          </Typography>
        </Box>
      ) : (
        <Stack spacing={1} mb={2}>
          {filteredChildren.map((child, index) => (
            <Box key={child.uniqueId}>
              {child.conjunction ? (
                <ConjunctionGroup
                  group={elementToConjunctionGroup(child)}
                  treeName={child.name}
                  depth={depth + 1}
                  templates={templates}
                  modifiers={modifiers}
                  dynamicEntries={dynamicEntries}
                  hideElementName={hideElementName}
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
                  hideElementName={hideElementName}
                  onUpdate={(updates) => onUpdateElement(child.uniqueId, updates)}
                  onRemove={() => onRemoveElement(child.uniqueId)}
                  onIndent={() => handleIndent(child.uniqueId)}
                  onOutdent={onOutdentElement ? () => onOutdentElement(child) : undefined}
                />
              )}
              {index < filteredChildren.length - 1 && (
                <ConjunctionConnector
                  value={conjunctionId}
                  onChange={(newConj) => handleConnectorChange(index, newConj)}
                />
              )}
            </Box>
          ))}
        </Stack>
      )}

      {/* Add Element + Add Sub-Group */}
      <Stack direction="row" spacing={1} alignItems="center">
        <ElementSelect
          templates={templates}
          dynamicEntries={dynamicEntries}
          twcoreMode={twcoreMode}
          onSelect={onAddElement}
        />
        <Button
          size="small"
          variant="outlined"
          startIcon={<SubGroupIcon />}
          onClick={handleAddSubGroup}
          sx={{ textTransform: 'none', fontSize: '0.8rem', whiteSpace: 'nowrap' }}
        >
          {t('conjunction.addSubGroup')}
        </Button>
      </Stack>
    </Box>
  )
})

export default ConjunctionGroup
