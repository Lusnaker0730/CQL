import { useState, useCallback, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Card, Tabs, Tab, Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, Typography, Stack, Chip, Tooltip, Alert, FormControlLabel, Switch } from '@mui/material'
import { CheckCircle as CheckIcon, ErrorOutline as ErrorIcon, Public as PublicIcon, LibraryBooks as LibraryIcon } from '@mui/icons-material'
import LibraryDefinitionPicker from '../cql-libraries/LibraryDefinitionPicker'
import { useArtifactLibraryPicker } from '../../hooks/useArtifactLibraryPicker'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { useArtifactHistory } from '../../hooks/useArtifactHistory'
import ArtifactWorkspaceHeader from './ArtifactWorkspaceHeader'
import ConjunctionGroup from './builder/ConjunctionGroup'
import Subpopulations from './subpopulations/Subpopulations'
import BaseElements from './base-elements/BaseElements'
import Recommendations from './recommendations/Recommendations'
import Parameters from './parameters/Parameters'
import ErrorStatementEditor from './error-statement/ErrorStatement'
import ExternalCql from './external-cql/ExternalCql'
import CqlPreviewPanel from './cql-preview/CqlPreviewPanel'
import ArtifactTester from './testing/ArtifactTester'
import ArtifactSummaryView from './summary/ArtifactSummaryView'
import { useUpdateArtifact, useArtifact } from '../../hooks/useAuthoring'
import { useTemplates } from '../../hooks/useTemplates'
import { useModifiers } from '../../hooks/useModifiers'
import { useExternalCqlList } from '../../hooks/useExternalCql'
import type { Artifact, ArtifactRequest, ConjunctionGroup as ConjunctionGroupType, ElementInstance } from '../../types/authoring'
import type { DynamicEntry } from './element-select/ElementSelectDropdown'
import {
  SYSTEM_DEFINITIONS, DEF_MEETS_INCLUSION, DEF_MEETS_EXCLUSION,
  TAB_INDEX_REVIEW_CQL, TAB_INDEX_TESTING, TAB_INDEX_SUMMARY, KEYBOARD_SHORTCUTS,
} from '../../constants/authoringConstants'
import { generateId } from '../../utils/validation'
import { getModifierMissingFields } from '../../utils/modifierUtils'

/**
 * Sync reference element names with current base elements / parameters.
 * Walks an expression tree and patches element_name fields for references.
 */
function syncReferenceNames(
  children: ElementInstance[],
  beMap: Map<string, string>,
  paramMap: Map<string, string>,
): ElementInstance[] {
  return children.map((el) => {
    // Recurse into conjunction sub-groups
    if (el.conjunction && el.childInstances) {
      const synced = syncReferenceNames(el.childInstances, beMap, paramMap)
      return synced !== el.childInstances ? { ...el, childInstances: synced } : el
    }
    // Patch reference names
    if (el.type === 'baseElementRef' || el.type === 'parameterRef') {
      const refId = el.fields?.find((f) => f.id === 'reference_id')?.value as string | undefined
      if (!refId) return el
      const map = el.type === 'baseElementRef' ? beMap : paramMap
      const currentName = map.get(refId)
      if (!currentName) return el
      const nameField = el.fields?.find((f) => f.id === 'element_name')
      if (nameField && nameField.value === currentName && el.name === currentName) return el
      return {
        ...el,
        name: currentName,
        fields: (el.fields || []).map((f) =>
          f.id === 'element_name' ? { ...f, value: currentName } : f
        ),
      }
    }
    return el
  })
}

function syncTreeRefs(
  tree: ConjunctionGroupType | undefined,
  beMap: Map<string, string>,
  paramMap: Map<string, string>,
): ConjunctionGroupType | undefined {
  if (!tree?.childInstances?.length) return tree
  const synced = syncReferenceNames(tree.childInstances, beMap, paramMap)
  return synced !== tree.childInstances ? { ...tree, childInstances: synced } : tree
}

interface ArtifactWorkspaceProps {
  artifact: Artifact
  onBack: () => void
  onArtifactUpdate: (updated: Artifact) => void
}

const DEFAULT_TREE: ConjunctionGroupType = {
  id: 'And',
  name: 'And',
  conjunction: true,
  returnType: 'boolean',
  childInstances: [],
}

type TabStatus = 'empty' | 'has-content' | 'has-error'

