import { useState, useMemo, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  MenuItem,
  Alert,
  IconButton,
  Paper,
  Divider,
  Chip,
  Menu,
  Checkbox,
  ListItemText,
  Tabs,
  Tab,
  Grid,
  Tooltip,
  LinearProgress,
  List,
  ListItemButton,
  ListSubheader,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Save as SaveIcon,
  CheckCircle as CheckCircleIcon,
  RadioButtonUnchecked as UncheckedIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import UcumUnitField from '../common/UcumUnitField'
import PopulationCard from './PopulationCard'
import type { CqlExpression } from './PopulationCard'
import RiskAdjustmentSection from './RiskAdjustmentSection'
import SupplementalDataSection from './SupplementalDataSection'
import ObservationSection from './ObservationSection'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { helpContent } from '../../constants/helpContent'
import {
  SCORING_TEMPLATES,
  POPULATION_BASIS_OPTIONS,
  createPopulationsFromTemplate,
  autoMapExpressions,
  getPopulationTypesForScoring,
  OBSERVATION_REQUIRED_SCORING,
} from '../../constants/populationConfig'
import type { MeasureDefinition, GroupDefinition, PopulationDefinition, StratifierDefinition } from '../../types'
import { SCORING_TYPE } from '../../constants/measureConstants'
import { ALERT_DISMISS_ERROR_MS } from '../../constants/timing'

interface PopulationCriteriaTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
  readOnly?: boolean
}

interface ValidationMessage {
  type: 'error' | 'warning'
  message: string
}

function createGroupFromScoring(index: number, scoringType: string): GroupDefinition {
  return {
    groupId: `group-${index + 1}`,
    description: '',
    populations: createPopulationsFromTemplate(scoringType),
    stratifiers: [],
  }
}

const EXCLUSION_POPULATION_TYPES = new Set([
  'denominator-exclusion', 'denominator-exception',
  'numerator-exclusion', 'measure-population-exclusion',
])

