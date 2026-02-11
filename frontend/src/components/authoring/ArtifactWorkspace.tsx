import { useState, useCallback, useEffect, useMemo } from 'react'
import { Box, Card, Tabs, Tab, Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, Typography, Stack, Chip } from '@mui/material'
import { CheckCircle as CheckIcon, ErrorOutline as ErrorIcon } from '@mui/icons-material'
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
import { useUpdateArtifact } from '../../hooks/useAuthoring'
import { useTemplates } from '../../hooks/useTemplates'
import { useModifiers } from '../../hooks/useModifiers'
import { useExternalCqlList } from '../../hooks/useExternalCql'
import type { Artifact, ArtifactRequest, ConjunctionGroup as ConjunctionGroupType, ElementInstance } from '../../types/authoring'
import type { DynamicEntry } from './element-select/ElementSelectDropdown'

interface ArtifactWorkspaceProps {
  artifact: Artifact
  onBack: () => void
  onArtifactUpdate: (updated: Artifact) => void
}

const TAB_LABELS = [
  'Inclusions',
  'Exclusions',
  'Subpopulations',
  'Base Elements',
  'Recommendations',
  'Parameters',
  'Error Handling',
  'External CQL',
  'Review CQL',
  'Testing',
  'Summary',
]

const DEFAULT_TREE: ConjunctionGroupType = {
  id: 'And',
  name: 'And',
  conjunction: true,
  returnType: 'boolean',
  childInstances: [],
}

type TabStatus = 'empty' | 'has-content' | 'has-error'

function computeTabStatuses(a: Artifact): TabStatus[] {
  const treeHasContent = (tree?: ConjunctionGroupType) =>
    (tree?.childInstances?.length ?? 0) > 0

  const treeHasError = (tree?: ConjunctionGroupType): boolean => {
    if (!tree?.childInstances?.length) return false
    return tree.childInstances.some((el) => {
      const name = el.fields?.find((f) => f.id === 'element_name')?.value
      if (!name) return true // missing name
      if (el.modifiers?.length) {
        let cur = el.returnType
        for (const mod of el.modifiers) {
          if (!mod.inputTypes.includes(cur)) return true
          cur = mod.returnType
        }
      }
      return false
    })
  }

  const recsHasError = (a.recommendations || []).some((r) => !r.text.trim())
  const paramsHasError = (a.parameters || []).some((p) => !p.name.trim() || !p.type)
  const subpopsHasError = (a.subpopulations || []).filter((s) => !s.special).some((s) => !s.subpopulationName.trim())
  const baseHasError = (a.baseElements || []).some((be) => !be.name.trim())

  const status = (hasContent: boolean, hasError: boolean): TabStatus =>
    hasError ? 'has-error' : hasContent ? 'has-content' : 'empty'

  return [
    status(treeHasContent(a.expTreeInclude), treeHasError(a.expTreeInclude)),       // Inclusions
    status(treeHasContent(a.expTreeExclude), treeHasError(a.expTreeExclude)),       // Exclusions
    status((a.subpopulations || []).filter((s) => !s.special).length > 0, subpopsHasError), // Subpopulations
    status((a.baseElements || []).length > 0, baseHasError),                         // Base Elements
    status((a.recommendations || []).length > 0, recsHasError),                      // Recommendations
    status((a.parameters || []).length > 0, paramsHasError),                         // Parameters
    status(!!(a.errorStatement?.ifThenClauses?.length || a.errorStatement?.elseClause), false), // Error Handling
    'empty', // External CQL (managed separately)
    'empty', // Review CQL
    'empty', // Testing
    'empty', // Summary
  ]
}

