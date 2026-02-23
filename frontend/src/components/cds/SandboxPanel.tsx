import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
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
} from '@mui/material'
import {
  ViewModule as BuilderIcon,
  Code as JsonIcon,
  FolderOpen as LoadIcon,
  Save as SaveIcon,
  RestartAlt as ResetIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import {
  useCdsServices,
  useSandboxInvoke,
  useSandboxPresets,
  useCreateSandboxPreset,
  useUpdateSandboxPreset,
  useDeleteSandboxPreset,
} from '../../hooks/useCdsHooks'
import type { CdsServiceDefinition, CdsCard, CdsResponse, SandboxPresetResponse } from '../../types'
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
import Editor from '@monaco-editor/react'
import GradientButton from '../common/GradientButton'
import { DEFAULT_PATIENT_ID, DEFAULT_PREFETCH } from '../../constants/sandboxDefaults'
import { generateId } from '../../utils/validation'

const LOCALSTORAGE_KEY = 'cds-sandbox-draft'

function getIndicatorColor(indicator: string): 'error' | 'warning' | 'info' {
  switch (indicator) {
    case 'critical':
      return 'error'
    case 'warning':
      return 'warning'
    case 'info':
    default:
      return 'info'
  }
}

function loadDraft(): { patientId: string; testDataJson: string } | null {
  try {
    const raw = localStorage.getItem(LOCALSTORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (parsed.patientId && parsed.testDataJson) return parsed
  } catch {
    // ignore
  }
  return null
}

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

  // Phase 1: localStorage draft restore
  const draft = useMemo(() => loadDraft(), [])
  const defaultJson = JSON.stringify(DEFAULT_PREFETCH, null, 2)

  const [selectedService, setSelectedService] = useState('')
  const [patientId, setPatientId] = useState(draft?.patientId ?? DEFAULT_PATIENT_ID)
  const [testDataJson, setTestDataJson] = useState(draft?.testDataJson ?? defaultJson)
  const [sandboxResponse, setSandboxResponse] = useState<CdsResponse | null>(null)
  const [dataTab, setDataTab] = useState(0)

  // Preset UI state
  const [activePreset, setActivePreset] = useState<SandboxPresetResponse | null>(null)
  const [saveDialogOpen, setSaveDialogOpen] = useState(false)
  const [presetName, setPresetName] = useState('')
  const [presetDescription, setPresetDescription] = useState('')
  const [presetShared, setPresetShared] = useState(false)

  const syncingRef = useRef(false)
  const initializedRef = useRef(false)

  // Phase 1: localStorage debounced save
  const draftTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (draftTimerRef.current) clearTimeout(draftTimerRef.current)
    draftTimerRef.current = setTimeout(() => {
      try {
        localStorage.setItem(LOCALSTORAGE_KEY, JSON.stringify({ patientId, testDataJson }))
      } catch {
        // storage full or unavailable
      }
    }, 1000)
    return () => {
      if (draftTimerRef.current) clearTimeout(draftTimerRef.current)
    }
  }, [patientId, testDataJson])

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )

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

  // Sync: Visual Builder -> JSON (prefetch)
  useEffect(() => {
    if (syncingRef.current) return
    if (state.entries.length > 0) {
      syncingRef.current = true
      const bundleJson = serializeToBundle(state.entries)
      try {
        const prefetch = bundleToPrefetch(bundleJson)
        setTestDataJson(JSON.stringify(prefetch, null, 2))
      } catch {
        // ignore conversion errors
      }
      syncingRef.current = false
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
        if (syncingRef.current) return
        try {
          const prefetch = JSON.parse(val)
          const bundleJson = prefetchToBundle(prefetch)
          const entries = parseFromBundle(bundleJson)
          if (entries.length > 0) {
            syncingRef.current = true
            dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
            syncingRef.current = false
          }
        } catch {
          // Invalid JSON -- don't sync
        }
      }, 500)
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
          syncingRef.current = true
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
          syncingRef.current = false
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
      if (preset.patientId) setPatientId(preset.patientId)
      if (preset.serviceId) setSelectedService(preset.serviceId)
      setActivePreset(preset)
      loadPrefetchIntoBuilder(preset.prefetchJson)
    },
    [loadPrefetchIntoBuilder]
  )

  const handleResetDefault = useCallback(() => {
    setTestDataJson(defaultJson)
    setPatientId(DEFAULT_PATIENT_ID)
    setActivePreset(null)
    loadPrefetchIntoBuilder(defaultJson)
    localStorage.removeItem(LOCALSTORAGE_KEY)
  }, [defaultJson, loadPrefetchIntoBuilder])

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
      patientId,
      prefetchJson: testDataJson,
      shared: presetShared,
    }
    try {
      if (activePreset) {
        const updated = await updatePresetMutation.mutateAsync({ id: activePreset.id, request })
        setActivePreset(updated)
      } else {
        const created = await createPresetMutation.mutateAsync(request)
        setActivePreset(created)
      }
      showNotification(t('sandbox.presets.saveSuccess'), 'success')
      setSaveDialogOpen(false)
    } catch (error) {
      showNotification(t('sandbox.presets.saveFailed', { error: extractApiError(error) }), 'error')
    }
  }, [
    presetName,
    presetDescription,
    selectedService,
    patientId,
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
        if (activePreset?.id === id) setActivePreset(null)
        showNotification(t('sandbox.presets.deleteSuccess'), 'success')
      } catch (error) {
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
      const response = await sandboxMutation.mutateAsync({
        serviceId: selectedService,
        request: {
          serviceId: selectedService,
          hook: service.hook,
          hookInstance: generateId(),
          context: { patientId },
          testData,
        },
      })
      setSandboxResponse(response)
    } catch (error) {
      showNotification(t('sandbox.invokeFailed', { error: extractApiError(error) }), 'error')
    }
  }

  // Group presets: mine vs shared
  const currentUsername = useMemo(() => {
    try {
      const token = localStorage.getItem('token')
      if (!token) return ''
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.sub || ''
    } catch {
      return ''
    }
  }, [])

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

      <TextField
        label={t('sandbox.patientIdLabel')}
        value={patientId}
        onChange={(e) => setPatientId(e.target.value)}
        size="small"
        fullWidth
      />

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
        </Tabs>

        {dataTab === 0 && (
          <VisualBundleBuilder onDirty={() => {}} />
        )}

        {dataTab === 1 && (
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Editor
              height="350px"
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
      </Box>

      <GradientButton
        onClick={handleSandboxInvoke}
        disabled={!selectedService || sandboxMutation.isPending}
        startIcon={sandboxMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
        sx={{
          '&.Mui-disabled': {
            background: 'rgba(0,0,0,0.12)',
          },
        }}
      >
        {sandboxMutation.isPending ? t('sandbox.invoking') : t('sandbox.invokeButton')}
      </GradientButton>

      {sandboxMutation.isError && (
        <Alert severity="error">{t('sandbox.invokeError', { error: extractApiError(sandboxMutation.error) })}</Alert>
      )}

      {sandboxResponse && sandboxResponse.cards && sandboxResponse.cards.length > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            {t('sandbox.results', { count: sandboxResponse.cards.length })}
          </Typography>
          <Stack spacing={2}>
            {sandboxResponse.cards.map((card: CdsCard) => (
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
                      dangerouslySetInnerHTML={{
                        __html: card.detail.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>'),
                      }}
                    />
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
