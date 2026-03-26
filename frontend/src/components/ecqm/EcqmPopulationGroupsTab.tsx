import { useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Accordion, AccordionDetails, AccordionSummary, Box, Button,
  IconButton, Stack, Typography,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon, Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { EcqmArtifact, PopulationGroup } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import EcqmPopulationGroupEditor from './EcqmPopulationGroupEditor'

interface Props {
  artifact: EcqmArtifact
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onUpdateGroups: (groups: PopulationGroup[]) => void
}

function newGroupId() {
  return 'group-' + Date.now().toString(36)
}

function createEmptyGroup(): PopulationGroup {
  return {
    groupId: newGroupId(),
    populations: {
      'initial-population': createEmptyConjunctionGroup() as ConjunctionGroupType,
      'denominator': createEmptyConjunctionGroup() as ConjunctionGroupType,
      'numerator': createEmptyConjunctionGroup() as ConjunctionGroupType,
      'denominator-exclusion': null,
      'denominator-exception': null,
      'numerator-exclusion': null,
      'measure-population': null,
      'measure-population-exclusion': null,
    },
    observations: [],
    stratifiers: [],
  }
}

export default function EcqmPopulationGroupsTab({ artifact, templates, modifiers, onUpdateGroups }: Props) {
  const { t } = useTranslation('ecqm')
  const groups = useMemo(() => artifact.populationGroups || [], [artifact.populationGroups])

  const addGroup = useCallback(() => {
    onUpdateGroups([...groups, createEmptyGroup()])
  }, [groups, onUpdateGroups])

  const removeGroup = useCallback((idx: number) => {
    const updated = [...groups]
    updated.splice(idx, 1)
    onUpdateGroups(updated)
  }, [groups, onUpdateGroups])

  const updateGroup = useCallback((idx: number, updated: PopulationGroup) => {
    const copy = [...groups]
    copy[idx] = updated
    onUpdateGroups(copy)
  }, [groups, onUpdateGroups])

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">{t('populationGroups.title')}</Typography>
        <Button startIcon={<AddIcon />} onClick={addGroup}>{t('populationGroups.addGroup')}</Button>
      </Stack>

      {groups.length === 0 && (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          {t('populationGroups.emptyState')}
        </Typography>
      )}

      {groups.map((group, idx) => (
        <Accordion key={group.groupId} defaultExpanded={idx === 0}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ flex: 1 }}>
              <Typography fontWeight={600}>
                {t('populationGroups.groupLabel', { number: idx + 1 })}{group.description ? `: ${group.description}` : ''}
              </Typography>
            </Stack>
            {groups.length > 1 && (
              <IconButton
                size="small" color="error"
                onClick={(e) => { e.stopPropagation(); removeGroup(idx) }}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            )}
          </AccordionSummary>
          <AccordionDetails>
            <EcqmPopulationGroupEditor
              group={group}
              scoringType={artifact.scoringType}
              populationBasis={artifact.populationBasis}
              templates={templates}
              modifiers={modifiers}
              onChange={(updated) => updateGroup(idx, updated)}
            />
          </AccordionDetails>
        </Accordion>
      ))}
    </Box>
  )
}
