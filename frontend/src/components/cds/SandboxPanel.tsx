import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { AUTOSAVE_NORMAL_MS, AUTOSAVE_FAST_MS } from '../../constants/timing'
import { EDITOR_HEIGHT_MEDIUM, EDITOR_HEIGHT_SMALL } from '../../constants/layout'
import { useTranslation } from 'react-i18next'

import {
  Box,
  Typography,
  Stack,
  TextField,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  useTheme,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControlLabel,
  Checkbox,
  ListSubheader,
  IconButton,
  Tooltip,
  Switch,
} from '@mui/material'
import {
  ViewModule as BuilderIcon,
  Code as JsonIcon,
  FolderOpen as LoadIcon,
  Save as SaveIcon,
  RestartAlt as ResetIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import CdsDebugPanel from './CdsDebugPanel'
import DebugModeSwitch from '../common/DebugModeSwitch'
import {
  useCdsServices,
  useSandboxInvoke,
  useSandboxPresets,
  useCreateSandboxPreset,
  useUpdateSandboxPreset,
  useDeleteSandboxPreset,
} from '../../hooks/useCdsHooks'
import type { CdsServiceDefinition, CdsCard, CdsResponse, SandboxPresetResponse } from '../../types'
import CriticalCardDialog from './CriticalCardDialog'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import {
  BundleBuilderProvider,
  useBundleBuilder,
  serializeToBundle,
  parseFromBundle,
} from '../../contexts/BundleBuilderContext'
import VisualBundleBuilder from '../testcase-builder/VisualBundleBuilder'
import { bundleToPrefetch, prefetchToBundle } from '../../utils/bundlePrefetchConverter'
import Editor from '../common/MonacoEditor'
import GradientButton from '../common/GradientButton'
import { DEFAULT_PATIENT_ID, DEFAULT_PREFETCH } from '../../constants/sandboxDefaults'
import { generateId, getStoredUsername } from '../../utils/validation'
import { getIndicatorColor, getStringContextFields, getObjectContextFields } from '../../constants/cdsHooks'
import type { CdsContextField } from '../../constants/cdsHooks'

const LOCALSTORAGE_KEY_BASE = 'cds-sandbox-draft'
const LEGACY_LOCALSTORAGE_KEY = 'cds-sandbox-draft'

// Per-user key so a shared workstation can't leak draft prefetch (which may
// contain test patient data) between users. Falls back to the bare key for
// anonymous sessions.
function getDraftStorageKey(username: string): string {
  return username && username !== 'anonymous'
    ? `${LOCALSTORAGE_KEY_BASE}:${username}`
    : LOCALSTORAGE_KEY_BASE
}

interface DraftData {
  contextFields?: Record<string, string>
  patientId?: string // backwards compat
  testDataJson: string
}

function loadDraft(key: string): DraftData | null {
  // Try the per-user key first, then the legacy un-scoped key once so the
  // user's existing draft isn't lost on first migration.
  for (const tryKey of [key, LEGACY_LOCALSTORAGE_KEY]) {
    try {
      const raw = localStorage.getItem(tryKey)
      if (!raw) continue
      const parsed = JSON.parse(raw)
      if (parsed.testDataJson) return parsed
    } catch {
      // ignore
    }
  }
  return null
}

interface ObjectFieldConfig {
  defaultValue: string
  tabKey: string
  descKey: string
}

const OBJECT_FIELD_CONFIG: Record<string, ObjectFieldConfig> = {
  draftOrders: {
    defaultValue: '{"resourceType":"Bundle","type":"collection","entry":[]}',
    tabKey: 'sandbox.tabDraftOrders',
    descKey: 'sandbox.draftOrdersDescription',
  },
  selections: {
    defaultValue: '[]',
    tabKey: 'sandbox.tabSelections',
    descKey: 'sandbox.selectionsDescription',
  },
  appointments: {
    defaultValue: '{"resourceType":"Bundle","type":"collection","entry":[]}',
    tabKey: 'sandbox.tabAppointments',
    descKey: 'sandbox.appointmentsDescription',
  },
}

const DEFAULT_OBJECT_VALUES = Object.fromEntries(
  Object.entries(OBJECT_FIELD_CONFIG).map(([k, v]) => [k, v.defaultValue])
)

export default function SandboxPanel() {
  return (
    <BundleBuilderProvider>
      <SandboxPanelInner />
    </BundleBuilderProvider>
  )
}

function SandboxPanelInner() {
  const theme = useTheme()
  const { data: servicesData } = useCdsServices()
  const sandboxMutation = useSandboxInvoke()
  const { state, dispatch } = useBundleBuilder()
  const { showNotification } = useNotification()
  const { t } = useTranslation('cds')

  // Presets hooks
  const { data: presets } = useSandboxPresets()
  const createPresetMutation = useCreateSandboxPreset()
  const updatePresetMutation = useUpdateSandboxPreset()
  const deletePresetMutation = useDeleteSandboxPreset()

  // Per-user localStorage scope (cds-sandbox-draft:<username>). Computed once
  // per mount; if username changes (rare — only on logout/login) we'll reload.
  const draftStorageKey = useMemo(() => getDraftStorageKey(getStoredUsername()), [])
  const draft = useMemo(() => loadDraft(draftStorageKey), [draftStorageKey])
  const defaultJson = JSON.stringify(DEFAULT_PREFETCH, null, 2)

  // Migrate old draft format: { patientId } → { contextFields: { patientId } }
  const initialContextFields = useMemo(() => {
    if (draft?.contextFields) return draft.contextFields
    if (draft?.patientId) return { patientId: draft.patientId }
    return { patientId: DEFAULT_PATIENT_ID }
  }, [draft])

  const [selectedService, setSelectedService] = useState('')
  const [contextFields, setContextFields] = useState<Record<string, string>>(initialContextFields)
  const [testDataJson, setTestDataJson] = useState(draft?.testDataJson ?? defaultJson)
  const [sandboxResponse, setSandboxResponse] = useState<CdsResponse | null>(null)
  const [dataTab, setDataTab] = useState(0)
  const [objectFieldJsons, setObjectFieldJsons] = useState<Record<string, string>>(DEFAULT_OBJECT_VALUES)
  const [debugMode, setDebugMode] = useState(false)
  const [dryRun, setDryRun] = useState(false)

  // Critical card queue state
  const [criticalQueue, setCriticalQueue] = useState<CdsCard[]>([])
  const [currentCritical, setCurrentCritical] = useState<CdsCard | null>(null)
  const [normalCards, setNormalCards] = useState<CdsCard[]>([])

  // Preset UI state
  const [activePreset, setActivePreset] = useState<SandboxPresetResponse | null>(null)
  const [saveDialogOpen, setSaveDialogOpen] = useState(false)
  const [presetName, setPresetName] = useState('')
  const [presetDescription, setPresetDescription] = useState('')
  const [presetShared, setPresetShared] = useState(false)

  // Source-tag for the bidirectional Builder ↔ JSON sync. The previous
  // `syncingRef` flag was set true and back to false synchronously inside the
  // same effect body, so the *opposite* effect never saw `true` (effects fire
  // after render). Tagging the source of the latest mutation and clearing it
  // only after the mirror effect runs avoids the ping-pong.
  const lastSyncSourceRef = useRef<'builder' | 'json' | null>(null)
  const initializedRef = useRef(false)

  // Guards post-await setState calls when the user navigates away mid-flight.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // Phase 1: localStorage debounced save
  const draftTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (draftTimerRef.current) clearTimeout(draftTimerRef.current)
    draftTimerRef.current = setTimeout(() => {
      try {
        localStorage.setItem(draftStorageKey, JSON.stringify({ contextFields, testDataJson }))
      } catch {
        // storage full or unavailable
      }
    }, AUTOSAVE_NORMAL_MS)
    return () => {
      if (draftTimerRef.current) clearTimeout(draftTimerRef.current)
    }
  }, [contextFields, testDataJson, draftStorageKey])

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )

  const selectedHook = useMemo(() => {
    const svc = services.find((s) => s.id === selectedService)
    return svc?.hook ?? ''
  }, [services, selectedService])

  // Dynamic context fields based on selected hook type
  const stringFields = useMemo(() => getStringContextFields(selectedHook), [selectedHook])
  const objectFields = useMemo(() => getObjectContextFields(selectedHook), [selectedHook])

  // Reset dataTab if it points to a tab that no longer exists when hook changes
  useEffect(() => {
    const maxTab = 1 + objectFields.length
    if (dataTab > maxTab) setDataTab(0)
  }, [objectFields.length, dataTab])

  // Initialize: convert initial prefetch to bundle entries and load into builder
  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true
      try {
        const prefetchData = JSON.parse(testDataJson)
        const bundleJson = prefetchToBundle(prefetchData)
        const entries = parseFromBundle(bundleJson)
        if (entries.length > 0) {
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // ignore
      }
    }
  }, [dispatch, testDataJson])

  // Sync: Visual Builder -> JSON (prefetch). Skip when the *latest* mutation
  // was a JSON edit feeding the builder — otherwise we'd echo the same data
  // back as JSON and the user's freshly typed JSON gets stomped by the
  // builder's serialization.
  useEffect(() => {
    if (lastSyncSourceRef.current === 'json') {
      lastSyncSourceRef.current = null
      return
    }
    if (state.entries.length > 0) {
      lastSyncSourceRef.current = 'builder'
      const bundleJson = serializeToBundle(state.entries)
      try {
        const prefetch = bundleToPrefetch(bundleJson)
        setTestDataJson(JSON.stringify(prefetch, null, 2))
      } catch {
        // ignore conversion errors
      }
    }
  }, [state.entries])

  // Debounced sync: JSON (prefetch) -> Visual Builder
  const jsonSyncTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (jsonSyncTimerRef.current) clearTimeout(jsonSyncTimerRef.current)
    }
  }, [])

  const handleJsonChange = useCallback(
    (value: string | undefined) => {
      const val = value || ''
      setTestDataJson(val)
      if (jsonSyncTimerRef.current) clearTimeout(jsonSyncTimerRef.current)
      jsonSyncTimerRef.current = setTimeout(() => {
        try {
          const prefetch = JSON.parse(val)
          const bundleJson = prefetchToBundle(prefetch)
          const entries = parseFromBundle(bundleJson)
          if (entries.length > 0) {
            // Tag this dispatch as JSON-originated so the builder→JSON effect
            // skips its mirror update and doesn't replace the user's typed JSON.
            lastSyncSourceRef.current = 'json'
            dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
          }
        } catch {
          // Invalid JSON -- don't sync
        }
      }, AUTOSAVE_FAST_MS)
    },
    [dispatch]
  )

  const loadPrefetchIntoBuilder = useCallback(
    (json: string) => {
      try {
        const prefetch = JSON.parse(json)
        const bundleJson = prefetchToBundle(prefetch)
        const entries = parseFromBundle(bundleJson)
        if (entries.length > 0) {
          // Tag as 'json' so the mirror-back effect skips one cycle.
          lastSyncSourceRef.current = 'json'
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // ignore
      }
    },
    [dispatch]
  )

  // Preset handlers
  const handleLoadPreset = useCallback(
    (preset: SandboxPresetResponse) => {
      setTestDataJson(preset.prefetchJson)
      if (preset.patientId) setContextFields((prev) => ({ ...prev, patientId: preset.patientId! }))
      if (preset.serviceId) setSelectedService(preset.serviceId)
      setActivePreset(preset)
      loadPrefetchIntoBuilder(preset.prefetchJson)
    },
    [loadPrefetchIntoBuilder]
  )

  const handleResetDefault = useCallback(() => {
    setTestDataJson(defaultJson)
    setContextFields({ patientId: DEFAULT_PATIENT_ID })
    setActivePreset(null)
    loadPrefetchIntoBuilder(defaultJson)
    localStorage.removeItem(draftStorageKey)
    // Clean up legacy un-scoped key on first reset post-migration so we don't
    // keep restoring it on subsequent mounts.
    if (draftStorageKey !== LEGACY_LOCALSTORAGE_KEY) {
      localStorage.removeItem(LEGACY_LOCALSTORAGE_KEY)
    }
  }, [defaultJson, loadPrefetchIntoBuilder, draftStorageKey])

  const handleOpenSaveDialog = useCallback(() => {
    if (activePreset) {
      setPresetName(activePreset.name)
      setPresetDescription(activePreset.description || '')
      setPresetShared(activePreset.shared)
    } else {
      setPresetName('')
      setPresetDescription('')
      setPresetShared(false)
    }
    setSaveDialogOpen(true)
  }, [activePreset])

  const handleSavePreset = useCallback(async () => {
    const request = {
      name: presetName,
      description: presetDescription || undefined,
      serviceId: selectedService || undefined,
      patientId: contextFields.patientId || '',
      prefetchJson: testDataJson,
      shared: presetShared,
    }
    try {
      if (activePreset) {
        const updated = await updatePresetMutation.mutateAsync({ id: activePreset.id, request })
        if (!isMountedRef.current) return
        setActivePreset(updated)
      } else {
        const created = await createPresetMutation.mutateAsync(request)
        if (!isMountedRef.current) return
        setActivePreset(created)
      }
      showNotification(t('sandbox.presets.saveSuccess'), 'success')
      setSaveDialogOpen(false)
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('sandbox.presets.saveFailed', { error: extractApiError(error) }), 'error')
    }
  }, [
    presetName,
    presetDescription,
    selectedService,
    contextFields,
    testDataJson,
    presetShared,
    activePreset,
    updatePresetMutation,
    createPresetMutation,
    showNotification,
    t,
  ])

  const handleDeletePreset = useCallback(
    async (id: number, e: React.MouseEvent) => {
      e.stopPropagation()
      try {
        await deletePresetMutation.mutateAsync(id)
        if (!isMountedRef.current) return
        if (activePreset?.id === id) setActivePreset(null)
        showNotification(t('sandbox.presets.deleteSuccess'), 'success')
      } catch (error) {
        if (!isMountedRef.current) return
        showNotification(t('sandbox.presets.deleteFailed', { error: extractApiError(error) }), 'error')
      }
    },
    [deletePresetMutation, activePreset, showNotification, t]
  )

  const handleSandboxInvoke = async () => {
    if (!selectedService) return
    const service = services.find((s) => s.id === selectedService)
    if (!service) return

    // Clear previous response so user sees fresh loading state
    setSandboxResponse(null)

    try {
      // Re-serialize from visual builder to ensure latest data
      if (dataTab === 0 && state.entries.length > 0) {
        const bundleJson = serializeToBundle(state.entries)
        const prefetch = bundleToPrefetch(bundleJson)
        setTestDataJson(JSON.stringify(prefetch, null, 2))
      }

      const testData = JSON.parse(testDataJson)

      // Build context from string fields
      const context: Record<string, string> = {}
      for (const field of stringFields) {
        if (contextFields[field.name]) {
          context[field.name] = contextFields[field.name]
        }
      }

      // Parse object-type context fields (draftOrders, selections, appointments)
      const objectContext: Record<string, unknown> = {}
      for (const field of objectFields) {
        const json = objectFieldJsons[field.name]
        if (json?.trim()) {
          try {
            objectContext[field.name] = JSON.parse(json)
          } catch {
            // Invalid JSON, skip
          }
        }
      }

      const response = await sandboxMutation.mutateAsync({
        serviceId: selectedService,
        request: {
          serviceId: selectedService,
          hook: service.hook,
          hookInstance: generateId(),
          context,
          testData,
          ...objectContext,
          debugMode,
          dryRun,
        },
      })
      if (!isMountedRef.current) return
      setSandboxResponse(response)

      // Partition cards: critical vs normal
      if (response.cards && response.cards.length > 0) {
        const critical = response.cards.filter((c) => c.indicator === 'critical')
        const normal = response.cards.filter((c) => c.indicator !== 'critical')
        setNormalCards(normal)
        if (critical.length > 0) {
          setCurrentCritical(critical[0])
          setCriticalQueue(critical.slice(1))
        } else {
          setCurrentCritical(null)
          setCriticalQueue([])
        }
      } else {
        setNormalCards([])
        setCurrentCritical(null)
        setCriticalQueue([])
      }
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('sandbox.invokeFailed', { error: extractApiError(error) }), 'error')
    }
  }

  const handleAcceptCritical = () => {
    if (criticalQueue.length > 0) {
      setCurrentCritical(criticalQueue[0])
      setCriticalQueue(criticalQueue.slice(1))
    } else {
      setCurrentCritical(null)
    }
  }

  const handleOverrideCritical = (_reason: string) => {
    if (criticalQueue.length > 0) {
      setCurrentCritical(criticalQueue[0])
      setCriticalQueue(criticalQueue.slice(1))
    } else {
      setCurrentCritical(null)
    }
  }

  // Group presets: mine vs shared
  const currentUsername = useMemo(() => getStoredUsername(), [])

  const myPresets = useMemo(
    () => (presets || []).filter((p) => p.ownerUsername === currentUsername),
    [presets, currentUsername]
  )
  const sharedPresets = useMemo(
    () => (presets || []).filter((p) => p.ownerUsername !== currentUsername && p.shared),
    [presets, currentUsername]
  )

  return (
    <Stack spacing={2}>
      <Alert severity="info">
        {t('sandbox.description')}
      </Alert>

      <FormControl fullWidth size="small">
        <InputLabel>{t('sandbox.serviceLabel')}</InputLabel>
        <Select value={selectedService} onChange={(e) => setSelectedService(e.target.value)} label={t('sandbox.serviceLabel')}>
          {services.map((service: CdsServiceDefinition) => (
            <MenuItem key={service.id} value={service.id}>
              {service.title} ({service.hook})
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {stringFields.map((field: CdsContextField) => (
        <TextField
          key={field.name}
          label={t(`sandbox.${field.name}Label`)}
          placeholder={t(`sandbox.${field.name}Placeholder`, { defaultValue: '' })}
          value={contextFields[field.name] || ''}
          onChange={(e) => setContextFields((prev) => ({ ...prev, [field.name]: e.target.value }))}
          size="small"
          fullWidth
          required={field.required}
        />
      ))}

      {/* Preset toolbar */}
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>{t('sandbox.presets.loadPreset')}</InputLabel>
          <Select
            value=""
            onChange={(e) => {
              const preset = (presets || []).find((p) => p.id === Number(e.target.value))
              if (preset) handleLoadPreset(preset)
            }}
            label={t('sandbox.presets.loadPreset')}
            startAdornment={<LoadIcon sx={{ mr: 0.5, color: 'action.active' }} />}
          >
            {myPresets.length > 0 && <ListSubheader>{t('sandbox.presets.myPresets')}</ListSubheader>}
            {myPresets.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                <Stack direction="row" alignItems="center" justifyContent="space-between" width="100%">
                  <Typography variant="body2" noWrap sx={{ flex: 1 }}>
                    {p.name}
                  </Typography>
                  <Tooltip title={t('common:delete')}>
                    <IconButton
                      size="small"
                      onClick={(e) => handleDeletePreset(p.id, e)}
                      sx={{ ml: 1 }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </MenuItem>
            ))}
            {sharedPresets.length > 0 && <ListSubheader>{t('sandbox.presets.sharedPresets')}</ListSubheader>}
            {sharedPresets.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                <Typography variant="body2" noWrap>
                  {p.name}
                  <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                    ({p.ownerUsername})
                  </Typography>
                </Typography>
              </MenuItem>
            ))}
            {myPresets.length === 0 && sharedPresets.length === 0 && (
              <MenuItem disabled>{t('sandbox.presets.noPresets')}</MenuItem>
            )}
          </Select>
        </FormControl>

        <Button
          variant="outlined"
          size="small"
          startIcon={<SaveIcon />}
          onClick={handleOpenSaveDialog}
        >
          {activePreset ? t('sandbox.presets.overwrite') : t('sandbox.presets.savePreset')}
        </Button>

        <Button
          variant="outlined"
          size="small"
          startIcon={<ResetIcon />}
          onClick={handleResetDefault}
        >
          {t('sandbox.presets.resetDefault')}
        </Button>

        {activePreset && (
          <Chip
            label={t('sandbox.presets.loadedChip', { name: activePreset.name })}
            size="small"
            color="primary"
            variant="outlined"
            onDelete={() => setActivePreset(null)}
          />
        )}
      </Stack>

      <Box>
        <Tabs value={dataTab} onChange={(_, v) => setDataTab(v)} sx={{ mb: 1 }}>
          <Tab icon={<BuilderIcon />} iconPosition="start" label={t('sandbox.tabVisualBuilder')} sx={{ textTransform: 'none', minHeight: 42 }} />
          <Tab icon={<JsonIcon />} iconPosition="start" label={t('sandbox.tabJsonPrefetch')} sx={{ textTransform: 'none', minHeight: 42 }} />
          {objectFields.map((field) => (
            <Tab
              key={field.name}
              icon={<JsonIcon />}
              iconPosition="start"
              label={t(OBJECT_FIELD_CONFIG[field.name]?.tabKey ?? field.name)}
              sx={{ textTransform: 'none', minHeight: 42 }}
            />
          ))}
        </Tabs>

        {dataTab === 0 && (
          <VisualBundleBuilder onDirty={() => {}} />
        )}

        {dataTab === 1 && (
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Editor
              height={EDITOR_HEIGHT_MEDIUM}
              language="json"
              theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
              value={testDataJson}
              onChange={handleJsonChange}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 2,
              }}
            />
          </Box>
        )}

        {objectFields.map((field, idx) =>
          dataTab === 2 + idx ? (
            <Box key={field.name}>
              <Alert severity="info" sx={{ mb: 1 }}>
                {t(OBJECT_FIELD_CONFIG[field.name]?.descKey ?? field.name)}
              </Alert>
              <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
                <Editor
                  height={EDITOR_HEIGHT_SMALL}
                  language="json"
                  theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
                  value={objectFieldJsons[field.name] || DEFAULT_OBJECT_VALUES[field.name] || '{}'}
                  onChange={(v) => setObjectFieldJsons((prev) => ({ ...prev, [field.name]: v || '' }))}
                  options={{
                    minimap: { enabled: false },
                    fontSize: 13,
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                    tabSize: 2,
                  }}
                />
              </Box>
            </Box>
          ) : null
        )}
      </Box>

      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
        <Stack direction="row" spacing={1}>
          <DebugModeSwitch checked={debugMode} onChange={setDebugMode} label={t('sandbox.debugMode')} disabled={dryRun} />
          <FormControlLabel
            control={
              <Switch
                size="small"
                checked={dryRun}
                onChange={(e) => setDryRun(e.target.checked)}
              />
            }
            label={
              <Typography variant="body2" color={dryRun ? 'warning.main' : 'text.secondary'}>
                {t('sandbox.dryRun')}
              </Typography>
            }
          />
        </Stack>
        <GradientButton
          onClick={handleSandboxInvoke}
          disabled={!selectedService || sandboxMutation.isPending}
          startIcon={sandboxMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
        >
          {sandboxMutation.isPending ? t('sandbox.invoking') : (dryRun ? t('sandbox.dryRunButton') : t('sandbox.invokeButton'))}
        </GradientButton>
      </Stack>

      {sandboxMutation.isError && (
        <Alert severity="error">{t('sandbox.invokeError', { error: extractApiError(sandboxMutation.error) })}</Alert>
      )}

      {sandboxResponse?.debug && <CdsDebugPanel debug={sandboxResponse.debug} />}

      {/* Critical Card Dialog */}
      {currentCritical && (
        <CriticalCardDialog
          open={!!currentCritical}
          card={currentCritical}
          onAccept={handleAcceptCritical}
          onOverride={handleOverrideCritical}
        />
      )}

      {sandboxResponse && normalCards.length > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            {t('sandbox.results', { count: sandboxResponse.cards.length })}
          </Typography>
          <Stack spacing={2}>
            {normalCards.map((card: CdsCard) => (
              <Card
                key={card.uuid}
                variant="outlined"
                sx={{
                  borderLeft: 4,
                  borderLeftColor: `${getIndicatorColor(card.indicator)}.main`,
                }}
              >
                <CardContent>
                  <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                    <Chip
                      label={card.indicator}
                      size="small"
                      color={getIndicatorColor(card.indicator)}
                    />
                    <Typography variant="subtitle1" fontWeight="bold">
                      {card.summary}
                    </Typography>
                  </Stack>
                  {card.detail && (
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ whiteSpace: 'pre-line' }}
                    >
                      {card.detail.split(/\*\*(.+?)\*\*/g).map((part, i) =>
                        i % 2 === 1 ? <strong key={i}>{part}</strong> : part,
                      )}
                    </Typography>
                  )}
                  {card.suggestions && card.suggestions.length > 0 && (
                    <Box mt={1}>
                      <Typography variant="caption" fontWeight="bold">
                        {t('sandbox.suggestions')}
                      </Typography>
                      {card.suggestions.map((s) => (
                        <Chip key={s.uuid} label={s.label} size="small" sx={{ ml: 0.5 }} variant="outlined" />
                      ))}
                    </Box>
                  )}
                </CardContent>
              </Card>
            ))}
          </Stack>
        </Box>
      )}

      {sandboxResponse?.systemActions && sandboxResponse.systemActions.length > 0 && (
        <Alert severity="info">
          <Typography variant="subtitle2">{t('sandbox.systemActions')}</Typography>
          {sandboxResponse.systemActions.map((action, i) => (
            <Typography key={`${action.type}-${i}`} variant="body2">
              {action.type}: {action.description || t('invoke.noDescription')}
            </Typography>
          ))}
        </Alert>
      )}

      {/* Save Preset Dialog */}
      <Dialog open={saveDialogOpen} onClose={() => setSaveDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('sandbox.presets.saveDialogTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label={t('sandbox.presets.nameLabel')}
              placeholder={t('sandbox.presets.namePlaceholder')}
              value={presetName}
              onChange={(e) => setPresetName(e.target.value)}
              size="small"
              fullWidth
              required
              autoFocus
            />
            <TextField
              label={t('sandbox.presets.descriptionLabel')}
              placeholder={t('sandbox.presets.descriptionPlaceholder')}
              value={presetDescription}
              onChange={(e) => setPresetDescription(e.target.value)}
              size="small"
              fullWidth
              multiline
              rows={2}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={presetShared}
                  onChange={(e) => setPresetShared(e.target.checked)}
                />
              }
              label={t('sandbox.presets.sharedLabel')}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSaveDialogOpen(false)}>{t('common:cancel')}</Button>
          <GradientButton
            onClick={handleSavePreset}
            disabled={!presetName.trim() || createPresetMutation.isPending || updatePresetMutation.isPending}
          >
            {createPresetMutation.isPending || updatePresetMutation.isPending
              ? t('sandbox.presets.saving')
              : activePreset
                ? t('sandbox.presets.update')
                : t('sandbox.presets.save')}
          </GradientButton>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