interface ValidationError {
  key: string
  params: Record<string, unknown>
}

interface TabStatusInfo {
  status: TabStatus
  errors: ValidationError[]
}

function collectTreeErrors(tree: ConjunctionGroupType | undefined): ValidationError[] {
  if (!tree?.childInstances?.length) return []
  const errors: ValidationError[] = []
  tree.childInstances.forEach((el) => {
    const name = (el.fields?.find((f) => f.id === 'element_name')?.value as string | undefined) || el.name
    if (el.modifiers?.length) {
      let cur = el.returnType
      for (const mod of el.modifiers) {
        if (!mod.inputTypes.includes(cur)) {
          errors.push({
            key: 'workspace.validation.elementModifierTypeMismatch',
            params: { element: name, modifier: mod.name, expected: mod.inputTypes.join('/'), actual: cur },
          })
        }
        const missing = getModifierMissingFields(mod)
        if (missing.length > 0) {
          errors.push({
            key: 'workspace.validation.elementModifierMissingFields',
            params: { element: name, modifier: mod.name, fields: missing.join(', ') },
          })
        }
        cur = mod.returnType
      }
    }
  })
  return errors
}

function computeTabStatuses(a: Artifact): TabStatusInfo[] {
  const treeHasContent = (tree?: ConjunctionGroupType) =>
    (tree?.childInstances?.length ?? 0) > 0

  const includeErrors = collectTreeErrors(a.expTreeInclude)
  const excludeErrors = collectTreeErrors(a.expTreeExclude)

  const recErrors: ValidationError[] = []
  ;(a.recommendations || []).forEach((r, i) => {
    if (!r.text.trim()) recErrors.push({ key: 'workspace.validation.recommendationMissingText', params: { index: i + 1 } })
  })

  const paramErrors: ValidationError[] = []
  ;(a.parameters || []).forEach((p, i) => {
    if (!p.name.trim()) paramErrors.push({ key: 'workspace.validation.parameterMissingName', params: { index: i + 1 } })
    else if (!p.type) paramErrors.push({ key: 'workspace.validation.parameterMissingType', params: { name: p.name } })
  })

  const subpopErrors: ValidationError[] = []
  ;(a.subpopulations || []).filter((s) => !s.special).forEach((s, i) => {
    if (!s.subpopulationName.trim()) subpopErrors.push({ key: 'workspace.validation.subpopulationMissingName', params: { index: i + 1 } })
  })

  const baseErrors: ValidationError[] = []
  ;(a.baseElements || []).forEach((be, i) => {
    if (!be.name.trim()) baseErrors.push({ key: 'workspace.validation.baseElementMissingName', params: { index: i + 1 } })
  })

  const info = (hasContent: boolean, errors: ValidationError[]): TabStatusInfo => ({
    status: errors.length > 0 ? 'has-error' : hasContent ? 'has-content' : 'empty',
    errors,
  })

  return [
    info(treeHasContent(a.expTreeInclude), includeErrors),
    info(treeHasContent(a.expTreeExclude), excludeErrors),
    info((a.subpopulations || []).filter((s) => !s.special).length > 0, subpopErrors),
    info((a.baseElements || []).length > 0, baseErrors),
    info((a.recommendations || []).length > 0, recErrors),
    info((a.parameters || []).length > 0, paramErrors),
    info(!!(a.errorStatement?.ifThenClauses?.length || a.errorStatement?.elseClause), []),
    { status: 'empty', errors: [] },
    { status: 'empty', errors: [] },
    { status: 'empty', errors: [] },
    { status: 'empty', errors: [] },
  ]
}