export default function ArtifactWorkspace({
  artifact,
  onBack,
  onArtifactUpdate,
}: ArtifactWorkspaceProps) {
  const [tab, setTab] = useState(0)
  const [localArtifact, setLocalArtifact] = useState<Artifact>(artifact)
  const [isDirty, setIsDirty] = useState(false)
  const [showBackConfirm, setShowBackConfirm] = useState(false)
  const updateMutation = useUpdateArtifact()

  // Browser beforeunload guard
  useUnsavedChangesGuard(isDirty)
  const { data: templates = [] } = useTemplates()
  const { data: modifiers = [] } = useModifiers()
  const { pushState, undo, redo, reset: resetHistory } = useArtifactHistory()

  useEffect(() => {
    setLocalArtifact(artifact)
    setIsDirty(false)
    resetHistory()
  }, [artifact.id, resetHistory])

  const updateLocal = useCallback((updates: Partial<Artifact>) => {
    setLocalArtifact((prev) => {
      pushState(prev)
      return { ...prev, ...updates }
    })
    setIsDirty(true)
  }, [pushState])

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
    strengthOfRecommendation: localArtifact.strengthOfRecommendation,
    qualityOfEvidence: localArtifact.qualityOfEvidence,
    context: localArtifact.context,
    topic: localArtifact.topic,
    author: localArtifact.author,
    reviewer: localArtifact.reviewer,
    endorser: localArtifact.endorser,
    relatedArtifact: localArtifact.relatedArtifact,
  }), [localArtifact])

  const handleSave = useCallback(
    (request: ArtifactRequest) => {
      updateMutation.mutate(
        { id: localArtifact.id, request },
        {
          onSuccess: (updated) => {
            setLocalArtifact(updated)
            setIsDirty(false)
            onArtifactUpdate(updated)
          },
        }
      )
    },
    [localArtifact.id, updateMutation, onArtifactUpdate]
  )

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
        setTab(8) // Review CQL tab
        return
      }
      if (e.key === '/' || e.key === '?') {
        e.preventDefault()
        setShowShortcutHelp((prev) => !prev)
        return
      }
      // Ctrl+1-9 → tabs 0-8, Ctrl+0 → tab 10 (Summary)
      if (!isInput && e.key >= '0' && e.key <= '9') {
        e.preventDefault()
        const tabIndex = e.key === '0' ? 10 : parseInt(e.key) - 1
        if (tabIndex <= 10) setTab(tabIndex)
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

  const { data: externalLibraries = [] } = useExternalCqlList(localArtifact.id)

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

    // Base Elements
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

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <ArtifactWorkspaceHeader
        artifact={localArtifact}
        isDirty={isDirty}
        onBack={handleBack}
        onSave={handleSave}
        onNameChange={handleNameChange}
        onUpdate={updateLocal}
      />

      <Tabs
        value={tab}
        onChange={(_, v) => setTab(v)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
      >
        {TAB_LABELS.map((label, i) => {
          const st = tabStatuses[i]
          return (
            <Tab
              key={label}
              label={label}
              icon={
                st === 'has-error' ? <ErrorIcon fontSize="small" color="warning" /> :
                st === 'has-content' ? <CheckIcon fontSize="small" color="success" /> :
                undefined
              }
              iconPosition="end"
              sx={{ minHeight: 48 }}
            />
          )
        })}
      </Tabs>

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 2 }}>
        {tab === 0 && (
          <ConjunctionGroup
            group={includeTree}
            treeName="Inclusions"
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            onUpdateGroup={handleUpdateInclude}
            onAddElement={handleAddIncludeElement}
            onRemoveElement={handleRemoveIncludeElement}
            onUpdateElement={handleUpdateIncludeElement}
          />
        )}
        {tab === 1 && (
          <ConjunctionGroup
            group={excludeTree}
            treeName="Exclusions"
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            onUpdateGroup={handleUpdateExclude}
            onAddElement={handleAddExcludeElement}
            onRemoveElement={handleRemoveExcludeElement}
            onUpdateElement={handleUpdateExcludeElement}
          />
        )}
        {tab === 2 && (
          <Subpopulations
            subpopulations={localArtifact.subpopulations || []}
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            onChange={(subpopulations) => updateLocal({ subpopulations })}
          />
        )}
        {tab === 3 && (
          <BaseElements
            baseElements={localArtifact.baseElements || []}
            templates={templates}
            modifiers={allModifiers}
            dynamicEntries={dynamicEntries}
            onChange={(baseElements) => updateLocal({ baseElements })}
          />
        )}
        {tab === 4 && (
          <Recommendations
            recommendations={localArtifact.recommendations || []}
            subpopulations={localArtifact.subpopulations || []}
            onChange={(recommendations) => updateLocal({ recommendations })}
          />
        )}
        {tab === 5 && (
          <Parameters
            parameters={localArtifact.parameters || []}
            onChange={(parameters) => updateLocal({ parameters })}
          />
        )}
        {tab === 6 && (
          <ErrorStatementEditor
            errorStatement={localArtifact.errorStatement}
            onChange={(errorStatement) => updateLocal({ errorStatement })}
          />
        )}
        {tab === 7 && <ExternalCql artifactId={localArtifact.id} />}
        {tab === 8 && (
          <CqlPreviewPanel
            artifactId={localArtifact.id}
            isDirty={isDirty}
            onSaveBeforeGenerate={async () => {
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
            }}
          />
        )}
        {tab === 9 && <ArtifactTester artifactId={localArtifact.id} />}
        {tab === 10 && <ArtifactSummaryView artifact={localArtifact} />}
      </Box>

      {/* Unsaved changes confirmation dialog */}
      <Dialog open={showBackConfirm} onClose={() => setShowBackConfirm(false)}>
        <DialogTitle>Unsaved Changes</DialogTitle>
        <DialogContent>
          <DialogContentText>
            You have unsaved changes. Are you sure you want to leave? Your changes will be lost.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowBackConfirm(false)}>Cancel</Button>
          <Button
            color="error"
            onClick={() => { setShowBackConfirm(false); onBack() }}
          >
            Discard Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* Keyboard shortcut help dialog */}
      <Dialog open={showShortcutHelp} onClose={() => setShowShortcutHelp(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Keyboard Shortcuts</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5}>
            {[
              ['Ctrl + S', 'Save artifact'],
              ['Ctrl + Z', 'Undo last change'],
              ['Ctrl + Y', 'Redo last change'],
              ['Ctrl + G', 'Go to Review CQL'],
              ['Ctrl + 1–9', 'Switch to tab 1–9'],
              ['Ctrl + 0', 'Switch to Summary'],
              ['Ctrl + /', 'Toggle this help'],
            ].map(([key, desc]) => (
              <Stack key={key} direction="row" alignItems="center" justifyContent="space-between">
                <Chip label={key} size="small" variant="outlined" sx={{ fontFamily: 'monospace', fontWeight: 600 }} />
                <Typography variant="body2" color="text.secondary">{desc}</Typography>
              </Stack>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowShortcutHelp(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Card>
  )
}
