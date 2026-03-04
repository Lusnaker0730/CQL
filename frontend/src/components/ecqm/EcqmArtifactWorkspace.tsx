import { useState, useCallback, useRef, useEffect } from 'react'
import { Box, Tab, Tabs, Snackbar, Alert } from '@mui/material'
import type { EcqmArtifact, EcqmArtifactRequest, PopulationGroup, SupplementalDataElement, StratifierElement } from '../../types/ecqm'
import { useUpdateEcqmArtifact, usePublishEcqm, useEcqmTemplates, useEcqmModifiers } from '../../hooks/useEcqm'
import EcqmArtifactWorkspaceHeader from './EcqmArtifactWorkspaceHeader'
import EcqmSummaryTab from './EcqmSummaryTab'
import EcqmPopulationGroupsTab from './EcqmPopulationGroupsTab'
import EcqmSdeTab from './EcqmSdeTab'
import EcqmStratifiersTab from './EcqmStratifiersTab'
import EcqmCqlPreviewTab from './EcqmCqlPreviewTab'

interface Props {
  artifact: EcqmArtifact
  onBack: () => void
  onArtifactUpdate: () => void
}

export default function EcqmArtifactWorkspace({ artifact, onBack, onArtifactUpdate }: Props) {
  const [tab, setTab] = useState(0)
  const [snack, setSnack] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)
  const updateMutation = useUpdateEcqmArtifact()
  const publishMutation = usePublishEcqm()
  const { data: templates = [] } = useEcqmTemplates()
  const { data: modifiers = [] } = useEcqmModifiers()

  // Use refs to avoid unstable callback dependencies on artifact/onArtifactUpdate
  const artifactRef = useRef(artifact)
  artifactRef.current = artifact
  const onArtifactUpdateRef = useRef(onArtifactUpdate)
  onArtifactUpdateRef.current = onArtifactUpdate

  // Auto-save timer
  const pendingRef = useRef<Partial<EcqmArtifactRequest> | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  const save = useCallback((updates: Partial<EcqmArtifactRequest>) => {
    const a = artifactRef.current
    const { id, publishedMeasureId, ownerUsername, createdAt, updatedAt, fhirVersion, ...base } = a
    const request: EcqmArtifactRequest = { ...base, ...updates }

    updateMutation.mutate(
      { id: a.id, request },
      {
        onSuccess: () => { onArtifactUpdateRef.current() },
        onError: () => { setSnack({ message: 'Save failed', severity: 'error' }) },
      }
    )
  }, [updateMutation])

  const debouncedSave = useCallback((updates: Partial<EcqmArtifactRequest>) => {
    pendingRef.current = { ...(pendingRef.current || {}), ...updates }
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      if (pendingRef.current) {
        save(pendingRef.current)
        pendingRef.current = null
      }
    }, 1500)
  }, [save])

  // Flush on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
      if (pendingRef.current) {
        save(pendingRef.current)
        pendingRef.current = null
      }
    }
  }, [save])

  const handlePublish = () => {
    publishMutation.mutate(artifact.id, {
      onSuccess: () => {
        setSnack({ message: 'Published successfully', severity: 'success' })
        onArtifactUpdate()
      },
      onError: () => { setSnack({ message: 'Publish failed', severity: 'error' }) },
    })
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <EcqmArtifactWorkspaceHeader
        artifact={artifact}
        onBack={onBack}
        onPublish={handlePublish}
        publishing={publishMutation.isPending}
      />

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
          <Tab label="Summary" />
          <Tab label="Population Groups" />
          <Tab label="Base Elements" />
          <Tab label="Parameters" />
          <Tab label="Supplemental Data" />
          <Tab label="Stratifiers" />
          <Tab label="External CQL" />
          <Tab label="Review CQL" />
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tab === 0 && (
          <EcqmSummaryTab artifact={artifact} onChange={debouncedSave} />
        )}
        {tab === 1 && (
          <EcqmPopulationGroupsTab
            artifact={artifact}
            templates={templates}
            modifiers={modifiers}
            onUpdateGroups={(groups: PopulationGroup[]) => debouncedSave({ populationGroups: groups })}
          />
        )}
        {tab === 2 && (
          <Box sx={{ p: 3 }}>
            {/* Base Elements — reuses CDS components via the shared expression tree model */}
            <Box sx={{ color: 'text.secondary' }}>
              Base Elements tab shares the same ConjunctionGroup-based builder as CDS authoring.
              Configure reusable expressions referenced by multiple populations.
            </Box>
          </Box>
        )}
        {tab === 3 && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ color: 'text.secondary' }}>
              Parameters tab for user-defined CQL parameters (reuses CDS Parameters component model).
            </Box>
          </Box>
        )}
        {tab === 4 && (
          <EcqmSdeTab
            supplementalData={artifact.supplementalData || []}
            supplementalDataGuidance={artifact.supplementalDataGuidance}
            templates={templates}
            modifiers={modifiers}
            onChange={(sde: SupplementalDataElement[]) => debouncedSave({ supplementalData: sde })}
            onGuidanceChange={(g: string) => debouncedSave({ supplementalDataGuidance: g })}
          />
        )}
        {tab === 5 && (
          <EcqmStratifiersTab
            stratifiers={artifact.stratifiers || []}
            templates={templates}
            modifiers={modifiers}
            onChange={(s: StratifierElement[]) => debouncedSave({ stratifiers: s })}
          />
        )}
        {tab === 6 && (
          <Box sx={{ p: 3, color: 'text.secondary' }}>
            External CQL tab — upload additional CQL libraries (reuses CDS External CQL component).
          </Box>
        )}
        {tab === 7 && (
          <EcqmCqlPreviewTab artifactId={artifact.id} onPublished={onArtifactUpdate} />
        )}
      </Box>

      <Snackbar
        open={!!snack} autoHideDuration={4000}
        onClose={() => setSnack(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setSnack(null)} severity={snack?.severity} variant="filled">
          {snack?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