export default function PopulationCriteriaTab({ measure, onMeasureUpdate, readOnly }: PopulationCriteriaTabProps) {
  const { t } = useTranslation('measures')
  const popLabel = (type: string) => t(`populationCard.types.${type}`, type)
  const queryClient = useQueryClient()
  const [subTab, setSubTab] = useState(0)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [groups, setGroups] = useState<GroupDefinition[]>(
    measure.groupDefinitions?.length
      ? measure.groupDefinitions
      : [createGroupFromScoring(0, measure.scoringType)]
  )
  const [isDirty, setIsDirty] = useState(false)
  const [riskAdjustments, setRiskAdjustments] = useState(measure.riskAdjustments || [])
  const [supplementalData, setSupplementalData] = useState(measure.supplementalData || [])
  const [improvementNotation, setImprovementNotation] = useState(measure.improvementNotation || '')
  const [rateAggregation, setRateAggregation] = useState(measure.rateAggregation || '')
  const prevScoringRef = useRef(measure.scoringType)
  const [scoringChanged, setScoringChanged] = useState(false)
  const [addMenuAnchor, setAddMenuAnchor] = useState<{ el: HTMLElement; groupIdx: number } | null>(null)
  const [autoMapAlert, setAutoMapAlert] = useState<string | null>(null)
  const autoMapDoneRef = useRef(false)

  useUnsavedChangesGuard(isDirty)

  // Detect scoring type changes
  useEffect(() => {
    if (measure.scoringType !== prevScoringRef.current) {
      const allEmpty = groups.every((g) =>
        (g.populations || []).every((p) => !p.criteriaExpression)
      )
      if (allEmpty) {
        // Auto-retemplate when no expressions are assigned
        setGroups((prev) =>
          prev.map((g) => ({
            ...g,
            populations: createPopulationsFromTemplate(measure.scoringType),
          }))
        )
        setIsDirty(true)
      } else {
        setScoringChanged(true)
      }
      prevScoringRef.current = measure.scoringType
    }
  }, [measure.scoringType, groups])

  const handleResetPopulations = () => {
    setGroups((prev) =>
      prev.map((g) => ({
        ...g,
        populations: createPopulationsFromTemplate(measure.scoringType),
      }))
    )
    setScoringChanged(false)
    setIsDirty(true)
  }

  const { data: expressions = [] } = useQuery<CqlExpression[]>({
    queryKey: ['cql-expressions', measure.id],
    queryFn: () => measureApi.getCqlExpressions(measure.id!),
    enabled: !!measure.id && !!measure.cqlContent,
  })

  // Filter to Patient-context, Public expressions
  const patientExpressions = useMemo(
    () => expressions.filter((e) => e.context === 'Patient' && e.accessLevel === 'Public'),
    [expressions]
  )

  const expressionNames = useMemo(
    () => patientExpressions.map((e) => e.name),
    [patientExpressions]
  )

  // Track groups in a ref so the auto-map effect can read the latest value
  // without re-triggering when groups are updated by setGroups within the effect
  const groupsRef = useRef(groups)
  groupsRef.current = groups

  // Auto-map effect
  useEffect(() => {
    if (autoMapDoneRef.current) return
    if (expressionNames.length === 0) return
    const currentGroups = groupsRef.current
    // Skip if any population already has an expression assigned
    const anyFilled = currentGroups.some((g) =>
      (g.populations || []).some((p) => !!p.criteriaExpression)
    )
    if (anyFilled) {
      autoMapDoneRef.current = true
      return
    }

    let totalCount = 0
    const newGroups = currentGroups.map((g) => {
      const { mapped, count } = autoMapExpressions(g.populations || [], expressionNames)
      totalCount += count
      return { ...g, populations: mapped }
    })

    autoMapDoneRef.current = true
    if (totalCount > 0) {
      setGroups(newGroups)
      setIsDirty(true)
      setAutoMapAlert(t('populationCriteria.autoMapped', { count: totalCount }))
      setTimeout(() => setAutoMapAlert(null), ALERT_DISMISS_ERROR_MS)
    }
  }, [expressionNames])

  const template = SCORING_TEMPLATES[measure.scoringType]

  // Validation
  const validationMessages = useMemo<ValidationMessage[]>(() => {
    const msgs: ValidationMessage[] = []
    if (!template) return msgs

    for (const group of groups) {
      const pops = group.populations || []
      const usedTypes = pops.map((p) => p.populationType)

      // Missing required populations
      for (const req of template.required) {
        // For ratio scoring, allow exactly 2 initial-population entries
        if (req === 'initial-population' && measure.scoringType === SCORING_TYPE.RATIO) {
          if (!usedTypes.includes(req)) {
            msgs.push({
              type: 'error',
              message: `${group.groupId}: ${t('populationCriteria.validation.missingRequired', { name: popLabel(req) })}`,
            })
          }
        } else if (!usedTypes.includes(req)) {
          msgs.push({
            type: 'error',
            message: `${group.groupId}: ${t('populationCriteria.validation.missingRequired', { name: popLabel(req) })}`,
          })
        }
      }

      // Empty expression on required populations
      for (const pop of pops) {
        if (template.required.includes(pop.populationType) && !pop.criteriaExpression) {
          msgs.push({
            type: 'warning',
            message: `${group.groupId}: ${t('populationCriteria.validation.noExpression', { name: popLabel(pop.populationType) })}`,
          })
        }
      }

      // Duplicate population types (except initial-population in ratio with exactly 2)
      const seen = new Map<string, number>()
      for (const pop of pops) {
        seen.set(pop.populationType, (seen.get(pop.populationType) || 0) + 1)
      }
      for (const [type, count] of seen) {
        if (count > 1) {
          if (type === 'initial-population' && measure.scoringType === SCORING_TYPE.RATIO && count === 2) {
            // Allowed: ratio dual IP — validate associations instead
            const ips = pops.filter((p) => p.populationType === 'initial-population')
            const assocTypes = ips.map((p) => p.associationType).filter(Boolean)
            if (assocTypes.length < 2) {
              msgs.push({
                type: 'warning',
                message: `${group.groupId}: ${t('populationCriteria.validation.bothAssociations')}`,
              })
            } else if (assocTypes[0] === assocTypes[1]) {
              msgs.push({
                type: 'error',
                message: `${group.groupId}: ${t('populationCriteria.validation.differentAssociations')}`,
              })
            }
          } else {
            msgs.push({
              type: 'error',
              message: `${group.groupId}: ${t('populationCriteria.validation.duplicatePopulation', { name: popLabel(type) })}`,
            })
          }
        }
      }

      // Population basis type check
      const basis = group.populationBasis || 'Boolean'
      if (basis === 'Boolean') {
        for (const pop of pops) {
          if (pop.criteriaExpression) {
            const expr = patientExpressions.find((e) => e.name === pop.criteriaExpression)
            if (expr?.resultType && !expr.resultType.toLowerCase().includes('boolean')) {
              msgs.push({
                type: 'warning',
                message: `${group.groupId}: ${t('populationCriteria.validation.nonBooleanReturn', { name: popLabel(pop.populationType) })}`,
              })
            }
          }
        }
      }

      // Stratifier: has expression but no associations
      for (const strat of group.stratifiers || []) {
        if (strat.criteriaExpression && (!strat.associations || strat.associations.length === 0)) {
          msgs.push({
            type: 'warning',
            message: `${group.groupId}: ${t('populationCriteria.validation.noPopulationAssociations', { name: strat.stratifierId })}`,
          })
        }
      }
    }
    return msgs
  }, [groups, template, measure.scoringType, patientExpressions])

  const hasErrors = validationMessages.some((m) => m.type === 'error')

  const saveMutation = useMutation({
    mutationFn: () =>
      measureApi.updateMeasure(measure.id!, {
        ...measure,
        groupDefinitions: groups,
        riskAdjustments,
        supplementalData,
        improvementNotation: improvementNotation || undefined,
        rateAggregation: rateAggregation || undefined,
      }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      onMeasureUpdate(updated)
      setIsDirty(false)
    },
  })

  const updateGroup = (groupIdx: number, field: keyof GroupDefinition, value: unknown) => {
    setGroups((prev) => {
      const updated = [...prev]
      updated[groupIdx] = { ...updated[groupIdx], [field]: value }
      return updated
    })
    setIsDirty(true)
  }

  const updatePopulation = (groupIdx: number, popIdx: number, field: keyof PopulationDefinition, value: string) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops[popIdx] = { ...pops[popIdx], [field]: value }
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const addPopulation = (groupIdx: number, populationType: string) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops.push({ populationType, criteriaExpression: '' })
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const removePopulation = (groupIdx: number, popIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const pops = [...(updated[groupIdx].populations || [])]
      pops.splice(popIdx, 1)
      updated[groupIdx] = { ...updated[groupIdx], populations: pops }
      return updated
    })
    setIsDirty(true)
  }

  const addStratifier = (groupIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats.push({ stratifierId: `strat-${strats.length + 1}`, criteriaExpression: '', description: '' })
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const updateStratifier = (groupIdx: number, stratIdx: number, field: keyof StratifierDefinition, value: string | string[]) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats[stratIdx] = { ...strats[stratIdx], [field]: value }
      // Auto-populate associations when expression first selected
      if (field === 'criteriaExpression' && value && (!strats[stratIdx].associations || strats[stratIdx].associations!.length === 0)) {
        strats[stratIdx] = { ...strats[stratIdx], associations: getPopulationTypesForScoring(measure.scoringType) }
      }
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const removeStratifier = (groupIdx: number, stratIdx: number) => {
    setGroups((prev) => {
      const updated = [...prev]
      const strats = [...(updated[groupIdx].stratifiers || [])]
      strats.splice(stratIdx, 1)
      updated[groupIdx] = { ...updated[groupIdx], stratifiers: strats }
      return updated
    })
    setIsDirty(true)
  }

  const addGroup = () => {
    setGroups((prev) => [...prev, createGroupFromScoring(prev.length, measure.scoringType)])
    setIsDirty(true)
  }

  const removeGroup = (idx: number) => {
    setGroups((prev) => prev.filter((_, i) => i !== idx))
    setIsDirty(true)
  }

  // Get unused optional types for the "Add Optional Population" menu
  const getUnusedOptionalTypes = (groupIdx: number): string[] => {
    if (!template) return []
    const usedTypes = (groups[groupIdx].populations || []).map((p) => p.populationType)
    return template.optional.filter((t) => !usedTypes.includes(t))
  }

  const stratifierAssociationOptions = getPopulationTypesForScoring(measure.scoringType)

  // Completeness indicator
  const completenessChecks = useMemo(() => {
    const hasPops = groups.some((g) => (g.populations || []).length > 0)
    const hasExprs = groups.some((g) =>
      (g.populations || []).some((p) => !!p.criteriaExpression)
    )
    const hasNotation = !!improvementNotation
    const hasStrats = groups.some((g) => (g.stratifiers || []).length > 0)
    const hasSupp = supplementalData.length > 0 || riskAdjustments.length > 0
    return [
      { key: 'populations', done: hasPops },
      { key: 'expressions', done: hasExprs },
      { key: 'improvementNotation', done: hasNotation },
      { key: 'stratifiers', done: hasStrats },
      { key: 'supplemental', done: hasSupp },
    ]
  }, [groups, improvementNotation, supplementalData, riskAdjustments])

  const completedCount = completenessChecks.filter((c) => c.done).length
  const completenessPercent = Math.round((completedCount / completenessChecks.length) * 100)

  // Sidebar navigation items
  const sidebarGroupItems = useMemo(() =>
    groups.map((g, i) => {
      const pops = g.populations || []
      const hasPopsWithExpr = pops.some((p) => !!p.criteriaExpression)
      return {
        key: `group-${i}`,
        label: t('populationCriteria.groupLabel', { number: i + 1 }),
        done: hasPopsWithExpr,
        groupIdx: i,
      }
    }),
    [groups, t]
  )

  const sidebarSectionItems = useMemo(() => [
    {
      key: 'stratifiers',
      label: t('populationCriteria.subTabs.stratifications'),
      subTab: 1,
      done: groups.some((g) => (g.stratifiers || []).length > 0),
    },
    {
      key: 'reporting',
      label: t('populationCriteria.subTabs.reporting'),
      subTab: 2,
      done: !!improvementNotation,
    },
    {
      key: 'supplemental',
      label: t('populationCriteria.subTabs.supplemental'),
      subTab: 3,
      done: supplementalData.length > 0 || riskAdjustments.length > 0,
    },
  ], [groups, improvementNotation, supplementalData, riskAdjustments, t])

  const handleSidebarGroupClick = (groupIdx: number) => {
    setSubTab(0)
    setTimeout(() => {
      document.getElementById(`pop-group-${groupIdx}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 50)
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Typography variant="h6">{t('populationCriteria.title')}</Typography>
          <HelpTooltip text={helpContent.measures.populationCriteria} />
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button size="small" startIcon={<AddIcon />} onClick={addGroup}>
            {t('populationCriteria.addGroup')}
          </Button>
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!isDirty || hasErrors || saveMutation.isPending || readOnly}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t('populationCriteria.saving') : t('populationCriteria.save')}
          </GradientButton>
        </Stack>
      </Stack>

      {/* Completeness indicator */}
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <Tooltip
          arrow
          title={
            <Stack spacing={0.5} sx={{ py: 0.5 }}>
              {completenessChecks.map((c) => (
                <Stack key={c.key} direction="row" spacing={0.5} alignItems="center">
                  {c.done ? (
                    <CheckCircleIcon sx={{ fontSize: 14, color: 'success.light' }} />
                  ) : (
                    <UncheckedIcon sx={{ fontSize: 14, color: 'grey.500' }} />
                  )}
                  <Typography variant="caption">
                    {t(`populationCriteria.completeness.${c.key}`)}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          }
        >
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flex: 1, cursor: 'default' }}>
            <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
              {t('populationCriteria.completeness.label')} {completedCount}/{completenessChecks.length}
            </Typography>
            <LinearProgress
              variant="determinate"
              value={completenessPercent}
              sx={{
                flex: 1,
                height: 6,
                borderRadius: 3,
                bgcolor: 'action.hover',
                '& .MuiLinearProgress-bar': {
                  borderRadius: 3,
                  bgcolor: completenessPercent === 100 ? 'success.main' : 'primary.main',
                },
              }}
            />
          </Stack>
        </Tooltip>
      </Stack>

      {saveMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(saveMutation.error as Error).message}
        </Alert>
      )}

      {autoMapAlert && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setAutoMapAlert(null)}>
          {autoMapAlert}
        </Alert>
      )}

      {scoringChanged && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={handleResetPopulations}>
              {t('populationCriteria.resetPopulations')}
            </Button>
          }
        >
          <span dangerouslySetInnerHTML={{ __html: t('populationCriteria.scoringChanged', { scoringType: measure.scoringType }) }} />
        </Alert>
      )}

      {patientExpressions.length === 0 && measure.cqlContent && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('populationCriteria.noExpressions')}
        </Alert>
      )}

      {validationMessages.length > 0 && (
        <Alert severity={hasErrors ? 'error' : 'warning'} sx={{ mb: 2 }}>
          <Stack spacing={0.5}>
            {validationMessages.map((msg, i) => (
              <Typography key={i} variant="body2">
                {msg.message}
              </Typography>
            ))}
          </Stack>
        </Alert>
      )}

      <Box sx={{ display: 'flex', gap: 2, minHeight: 0 }}>
        {/* Left sidebar navigation */}
        <Paper
          variant="outlined"
          sx={{
            width: sidebarOpen ? 180 : 40,
            flexShrink: 0,
            transition: 'width 0.2s',
            overflow: 'hidden',
            display: { xs: 'none', md: 'block' },
          }}
        >
          <Box sx={{ display: 'flex', justifyContent: sidebarOpen ? 'flex-end' : 'center', p: 0.5 }}>
            <Tooltip title={sidebarOpen ? t('populationCriteria.sidebar.collapse') : t('populationCriteria.sidebar.expand')}>
              <IconButton size="small" onClick={() => setSidebarOpen((v) => !v)}>
                {sidebarOpen ? <ChevronLeftIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Box>
          {sidebarOpen && (
            <List dense disablePadding>
              <ListSubheader sx={{ lineHeight: '28px', fontSize: '0.7rem', bgcolor: 'transparent' }}>
                {t('populationCriteria.sidebar.groups')}
              </ListSubheader>
              {sidebarGroupItems.map((item) => (
                <ListItemButton
                  key={item.key}
                  selected={subTab === 0}
                  onClick={() => handleSidebarGroupClick(item.groupIdx)}
                  sx={{ py: 0.25, pl: 2 }}
                >
                  {item.done ? (
                    <CheckCircleIcon sx={{ fontSize: 14, mr: 1, color: 'success.main' }} />
                  ) : (
                    <UncheckedIcon sx={{ fontSize: 14, mr: 1, color: 'text.disabled' }} />
                  )}
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{ variant: 'body2', fontSize: '0.8rem' }}
                  />
                </ListItemButton>
              ))}
              <Divider sx={{ my: 0.5 }} />
              {sidebarSectionItems.map((item) => (
                <ListItemButton
                  key={item.key}
                  selected={subTab === item.subTab}
                  onClick={() => setSubTab(item.subTab)}
                  sx={{ py: 0.25, pl: 2 }}
                >
                  {item.done ? (
                    <CheckCircleIcon sx={{ fontSize: 14, mr: 1, color: 'success.main' }} />
                  ) : (
                    <UncheckedIcon sx={{ fontSize: 14, mr: 1, color: 'text.disabled' }} />
                  )}
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{ variant: 'body2', fontSize: '0.8rem' }}
                  />
                </ListItemButton>
              ))}
            </List>
          )}
        </Paper>

        {/* Main content area */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Tabs
            value={subTab}
            onChange={(_, v) => setSubTab(v)}
            sx={{ minHeight: 36, mb: 2, borderBottom: 1, borderColor: 'divider', display: { md: 'none', xs: 'flex' } }}
          >
            <Tab label={t('populationCriteria.subTabs.populations')} sx={{ minHeight: 36, textTransform: 'none' }} />
            <Tab label={t('populationCriteria.subTabs.stratifications')} sx={{ minHeight: 36, textTransform: 'none' }} />
            <Tab label={t('populationCriteria.subTabs.reporting')} sx={{ minHeight: 36, textTransform: 'none' }} />
            <Tab label={t('populationCriteria.subTabs.supplemental')} sx={{ minHeight: 36, textTransform: 'none' }} />
          </Tabs>

      {/* Sub-tab 0: Populations */}
      {subTab === 0 && (
        <Stack spacing={3}>
          {groups.map((group, groupIdx) => {
            const pops = group.populations || []
            const unusedOptional = getUnusedOptionalTypes(groupIdx)
            const ipCount = pops.filter((p) => p.populationType === 'initial-population').length
            const showDualIpButton = measure.scoringType === SCORING_TYPE.RATIO && ipCount < 2
            const showAssociationType = measure.scoringType === SCORING_TYPE.RATIO && ipCount === 2

            const requiredTypes = template?.required || []
            const sortedPops = [...pops].sort((a, b) => {
              const aReq = requiredTypes.includes(a.populationType) ? 0 : 1
              const bReq = requiredTypes.includes(b.populationType) ? 0 : 1
              if (aReq !== bReq) return aReq - bReq
              const aIdx = requiredTypes.indexOf(a.populationType)
              const bIdx = requiredTypes.indexOf(b.populationType)
              if (aIdx !== -1 && bIdx !== -1) return aIdx - bIdx
              return 0
            })
            const sortedWithIdx = sortedPops.map((pop) => ({
              pop,
              originalIdx: pops.indexOf(pop),
            }))

            // Split into standard (left) and exclusion (right) columns
            const standardPops = sortedWithIdx.filter(({ pop }) => !EXCLUSION_POPULATION_TYPES.has(pop.populationType))
            const exclusionPops = sortedWithIdx.filter(({ pop }) => EXCLUSION_POPULATION_TYPES.has(pop.populationType))
            const hasExclusions = exclusionPops.length > 0

            return (
              <Paper key={groupIdx} id={`pop-group-${groupIdx}`} variant="outlined" sx={{ p: 2 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="subtitle1" fontWeight={600}>
                      {t('populationCriteria.groupLabel', { number: groupIdx + 1 })}
                    </Typography>
                    <Chip label={t('populationCriteria.populationCount', { count: pops.length })} size="small" />
                  </Stack>
                  {groups.length > 1 && (
                    <IconButton size="small" aria-label={t('populationCriteria.removeGroup')} color="error" onClick={() => removeGroup(groupIdx)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  )}
                </Stack>

                <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
                  <TextField
                    label={t('populationCriteria.fields.groupDescription')}
                    size="small"
                    fullWidth
                    value={group.description || ''}
                    onChange={(e) => updateGroup(groupIdx, 'description', e.target.value)}
                  />
                  <TextField
                    label={t('populationCriteria.fields.rateNumber')}
                    size="small"
                    type="number"
                    sx={{ width: 90 }}
                    value={group.rateIndex ?? ''}
                    onChange={(e) => updateGroup(groupIdx, 'rateIndex', e.target.value ? parseInt(e.target.value) : undefined)}
                    helperText={t('populationCriteria.fields.rateNumberHelper')}
                  />
                  <TextField
                    label={t('populationCriteria.fields.rateDescription')}
                    size="small"
                    sx={{ minWidth: 200 }}
                    value={group.rateDescription || ''}
                    onChange={(e) => updateGroup(groupIdx, 'rateDescription', e.target.value)}
                    helperText={t('populationCriteria.fields.rateDescriptionHelper')}
                  />
                </Stack>

                <TextField
                  select
                  label={t('populationCriteria.fields.populationBasis')}
                  size="small"
                  fullWidth
                  value={group.populationBasis || 'Boolean'}
                  onChange={(e) => updateGroup(groupIdx, 'populationBasis', e.target.value)}
                  sx={{ mb: 2 }}
                  helperText={t('populationCriteria.fields.populationBasisHelper')}
                >
                  {POPULATION_BASIS_OPTIONS.map((opt) => (
                    <MenuItem key={opt} value={opt}>
                      {opt}
                    </MenuItem>
                  ))}
                </TextField>

                <Grid container spacing={2}>
                  {/* Left column: standard populations */}
                  <Grid item xs={12} md={hasExclusions ? 6 : 12}>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      {t('populationCriteria.populations')}
                    </Typography>
                    <Stack spacing={1.5}>
                      {standardPops.map(({ pop, originalIdx }) => {
                        const isIp = pop.populationType === 'initial-population'
                        const lastIpIdx = pops.reduce((last, p, i) => p.populationType === 'initial-population' ? i : last, -1)
                        const isSecondIp = isIp && ipCount === 2 && originalIdx === lastIpIdx
                        const canRemove = isSecondIp || !requiredTypes.includes(pop.populationType)

                        return (
                          <PopulationCard
                            key={`${pop.populationType}-${originalIdx}`}
                            population={pop}
                            isRequired={requiredTypes.includes(pop.populationType)}
                            expressions={patientExpressions}
                            onChange={(field, value) => updatePopulation(groupIdx, originalIdx, field, value)}
                            onRemove={() => removePopulation(groupIdx, originalIdx)}
                            canRemove={canRemove}
                            showAssociationType={isIp && showAssociationType}
                            populationBasis={group.populationBasis}
                          />
                        )
                      })}
                    </Stack>
                  </Grid>

                  {/* Right column: exclusion / exception populations */}
                  {hasExclusions && (
                    <Grid item xs={12} md={6}>
                      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                        {t('populationCriteria.exclusions')}
                      </Typography>
                      <Stack spacing={1.5}>
                        {exclusionPops.map(({ pop, originalIdx }) => {
                          const canRemove = !requiredTypes.includes(pop.populationType)

                          return (
                            <PopulationCard
                              key={`${pop.populationType}-${originalIdx}`}
                              population={pop}
                              isRequired={requiredTypes.includes(pop.populationType)}
                              expressions={patientExpressions}
                              onChange={(field, value) => updatePopulation(groupIdx, originalIdx, field, value)}
                              onRemove={() => removePopulation(groupIdx, originalIdx)}
                              canRemove={canRemove}
                              showAssociationType={false}
                              populationBasis={group.populationBasis}
                            />
                          )
                        })}
                      </Stack>
                    </Grid>
                  )}
                </Grid>

                <Stack direction="row" spacing={1} sx={{ alignSelf: 'flex-start', mt: 2 }}>
                  {showDualIpButton && (
                    <Button
                      size="small"
                      startIcon={<AddIcon />}
                      onClick={() => addPopulation(groupIdx, 'initial-population')}
                    >
                      {t('populationCriteria.addSecondIP')}
                    </Button>
                  )}

                  {unusedOptional.length > 0 && (
                    <>
                      <Button
                        size="small"
                        startIcon={<AddIcon />}
                        onClick={(e) => setAddMenuAnchor({ el: e.currentTarget, groupIdx })}
                      >
                        {t('populationCriteria.addOptional')}
                      </Button>
                      <Menu
                        anchorEl={addMenuAnchor?.groupIdx === groupIdx ? addMenuAnchor.el : null}
                        open={addMenuAnchor?.groupIdx === groupIdx}
                        onClose={() => setAddMenuAnchor(null)}
                      >
                        {unusedOptional.map((type) => (
                          <MenuItem
                            key={type}
                            onClick={() => {
                              addPopulation(groupIdx, type)
                              setAddMenuAnchor(null)
                            }}
                          >
                            {popLabel(type)}
                          </MenuItem>
                        ))}
                      </Menu>
                    </>
                  )}
                </Stack>

                {OBSERVATION_REQUIRED_SCORING.has(measure.scoringType) && (
                  <>
                    <Divider sx={{ my: 2 }} />
                    <ObservationSection
                      observations={group.observations}
                      onChange={(obs) => updateGroup(groupIdx, 'observations', obs)}
                      expressionNames={expressionNames}
                      populationTypes={(group.populations || []).map(p => p.populationType)}
                      readOnly={measure.status === 'active'}
                    />
                  </>
                )}

                <Divider sx={{ my: 2 }} />
                <UcumUnitField
                  label={t('populationCriteria.scoringUnit.label')}
                  fullWidth
                  value={group.scoringUnit || ''}
                  onChange={(val) => updateGroup(groupIdx, 'scoringUnit', val)}
                  placeholder={t('populationCriteria.scoringUnit.placeholder')}
                  helperText={t('populationCriteria.scoringUnit.helper')}
                />
              </Paper>
            )
          })}
        </Stack>
      )}

      {/* Sub-tab 1: Stratifications */}
      {subTab === 1 && (
        <Stack spacing={3}>
          {groups.map((group, groupIdx) => {
            const strats = group.stratifiers || []
            return (
              <Paper key={groupIdx} variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle1" fontWeight={600} mb={2}>
                  {t('populationCriteria.groupLabel', { number: groupIdx + 1 })}
                </Typography>

                {strats.length === 0 ? (
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    {t('populationCriteria.noStratifiers')}
                  </Typography>
                ) : (
                  <Stack spacing={1.5} mb={2}>
                    {strats.map((strat, stratIdx) => (
                      <Paper key={stratIdx} variant="outlined" sx={{ p: 1.5 }}>
                        <Stack spacing={1.5}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <TextField
                              label={t('populationCriteria.stratifierFields.stratifierId')}
                              size="small"
                              value={strat.stratifierId}
                              onChange={(e) => updateStratifier(groupIdx, stratIdx, 'stratifierId', e.target.value)}
                              sx={{ minWidth: 140 }}
                            />
                            <TextField
                              select={expressionNames.length > 0}
                              label={t('populationCriteria.stratifierFields.cqlExpression')}
                              size="small"
                              fullWidth
                              value={strat.criteriaExpression}
                              onChange={(e) => updateStratifier(groupIdx, stratIdx, 'criteriaExpression', e.target.value)}
                            >
                              {expressionNames.map((name) => (
                                <MenuItem key={name} value={name}>
                                  {name}
                                </MenuItem>
                              ))}
                            </TextField>
                            <IconButton size="small" aria-label={t('populationCriteria.stratifierFields.removeStratifier')} color="error" onClick={() => removeStratifier(groupIdx, stratIdx)}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Stack>

                          {strat.criteriaExpression && stratifierAssociationOptions.length > 0 && (
                            <TextField
                              select
                              label={t('populationCriteria.stratifierFields.populationAssociations')}
                              size="small"
                              fullWidth
                              SelectProps={{
                                multiple: true,
                                renderValue: (selected) =>
                                  (selected as string[]).map((v) => popLabel(v)).join(', '),
                              }}
                              value={strat.associations || []}
                              onChange={(e) => updateStratifier(groupIdx, stratIdx, 'associations', e.target.value as unknown as string[])}
                            >
                              {stratifierAssociationOptions.map((type) => (
                                <MenuItem key={type} value={type}>
                                  <Checkbox checked={(strat.associations || []).includes(type)} size="small" />
                                  <ListItemText primary={popLabel(type)} />
                                </MenuItem>
                              ))}
                            </TextField>
                          )}

                          <TextField
                            label={t('populationCriteria.stratifierFields.description')}
                            size="small"
                            fullWidth
                            multiline
                            minRows={2}
                            maxRows={4}
                            value={strat.description || ''}
                            onChange={(e) => updateStratifier(groupIdx, stratIdx, 'description', e.target.value)}
                            placeholder={t('populationCriteria.stratifierFields.descriptionPlaceholder')}
                          />
                        </Stack>
                      </Paper>
                    ))}
                  </Stack>
                )}

                <Button size="small" startIcon={<AddIcon />} onClick={() => addStratifier(groupIdx)} sx={{ alignSelf: 'flex-start' }}>
                  {t('populationCriteria.stratifierFields.addStratifier')}
                </Button>
              </Paper>
            )
          })}
        </Stack>
      )}

      {/* Sub-tab 2: Reporting */}
      {subTab === 2 && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={3}>
            <Box>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                {t('populationCriteria.reporting.improvementNotation')}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {t('populationCriteria.reporting.improvementNotationHelper')}
              </Typography>
              <TextField
                select
                size="small"
                fullWidth
                value={improvementNotation}
                onChange={(e) => { setImprovementNotation(e.target.value); setIsDirty(true) }}
                sx={{ maxWidth: 400 }}
              >
                <MenuItem value="">
                  <em>{t('populationCriteria.reporting.notSet')}</em>
                </MenuItem>
                <MenuItem value="increase">{t('populationCriteria.reporting.increase')}</MenuItem>
                <MenuItem value="decrease">{t('populationCriteria.reporting.decrease')}</MenuItem>
              </TextField>
            </Box>

            <Divider />

            <Box>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                {t('populationCriteria.reporting.rateAggregation')}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {t('populationCriteria.reporting.rateAggregationHelper')}
              </Typography>
              <TextField
                size="small"
                fullWidth
                multiline
                minRows={3}
                maxRows={8}
                value={rateAggregation}
                onChange={(e) => { setRateAggregation(e.target.value); setIsDirty(true) }}
                placeholder={t('populationCriteria.reporting.rateAggregationPlaceholder')}
              />
            </Box>
          </Stack>
        </Paper>
      )}

      {/* Sub-tab 3: Supplemental */}
      {subTab === 3 && (
        <Stack spacing={2}>
          <Stack direction="row" spacing={0.5} alignItems="center">
            <Typography variant="h6">{t('populationCriteria.riskAdjustment')}</Typography>
            <HelpTooltip text={helpContent.measures.riskAdjustment} />
          </Stack>
          <RiskAdjustmentSection
            riskAdjustments={riskAdjustments}
            onChange={(val) => { setRiskAdjustments(val); setIsDirty(true) }}
            expressionNames={expressionNames}
            readOnly={measure.status === 'active'}
          />
          <Stack direction="row" spacing={0.5} alignItems="center">
            <Typography variant="h6">{t('populationCriteria.supplementalData')}</Typography>
            <HelpTooltip text={helpContent.measures.supplementalData} />
          </Stack>
          <SupplementalDataSection
            supplementalData={supplementalData}
            onChange={(val) => { setSupplementalData(val); setIsDirty(true) }}
            expressionNames={expressionNames}
            readOnly={measure.status === 'active'}
          />
        </Stack>
      )}
        </Box>
      </Box>
    </Box>
  )
}
