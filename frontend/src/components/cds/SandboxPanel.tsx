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
} from '@mui/material'
import {
  ViewModule as BuilderIcon,
  Code as JsonIcon,
} from '@mui/icons-material'
import { useCdsServices, useSandboxInvoke } from '../../hooks/useCdsHooks'
import type { CdsServiceDefinition, CdsCard, CdsResponse } from '../../types'
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

  const [selectedService, setSelectedService] = useState('')
  const [patientId, setPatientId] = useState(DEFAULT_PATIENT_ID)
  const [testDataJson, setTestDataJson] = useState(JSON.stringify(DEFAULT_PREFETCH, null, 2))
  const [sandboxResponse, setSandboxResponse] = useState<CdsResponse | null>(null)
  const [dataTab, setDataTab] = useState(0)

  const syncingRef = useRef(false)
  const initializedRef = useRef(false)

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )

  // Initialize: convert default prefetch to bundle entries and load into builder
  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true
      try {
        const bundleJson = prefetchToBundle(DEFAULT_PREFETCH)
        const entries = parseFromBundle(bundleJson)
        if (entries.length > 0) {
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // ignore
      }
    }
  }, [dispatch])

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
    </Stack>
  )
}