export default function ArtifactWorkspace({
  artifact,
  onBack,
  onArtifactUpdate,
}: ArtifactWorkspaceProps) {
  const { t } = useTranslation('authoring')
  const { t: tc } = useTranslation('common')

  const TAB_LABELS = useMemo(() => [
    t('workspace.tabInclusions'),
    t('workspace.tabExclusions'),
    t('workspace.tabSubpopulations'),
    t('workspace.tabBaseElements'),
    t('workspace.tabRecommendations'),
    t('workspace.tabParameters'),
    t('workspace.tabErrorHandling'),
    t('workspace.tabExternalCql'),
    t('workspace.tabReviewCql'),
    t('workspace.tabTesting'),
    t('workspace.tabSummary'),
  ], [t])

  const [tab, setTab] = useState(0)
  const [localArtifact, setLocalArtifact] = useState<Artifact>(artifact)
  const [isDirty, setIsDirty] = useState(false)
  const [twcoreMode, setTwcoreMode] = useState(false)
  const [showBackConfirm, setShowBackConfirm] = useState(false)
  const [showConflictDialog, setShowConflictDialog] = useState(false)
  const [reloadError, setReloadError] = useState<string | null>(null)
  const updateMutation = useUpdateArtifact()
  const { refetch: refetchArtifact } = useArtifact(artifact.id)

  // Browser beforeunload guard
  useUnsavedChangesGuard(isDirty)
  const { data: templates = [], error: templatesError } = useTemplates()
  const { data: modifiers = [], error: modifiersError } = useModifiers()
  const { pushState, undo, redo, reset: resetHistory } = useArtifactHistory()

  useEffect(() => {
    setLocalArtifact(artifact)
    setIsDirty(false)
    resetHistory()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset only when artifact.id changes, not on every artifact object mutation
  }, [artifact.id, resetHistory])

  const updateLocal = useCallback((updates: Partial<Artifact>) => {
    // Snapshot the pre-update state for undo BEFORE the setter — keeps
    // history correct under React 18 StrictMode / concurrent rendering,
    // which may invoke the setter callback twice.
    pushState(localArtifact)
    setLocalArtifact((prev) => {
      const next = { ...prev, ...updates }

      // If base elements or parameters changed, sync reference names in expression trees
      if (updates.baseElements || updates.parameters) {
        const beMap = new Map<string, string>()
        for (const be of (next.baseElements || [])) beMap.set(be.uniqueId, be.name)
        const paramMap = new Map<string, string>()
        for (const p of (next.parameters || [])) if (p.name) paramMap.set(p.uniqueId, p.name)

        const syncedInclude = syncTreeRefs(next.expTreeInclude, beMap, paramMap)
        const syncedExclude = syncTreeRefs(next.expTreeExclude, beMap, paramMap)
        if (syncedInclude !== next.expTreeInclude) next.expTreeInclude = syncedInclude!
        if (syncedExclude !== next.expTreeExclude) next.expTreeExclude = syncedExclude!
      }
      return next
    })
    setIsDirty(true)
  }, [pushState, localArtifact])

  const buildSaveRequest = useCallback((): ArtifactRequest => ({
    name: localArtifact.name,
    version: localArtifact.version,
    description: localArtifact.description,
    status: localArtifact.status,
    fhirVersion: localArtifact.fhirVersion,
    expTreeInclude: localArtifact.expTreeInclude,
    expTreeExclude: localArtifact.expTreeExclude,
    recommendations: localArtifact.recommendations,
    subpopulations: localArtifact.subpopulations,
    baseElements: localArtifact.baseElements,
    parameters: localArtifact.parameters,
    errorStatement: localArtifact.errorStatement,
    url: localArtifact.url,
    publisher: localArtifact.publisher,
    purpose: localArtifact.purpose,
    usageInfo: localArtifact.usageInfo,
    copyright: localArtifact.copyright,
    experimental: localArtifact.experimental,
    approvalDate: localArtifact.approvalDate,
    lastReviewDate: localArtifact.lastReviewDate,
    effectivePeriodStart: localArtifact.effectivePeriodStart,
    effectivePeriodEnd: localArtifact.effectivePeriodEnd,
    strengthOfRecommendation: localArtifact.strengthOfRecommendation,
    qualityOfEvidence: localArtifact.qualityOfEvidence,
    context: localArtifact.context,
    topic: localArtifact.topic,
    author: localArtifact.author,
    reviewer: localArtifact.reviewer,
    endorser: localArtifact.endorser,
    relatedArtifact: localArtifact.relatedArtifact,
    lockVersion: localArtifact.lockVersion,
  }), [localArtifact])

  const handleSave = useCallback(
    (request: ArtifactRequest) => {
      // Bail if a previous save is still in flight — prevents double-submit
      // and the response-overwrites-pending-edits race that drops user input
      // typed between submit and onSuccess.
      if (updateMutation.isPending) return
      updateMutation.mutate(
        { id: localArtifact.id, request },
        {
          onSuccess: (updated) => {
            setLocalArtifact(updated)
            setIsDirty(false)
            onArtifactUpdate(updated)
          },
          onError: (error) => {
            if ((error as { response?: { status?: number } })?.response?.status === 409) {
              setShowConflictDialog(true)
            }
          },
        }
      )
    },
    [localArtifact.id, updateMutation, onArtifactUpdate]
  )

  const handleSaveBeforeGenerate = useCallback(async (): Promise<void> => {
    return new Promise<void>((resolve, reject) => {
      const request = buildSaveRequest()
      updateMutation.mutate(
        { id: localArtifact.id, request },
        {
          onSuccess: (updated) => {
            setLocalArtifact(updated)
            setIsDirty(false)
            onArtifactUpdate(updated)
            resolve()
          },
          onError: reject,
        }
      )
    })
  }, [buildSaveRequest, localArtifact.id, updateMutation, onArtifactUpdate])

  const handleNameChange = useCallback(
    (name: string) => updateLocal({ name }),
    [updateLocal]
  )

  const handleBack = useCallback(() => {
    if (isDirty) {
      setShowBackConfirm(true)
    } else {
      onBack()
    }
  }, [isDirty, onBack])

  const [showShortcutHelp, setShowShortcutHelp] = useState(false)

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return
      // Don't capture when typing in inputs/textareas
      const tag = (e.target as HTMLElement)?.tagName
      const isInput = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable

      if (e.key === 's') {
        e.preventDefault()
        if (isDirty) handleSave(buildSaveRequest())
        return
      }
      if (e.key === 'z' && !e.shiftKey) {
        e.preventDefault()
        setLocalArtifact((current) => {
          const prev = undo(current)
          if (prev) {
            setIsDirty(true)
            return prev
          }
          return current
        })
        return
      }
      if ((e.key === 'y') || (e.key === 'z' && e.shiftKey)) {
        e.preventDefault()
        setLocalArtifact((current) => {
          const next = redo(current)
          if (next) {
            setIsDirty(true)
            return next
          }
          return current
        })
        return
      }
      if (e.key === 'g' && !isInput) {
        e.preventDefault()
        setTab(TAB_INDEX_REVIEW_CQL)
        return
      }
      if (e.key === '/' || e.key === '?') {
        e.preventDefault()
        setShowShortcutHelp((prev) => !prev)
        return
      }
      // Ctrl+1-8 → tabs 0-7 (Inclusions..External CQL)
      // Ctrl+9 → Testing (skip Review CQL since it has Ctrl+G)
      // Ctrl+0 → Summary
      if (!isInput && e.key >= '0' && e.key <= '9') {
        e.preventDefault()
        let tabIndex: number
        if (e.key === '0') tabIndex = TAB_INDEX_SUMMARY
        else if (e.key === '9') tabIndex = TAB_INDEX_TESTING
        else tabIndex = parseInt(e.key) - 1
        if (tabIndex <= TAB_INDEX_SUMMARY) setTab(tabIndex)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isDirty, buildSaveRequest, handleSave, undo, redo])

  // Tree update handlers
  const handleUpdateInclude = useCallback(
    (updated: ConjunctionGroupType) => updateLocal({ expTreeInclude: updated }),
    [updateLocal]
  )
  const handleAddIncludeElement = useCallback(
    (element: ElementInstance) => {
      const tree = localArtifact.expTreeInclude || DEFAULT_TREE
      updateLocal({
        expTreeInclude: { ...tree, childInstances: [...tree.childInstances, element] },
      })
    },
    [localArtifact.expTreeInclude, updateLocal]
  )
  const handleRemoveIncludeElement = useCallback(
    (uniqueId: string) => {
      const tree = localArtifact.expTreeInclude || DEFAULT_TREE
      updateLocal({
        expTreeInclude: { ...tree, childInstances: tree.childInstances.filter((c) => c.uniqueId !== uniqueId) },
      })
    },
    [localArtifact.expTreeInclude, updateLocal]
  )
  const handleUpdateIncludeElement = useCallback(
    (uniqueId: string, updates: Partial<ElementInstance>) => {
      const tree = localArtifact.expTreeInclude || DEFAULT_TREE
      updateLocal({
        expTreeInclude: {
          ...tree,
          childInstances: tree.childInstances.map((c) =>
            c.uniqueId === uniqueId ? { ...c, ...updates } : c
          ),
        },
      })
    },
    [localArtifact.expTreeInclude, updateLocal]
  )

  const handleUpdateExclude = useCallback(
    (updated: ConjunctionGroupType) => updateLocal({ expTreeExclude: updated }),
    [updateLocal]
  )
  const handleAddExcludeElement = useCallback(
    (element: ElementInstance) => {
      const tree = localArtifact.expTreeExclude || DEFAULT_TREE
      updateLocal({
        expTreeExclude: { ...tree, childInstances: [...tree.childInstances, element] },
      })
    },
    [localArtifact.expTreeExclude, updateLocal]
  )
  const handleRemoveExcludeElement = useCallback(
    (uniqueId: string) => {
      const tree = localArtifact.expTreeExclude || DEFAULT_TREE
      updateLocal({
        expTreeExclude: { ...tree, childInstances: tree.childInstances.filter((c) => c.uniqueId !== uniqueId) },
      })
    },
    [localArtifact.expTreeExclude, updateLocal]
  )
  const handleUpdateExcludeElement = useCallback(
    (uniqueId: string, updates: Partial<ElementInstance>) => {
      const tree = localArtifact.expTreeExclude || DEFAULT_TREE
      updateLocal({
        expTreeExclude: {
          ...tree,
          childInstances: tree.childInstances.map((c) =>
            c.uniqueId === uniqueId ? { ...c, ...updates } : c
          ),
        },
      })
    },
    [localArtifact.expTreeExclude, updateLocal]
  )

  const handleSubpopulationsChange = useCallback(
    (subpopulations: Artifact['subpopulations']) => updateLocal({ subpopulations }),
    [updateLocal]
  )

  // Library definition picker — delegated to useArtifactLibraryPicker so the
  // inclusion/exclusion dispatch logic is independently testable without
  // mounting the full workspace. Backend contract for the resulting
  // externalCqlElement tree node is covered by
  // CqlGenerationServiceLibraryIntegrationTest (PAT-102) +
  // ArtifactServiceLibraryRefIntegrationTest (PAT-103).
  const libraryPicker = useArtifactLibraryPicker(handleAddIncludeElement, handleAddExcludeElement)
  const handleBaseElementsChange = useCallback(
    (baseElements: Artifact['baseElements']) => updateLocal({ baseElements }),
    [updateLocal]
  )
  const handleRecommendationsChange = useCallback(
    (recommendations: Artifact['recommendations']) => updateLocal({ recommendations }),
    [updateLocal]
  )
  const handleParametersChange = useCallback(
    (parameters: Artifact['parameters']) => updateLocal({ parameters }),
    [updateLocal]
  )
  const handleErrorStatementChange = useCallback(
    (errorStatement: Artifact['errorStatement']) => updateLocal({ errorStatement }),
    [updateLocal]
  )

  const { data: externalLibraries = [], error: externalLibrariesError } = useExternalCqlList(localArtifact.id)

  // Create synthetic modifier definitions from external CQL functions
  const allModifiers = useMemo(() => {
    const externalModifiers: typeof modifiers = []
    for (const lib of externalLibraries) {
      if (!lib.details?.definitions) continue
      for (const def of lib.details.definitions) {
        // Only include definitions that could act as functions/modifiers
        // (definitions with a known return type that could transform data)
        if (def.resultType && def.resultType !== 'unknown') {
          // External CQL definitions can be used as modifiers with broad input types
          // We'll accept list types and single types as input
          externalModifiers.push({
            id: `extcql_${lib.id}_${def.name}`,
            name: `${def.name} (${lib.name})`,
            inputTypes: [def.resultType], // match same-type definitions
            returnType: def.resultType,
            cqlTemplate: `"${lib.name}"."${def.name}"`,
            cqlLibraryFunction: `${lib.name}.${def.name}`,
          })
        }
      }
    }
    return [...modifiers, ...externalModifiers]
  }, [modifiers, externalLibraries])

  const dynamicEntries = useMemo((): DynamicEntry[] => {
    const entries: DynamicEntry[] = []

    // Base Elements — use the stored returnType (already computed by BaseElements component)
    for (const be of (localArtifact.baseElements || [])) {
      entries.push({
        id: `be-${be.uniqueId}`,
        name: be.name,
        description: `Base Element (${be.returnType.replace(/_/g, ' ')})`,
        returnType: be.returnType,
        category: 'Base Elements',
        sourceType: 'baseElement',
        sourceId: be.uniqueId,
      })
    }

    // Parameters
    for (const p of (localArtifact.parameters || [])) {
      if (!p.name || !p.type) continue
      const rtMap: Record<string, string> = {
        Boolean: 'boolean', Integer: 'integer', Decimal: 'decimal',
        String: 'string', DateTime: 'system_date_time', Quantity: 'system_quantity',
        Code: 'system_code', Concept: 'system_concept', Time: 'system_time',
      }
      entries.push({
        id: `param-${p.uniqueId}`,
        name: p.name,
        description: `Parameter (${p.type})`,
        returnType: rtMap[p.type] || p.type.toLowerCase(),
        category: 'Parameters',
        sourceType: 'parameter',
        sourceId: p.uniqueId,
      })
    }

    // External CQL definitions
    for (const lib of externalLibraries) {
      if (lib.details?.definitions) {
        for (const def of lib.details.definitions) {
          entries.push({
            id: `ecql-${lib.id}-${def.name}`,
            name: def.name,
            description: def.resultType ? `Returns ${def.resultType}` : 'External CQL definition',
            returnType: def.resultType || 'unknown',
            category: 'External CQL',
            sourceType: 'externalCql',
            sourceId: `${lib.id}:${def.name}`,
            libraryName: lib.name,
          })
        }
      }
    }

    return entries
  }, [localArtifact.baseElements, localArtifact.parameters, externalLibraries])

  const includeTree = localArtifact.expTreeInclude || DEFAULT_TREE
  const excludeTree = localArtifact.expTreeExclude || DEFAULT_TREE
  const tabStatuses = useMemo(() => computeTabStatuses(localArtifact), [localArtifact])

  const dataLoadFailed = !!(templatesError || modifiersError || externalLibrariesError)

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <ArtifactWorkspaceHeader
        artifact={localArtifact}
        isDirty={isDirty}
        onBack={handleBack}
        onSave={handleSave}
        onSaveBeforeGenerate={handleSaveBeforeGenerate}
        onNameChange={handleNameChange}
        onUpdate={updateLocal}
      />

      {dataLoadFailed && (
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          {t('workspace.dataLoadFailed')}
        </Alert>
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', borderBottom: 1, borderColor: 'divider', px: 2 }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ flex: 1 }}
        >
        {TAB_LABELS.map((label, i) => {
          const si = tabStatuses[i]
          const errorIcon = si.errors.length > 0 ? (
            <Tooltip
              arrow
              title={
                <Box sx={{ '& ul': { m: 0, pl: 2 }, '& li': { mb: 0.25 } }}>
                  <Typography variant="caption" fontWeight={600}>
                    {t('workspace.validation.errorsFound', { count: si.errors.length })}
                  </Typography>
                  <ul>
                    {si.errors.slice(0, 8).map((e, ei) => (
                      <li key={ei}><Typography variant="caption">{t(e.key, e.params) as string}</Typography></li>
                    ))}
                    {si.errors.length > 8 && (
                      <li><Typography variant="caption">…+{si.errors.length - 8}</Typography></li>
                    )}
                  </ul>
                </Box>
              }
            >
              <ErrorIcon fontSize="small" color="warning" />
            </Tooltip>
          ) : undefined
          return (
            <Tab
              key={label}
              label={label}
              icon={
                errorIcon ?? (si.status === 'has-content' ? <CheckIcon fontSize="small" color="success" /> : undefined)
              }
              iconPosition="end"
              sx={{ minHeight: 48 }}
            />
          )
        })}
        </Tabs>
        <Tooltip title={t('workspace.twcoreTooltip')}>
          <FormControlLabel
            control={
              <Switch
                size="small"
                checked={twcoreMode}
                onChange={(e) => setTwcoreMode(e.target.checked)}
              />
            }
            label={
              <Stack direction="row" alignItems="center" spacing={0.5}>
                <PublicIcon fontSize="small" />
                <Typography variant="caption" fontWeight={600} noWrap>TWCORE</Typography>
              </Stack>
            }
            sx={{ ml: 1, mr: 1, flexShrink: 0 }}
          />
        </Tooltip>
      </Box>

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 2 }}>
        {tabStatuses[tab].errors.length > 0 && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            <Typography variant="body2" fontWeight={600} gutterBottom>
              {t('workspace.validation.errorsFound', { count: tabStatuses[tab].errors.length })}
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2, '& li': { mb: 0.25 } }}>
              {tabStatuses[tab].errors.map((e, ei) => (
                <li key={ei}><Typography variant="body2">{t(e.key, e.params) as string}</Typography></li>
              ))}
            </Box>
          </Alert>
        )}
        {tab === 0 && (
          <Box>
            <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
              <Button
                size="small"
                startIcon={<LibraryIcon />}
                onClick={libraryPicker.openForInclude}
                sx={{ textTransform: 'none' }}
              >
                {t('workspace.useLibraryDefinition')}
              </Button>
            </Stack>
            <ConjunctionGroup
              group={includeTree}
              treeName="Inclusions"
              templates={templates}
              modifiers={allModifiers}
              dynamicEntries={dynamicEntries}
              twcoreMode={twcoreMode}
              onUpdateGroup={handleUpdateInclude}
              onAddElement={handleAddIncludeElement}
              onRemoveElement={handleRemoveIncludeElement}
              onUpdateElement={handleUpdateIncludeElement}
            />
          </Box>
        )}
        {tab === 1 && (
          <Box>
            <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
              <Button
                size="small"
                startIcon={<LibraryIcon />}
                onClick={libraryPicker.openForExclude}
                sx={{ textTransform: 'none' }}
              >
                {t('workspace.useLibraryDefinition')}
              </Button>
            </Stack>
            <ConjunctionGroup
              group={excludeTree}
              treeName="Exclusions"
              templates={templates}
              modifiers={allModifiers}
              dynamicEntries={dynamicEntries}
              twcoreMode={twcoreMode}
              onUpdateGroup={handleUpdateExclude}
              onAddElement={handleAddExcludeElement}
              onRemoveElement={handleRemoveExcludeElement}
              onUpdateElement={handleUpdateExcludeElement}
            />
          </Box>
        )}
        <LibraryDefinitionPicker
          open={libraryPicker.target !== null}
          onClose={libraryPicker.close}
          onSelect={libraryPicker.handleSelect}
        />
        {tab === 2 && (
          <Subpopulations
            subpopulations={localArtifact.subpopulations || []}
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            twcoreMode={twcoreMode}
            onChange={handleSubpopulationsChange}
          />
        )}
        {tab === 3 && (
          <BaseElements
            baseElements={localArtifact.baseElements || []}
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            twcoreMode={twcoreMode}
            onChange={handleBaseElementsChange}
          />
        )}
        {tab === 4 && (
          <Recommendations
            recommendations={localArtifact.recommendations || []}
            subpopulations={localArtifact.subpopulations || []}
            onChange={handleRecommendationsChange}
          />
        )}
        {tab === 5 && (
          <Parameters
            parameters={localArtifact.parameters || []}
            onChange={handleParametersChange}
          />
        )}
        {tab === 6 && (
          <ErrorStatementEditor
            errorStatement={localArtifact.errorStatement}
            onChange={handleErrorStatementChange}
          />
        )}
        {tab === 7 && (
          <ExternalCql
            artifactId={localArtifact.id}
            onApplyToArtifact={(lib) => {
              const defs = lib.details?.definitions ?? []
              const SYSTEM_DEFS = SYSTEM_DEFINITIONS

              const makeRef = (d: { name: string; resultType?: string }) => ({
                uniqueId: generateId(),
                type: 'externalCqlRef' as const,
                name: d.name,
                returnType: d.resultType || 'System.Any',
                fields: [
                  { id: 'element_name', type: 'string', name: 'Element Name', value: d.name },
                  { id: 'reference_id', type: 'string', name: 'Reference ID', value: `${lib.id}:${d.name}`, static: true },
                  { id: 'library_name', type: 'string', name: 'Library', value: lib.name, static: true },
                  ...(lib.version ? [{ id: 'library_version', type: 'string', name: 'Library Version', value: lib.version, static: true }] : []),
                ],
                modifiers: [],
              })

              const wrapConj = (name: string, elements: ReturnType<typeof makeRef>[]) => ({
                id: 'And',
                name,
                conjunction: true,
                returnType: 'boolean',
                childInstances: elements,
              })

              const incDef = defs.find((d) => d.name === DEF_MEETS_INCLUSION)
              const excDef = defs.find((d) => d.name === DEF_MEETS_EXCLUSION)

              const updates: Partial<Artifact> = {}
              if (incDef) {
                updates.expTreeInclude = wrapConj(DEF_MEETS_INCLUSION, [
                  makeRef({ ...incDef, resultType: incDef.resultType || 'System.Boolean' }),
                ]) as ConjunctionGroupType
              }
              if (excDef) {
                updates.expTreeExclude = wrapConj(DEF_MEETS_EXCLUSION, [
                  makeRef({ ...excDef, resultType: excDef.resultType || 'System.Boolean' }),
                ]) as ConjunctionGroupType
              }

              const baseEls = defs
                .filter((d) => !SYSTEM_DEFS.has(d.name))
                .map((d) => ({
                  uniqueId: generateId(),
                  name: d.name,
                  type: 'externalCqlRef' as const,
                  returnType: d.resultType || 'System.Any',
                  fields: [
                    { id: 'element_name', type: 'string', name: 'Element Name', value: d.name },
                    { id: 'reference_id', type: 'string', name: 'Reference ID', value: `${lib.id}:${d.name}`, static: true },
                    { id: 'library_name', type: 'string', name: 'Library', value: lib.name, static: true },
                    ...(lib.version ? [{ id: 'library_version', type: 'string', name: 'Library Version', value: lib.version, static: true }] : []),
                  ],
                  modifiers: [],
                }))
              if (baseEls.length > 0) updates.baseElements = baseEls

              if (Object.keys(updates).length > 0) {
                updateLocal(updates)
                setTab(0) // Switch to Inclusions tab
              }
            }}
          />
        )}
        {tab === 8 && (
          <CqlPreviewPanel
            artifactId={localArtifact.id}
            isDirty={isDirty}
            onSaveBeforeGenerate={handleSaveBeforeGenerate}
          />
        )}
        {tab === 9 && <ArtifactTester artifactId={localArtifact.id} />}
        {tab === 10 && <ArtifactSummaryView artifact={localArtifact} />}
      </Box>

      {/* Unsaved changes confirmation dialog */}
      <Dialog open={showBackConfirm} onClose={() => setShowBackConfirm(false)}>
        <DialogTitle>{t('workspace.unsavedTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('workspace.unsavedMessage')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowBackConfirm(false)}>{tc('actions.cancel')}</Button>
          <Button
            color="error"
            onClick={() => { setShowBackConfirm(false); onBack() }}
          >
            {t('workspace.discardChanges')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Optimistic lock conflict dialog */}
      <Dialog open={showConflictDialog} onClose={() => { setShowConflictDialog(false); setReloadError(null) }}>
        <DialogTitle>{t('workspace.conflict.title')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('workspace.conflict.message')}
          </DialogContentText>
          {reloadError && (
            <Alert severity="error" sx={{ mt: 1.5 }}>{reloadError}</Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowConflictDialog(false)}>
            {t('workspace.conflict.keepEditing')}
          </Button>
          <Button
            color="primary"
            variant="contained"
            onClick={async () => {
              // Stash the current local edits so the user can recover them
              // after a conflict reload (or after a refetch failure).
              try {
                const recoveryKey = `authoring-recovery:${localArtifact.id}`
                window.localStorage?.setItem(recoveryKey, JSON.stringify({
                  savedAt: new Date().toISOString(),
                  artifact: localArtifact,
                }))
              } catch {
                // localStorage may be unavailable (private mode, quota) — continue.
              }
              try {
                const { data } = await refetchArtifact()
                if (data) {
                  setLocalArtifact(data)
                  setIsDirty(false)
                  onArtifactUpdate(data)
                  setReloadError(null)
                  setShowConflictDialog(false)
                } else {
                  setReloadError(t('workspace.conflict.reloadFailed'))
                }
              } catch {
                setReloadError(t('workspace.conflict.reloadFailed'))
              }
            }}
          >
            {t('workspace.conflict.reload')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Keyboard shortcut help dialog */}
      <Dialog open={showShortcutHelp} onClose={() => setShowShortcutHelp(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('workspace.shortcutsTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5}>
            {KEYBOARD_SHORTCUTS.map((sc) => (
              <Stack key={sc.key} direction="row" alignItems="center" justifyContent="space-between">
                <Chip label={sc.key} size="small" variant="outlined" sx={{ fontFamily: 'monospace', fontWeight: 600 }} />
                <Typography variant="body2" color="text.secondary">{sc.description}</Typography>
              </Stack>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowShortcutHelp(false)}>{tc('actions.close')}</Button>
        </DialogActions>
      </Dialog>
    </Card>
  )
}
