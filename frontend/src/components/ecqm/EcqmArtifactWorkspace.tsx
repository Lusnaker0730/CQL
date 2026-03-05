import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Dialog, DialogTitle, DialogContent, DialogContentText,
  DialogActions, Tab, Tabs, Snackbar, Alert,
} from '@mui/material'
import type { EcqmArtifact, EcqmArtifactRequest, PopulationGroup, SupplementalDataElement, StratifierElement } from '../../types/ecqm'
import { useUpdateEcqmArtifact, usePublishEcqm, useEcqmTemplates, useEcqmModifiers } from '../../hooks/useEcqm'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
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

export type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

export default function EcqmArtifactWorkspace({ artifact, onBack, onArtifactUpdate }: Props) {
  const { t } = useTranslation('ecqm')
  const [tab, setTab] = useState(0)
  const [snack, setSnack] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [showBackConfirm, setShowBackConfirm] = useState(false)

  // Local optimistic state — mirrors server artifact but updates immediately on user edits
  const [localOverrides, setLocalOverrides] = useState<Partial<EcqmArtifactRequest>>({})
  const localArtifact = useMemo<EcqmArtifact>(
    () => ({ ...artifact, ...localOverrides }) as EcqmArtifact,
    [artifact, localOverrides]
  )
  const updateMutation = useUpdateEcqmArtifact()
  const publishMutation = usePublishEcqm()
  const { data: templates = [] } = useEcqmTemplates()
  const { data: modifiers = [] } = useEcqmModifiers()

  const isDirty = saveStatus === 'dirty' || saveStatus === 'saving'
  useUnsavedChangesGuard(isDirty)

  // Clear local overrides when server artifact changes (refetch completed)
  const lastArtifactIdRef = useRef(artifact.updatedAt)
  useEffect(() => {
    if (artifact.updatedAt !== lastArtifactIdRef.current) {
      lastArtifactIdRef.current = artifact.updatedAt
      setLocalOverrides({})
    }
  }, [artifact.updatedAt])

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

    setSaveStatus('saving')
    updateMutation.mutate(
      { id: a.id, request },
      {
        onSuccess: () => {
          setSaveStatus('saved')
          onArtifactUpdateRef.current()
        },
        onError: () => {
          setSaveStatus('error')
          setSnack({ message: t('workspace.saveFailed'), severity: 'error' })
        },
      }
    )
  }, [updateMutation, t])

  const debouncedSave = useCallback((updates: Partial<EcqmArtifactRequest>) => {
    // Apply optimistic update immediately so UI reflects the change
    setLocalOverrides(prev => ({ ...prev, ...updates }))
    setSaveStatus('dirty')
    pendingRef.current = { ...(pendingRef.current || {}), ...updates }
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      if (pendingRef.current) {
        save(pendingRef.current)
        pendingRef.current = null
      }
    }, 1500)
  }, [save])

  // Immediate save (flush pending + save now)
  const flushSave = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
    if (pendingRef.current) {
      save(pendingRef.current)
      pendingRef.current = null
    }
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

  // Ctrl+S keyboard shortcut
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        flushSave()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [flushSave])

  const handlePublish = () => {
    // Flush any pending changes before publishing
    flushSave()
    publishMutation.mutate(artifact.id, {
      onSuccess: () => {
        setSnack({ message: t('workspace.publishSuccess'), severity: 'success' })
        onArtifactUpdate()
      },
      onError: () => { setSnack({ message: t('workspace.publishFailed'), severity: 'error' }) },
    })
  }

  const handleBack = useCallback(() => {
    if (saveStatus === 'dirty' || saveStatus === 'saving') {
      setShowBackConfirm(true)
    } else {
      onBack()
    }
  }, [saveStatus, onBack])

  const handleSaveAndLeave = useCallback(() => {
    setShowBackConfirm(false)
    if (timerRef.current) clearTimeout(timerRef.current)
    if (pendingRef.current) {
      const a = artifactRef.current
      const { id, publishedMeasureId, ownerUsername, createdAt, updatedAt, fhirVersion, ...base } = a
      const request: EcqmArtifactRequest = { ...base, ...pendingRef.current }
      pendingRef.current = null
      updateMutation.mutate(
        { id: a.id, request },
        { onSuccess: () => { onArtifactUpdateRef.current(); onBack() }, onError: () => onBack() }
      )
    } else {
      onBack()
    }
  }, [updateMutation, onBack])

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <EcqmArtifactWorkspaceHeader
        artifact={artifact}
        saveStatus={saveStatus}
        onBack={handleBack}
        onSave={flushSave}
        onPublish={handlePublish}
        publishing={publishMutation.isPending}
      />

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
          <Tab label={t('workspace.tabs.summary')} />
          <Tab label={t('workspace.tabs.populationGroups')} />
          <Tab label={t('workspace.tabs.baseElements')} />
          <Tab label={t('workspace.tabs.parameters')} />
          <Tab label={t('workspace.tabs.sde')} />
          <Tab label={t('workspace.tabs.stratifiers')} />
          <Tab label={t('workspace.tabs.externalCql')} />
          <Tab label={t('workspace.tabs.reviewCql')} />
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tab === 0 && (
          <EcqmSummaryTab artifact={localArtifact} onChange={debouncedSave} />
        )}
        {tab === 1 && (
          <EcqmPopulationGroupsTab
            artifact={localArtifact}
            templates={templates}
            modifiers={modifiers}
            onUpdateGroups={(groups: PopulationGroup[]) => debouncedSave({ populationGroups: groups })}
          />
        )}
        {tab === 2 && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ color: 'text.secondary' }}>
              {t('workspace.baseElementsPlaceholder')}
            </Box>
          </Box>
        )}
        {tab === 3 && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ color: 'text.secondary' }}>
              {t('workspace.parametersPlaceholder')}
            </Box>
          </Box>
        )}
        {tab === 4 && (
          <EcqmSdeTab
            supplementalData={localArtifact.supplementalData || []}
            supplementalDataGuidance={localArtifact.supplementalDataGuidance}
            templates={templates}
            modifiers={modifiers}
            onChange={(sde: SupplementalDataElement[]) => debouncedSave({ supplementalData: sde })}
            onGuidanceChange={(g: string) => debouncedSave({ supplementalDataGuidance: g })}
          />
        )}
        {tab === 5 && (
          <EcqmStratifiersTab
            stratifiers={localArtifact.stratifiers || []}
            templates={templates}
            modifiers={modifiers}
            onChange={(s: StratifierElement[]) => debouncedSave({ stratifiers: s })}
          />
        )}
        {tab === 6 && (
          <Box sx={{ p: 3, color: 'text.secondary' }}>
            {t('workspace.externalCqlPlaceholder')}
          </Box>
        )}
        {tab === 7 && (
          <EcqmCqlPreviewTab artifactId={artifact.id} onPublished={onArtifactUpdate} />
        )}
      </Box>

      {/* Unsaved changes confirmation dialog */}
      <Dialog open={showBackConfirm} onClose={() => setShowBackConfirm(false)}>
        <DialogTitle>{t('workspace.unsavedTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('workspace.unsavedMessage')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowBackConfirm(false)}>{t('common:actions.cancel')}</Button>
          <Button color="error" onClick={() => { setShowBackConfirm(false); onBack() }}>
            {t('workspace.discardChanges')}
          </Button>
          <Button variant="contained" onClick={handleSaveAndLeave}>
            {t('workspace.saveAndLeave')}
          </Button>
        </DialogActions>
      </Dialog>

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
