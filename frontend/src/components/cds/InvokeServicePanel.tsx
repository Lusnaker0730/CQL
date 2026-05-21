import { useState, useEffect, useMemo, useRef } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useTranslation } from 'react-i18next'
import { setCqlContent } from '../../store/editorSlice'
import type { RootState } from '../../store'

import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  Card,
  CardContent,
  CardActions,
  Chip,
  CircularProgress,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Divider,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the old Proxy mock).
import InfoIcon from '@mui/icons-material/Info'
import WarningIcon from '@mui/icons-material/Warning'
import ErrorIcon from '@mui/icons-material/Error'
import CheckIcon from '@mui/icons-material/CheckCircle'
import CdsDebugPanel from './CdsDebugPanel'
import DebugModeSwitch from '../common/DebugModeSwitch'
import { alpha } from '@mui/material/styles'
import {
  useCdsServices,
  useInvokeCdsService,
  useCdsServiceConfigs,
  useSubmitCdsFeedback,
} from '../../hooks/useCdsHooks'
import type { CdsServiceDefinition, CdsCard, CdsResponse } from '../../types'
import CriticalCardDialog from './CriticalCardDialog'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import FhirServerUrlField from '../common/FhirServerUrlField'
import GradientButton from '../common/GradientButton'
import { FHIR_SERVER_PRESETS } from '../../constants/fhirServers'
import { generateId } from '../../utils/validation'
import {
  FEEDBACK_ACCEPTED,
  FEEDBACK_OVERRIDDEN,
  FEEDBACK_OVERRIDE_CODE,
  FEEDBACK_OVERRIDE_DEFAULT_DISPLAY,
  getIndicatorColor,
  getStringContextFields,
} from '../../constants/cdsHooks'
import type { CdsContextField } from '../../constants/cdsHooks'

// CDS Cards links may come from third-party services. Reject anything that
// isn't an absolute http(s) URL — defends against `javascript:` / `data:` /
// relative-protocol injections from a hostile service definition.
function isSafeHttpUrl(raw: string | undefined): boolean {
  if (!raw) return false
  try {
    const parsed = new URL(raw)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

function getIndicatorIcon(indicator: string) {
  switch (indicator) {
    case 'critical':
      return <ErrorIcon color="error" />
    case 'warning':
      return <WarningIcon color="warning" />
    case 'info':
    default:
      return <InfoIcon color="info" />
  }
}

export default function InvokeServicePanel() {
  const { data: servicesData, isLoading: loadingServices, isError: servicesError } = useCdsServices()
  const { data: serviceConfigs } = useCdsServiceConfigs()
  const dispatch = useDispatch()
  const cqlContent = useSelector((state: RootState) => state.editor.cqlContent)
  const invokeMutation = useInvokeCdsService()
  const feedbackMutation = useSubmitCdsFeedback()
  const { t } = useTranslation('cds')
  const { t: tc } = useTranslation('common')

  const { showNotification } = useNotification()

  const [selectedService, setSelectedService] = useState<string>('')
  const [contextFields, setContextFields] = useState<Record<string, string>>({})
  const [fhirServer, setFhirServer] = useState(FHIR_SERVER_PRESETS[0].url)
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [cdsResponse, setCdsResponse] = useState<CdsResponse | null>(null)
  const [debugMode, setDebugMode] = useState(false)
  const [overrideDialogOpen, setOverrideDialogOpen] = useState(false)
  const [overrideCardUuid, setOverrideCardUuid] = useState('')
  const [overrideReason, setOverrideReason] = useState('')

  // Critical card queue state
  const [criticalQueue, setCriticalQueue] = useState<CdsCard[]>([])
  const [currentCritical, setCurrentCritical] = useState<CdsCard | null>(null)
  const [normalCards, setNormalCards] = useState<CdsCard[]>([])

  // Guards post-await setState calls when the user navigates away mid-flight.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // Tracks the CQL we last auto-loaded into the editor so we don't silently
  // overwrite user edits when they switch the service dropdown.
  const lastAutoLoadedCqlRef = useRef<string>('')

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )
  const allCards = useMemo(() => cdsResponse?.cards ?? [], [cdsResponse?.cards])

  const selectedHook = useMemo(() => {
    const svc = services.find((s) => s.id === selectedService)
    return svc?.hook ?? ''
  }, [services, selectedService])

  const stringFields = useMemo(() => getStringContextFields(selectedHook), [selectedHook])

  // Auto-load the selected service's CQL into the editor, but only when the
  // editor is "clean" — empty, or still showing whatever we last auto-loaded.
  // This prevents silently discarding user edits when they switch services.
  useEffect(() => {
    if (!selectedService || !serviceConfigs) return
    const config = serviceConfigs.find((s) => s.id === selectedService)
    if (!config?.cqlContent) return
    const editorIsClean =
      !cqlContent.trim() ||
      cqlContent === lastAutoLoadedCqlRef.current ||
      cqlContent === config.cqlContent
    if (editorIsClean) {
      dispatch(setCqlContent(config.cqlContent))
      lastAutoLoadedCqlRef.current = config.cqlContent
    } else {
      showNotification(t('invoke.editorPreservedNotice'), 'info')
    }
    // cqlContent intentionally excluded — re-running on every keystroke would
    // either no-op or fight the user's typing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedService, serviceConfigs, dispatch])

  const handleInvoke = async () => {
    if (!selectedService) return

    const service = services.find((s) => s.id === selectedService)
    if (!service) return

    // Clear any previous response so the user sees a fresh loading state
    // rather than stale cards lingering during the await.
    setCdsResponse(null)

    try {
      // Build context from dynamic string fields
      const context: Record<string, string> = {}
      for (const field of stringFields) {
        if (contextFields[field.name]) {
          context[field.name] = contextFields[field.name]
        }
      }

      const response = await invokeMutation.mutateAsync({
        serviceId: selectedService,
        request: {
          hook: service.hook,
          hookInstance: generateId(),
          fhirServer,
          context,
          debugMode,
        },
      })
      if (!isMountedRef.current) return
      setCdsResponse(response)

      // Partition cards: critical vs normal
      if (response.cards && response.cards.length > 0) {
        const critical = response.cards.filter((c: CdsCard) => c.indicator === 'critical')
        const normal = response.cards.filter((c: CdsCard) => c.indicator !== 'critical')
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
      showNotification(t('invoke.invokeFailed', { error: extractApiError(error) }), 'error')
    }
  }

  const advanceCriticalQueue = () => {
    if (criticalQueue.length > 0) {
      setCurrentCritical(criticalQueue[0])
      setCriticalQueue(criticalQueue.slice(1))
    } else {
      setCurrentCritical(null)
    }
  }

  // Critical-card flows must record feedback just like normal cards. Without
  // this, the highest-severity decisions are missing from analytics.
  const submitCriticalFeedback = async (
    cardUuid: string,
    outcome: typeof FEEDBACK_ACCEPTED | typeof FEEDBACK_OVERRIDDEN,
    reason?: string,
  ) => {
    if (!selectedService) return
    try {
      await feedbackMutation.mutateAsync({
        serviceId: selectedService,
        feedback: {
          feedback: [
            outcome === FEEDBACK_ACCEPTED
              ? { card: cardUuid, outcome: FEEDBACK_ACCEPTED }
              : {
                  card: cardUuid,
                  outcome: FEEDBACK_OVERRIDDEN,
                  overrideReason: {
                    code: FEEDBACK_OVERRIDE_CODE,
                    display: reason?.trim() || FEEDBACK_OVERRIDE_DEFAULT_DISPLAY,
                  },
                },
          ],
        },
      })
    } catch {
      // Surface as a non-blocking warning; the queue still advances so the
      // clinician isn't trapped by a transient feedback-endpoint failure.
      showNotification(t('invoke.criticalFeedbackFailed'), 'warning')
    }
  }

  const handleAcceptCritical = async () => {
    const cardUuid = currentCritical?.uuid
    if (cardUuid) await submitCriticalFeedback(cardUuid, FEEDBACK_ACCEPTED)
    advanceCriticalQueue()
  }

  const handleOverrideCritical = async (reason: string) => {
    const cardUuid = currentCritical?.uuid
    if (cardUuid) await submitCriticalFeedback(cardUuid, FEEDBACK_OVERRIDDEN, reason)
    advanceCriticalQueue()
  }

  const handleAccept = async (cardUuid: string) => {
    try {
      await feedbackMutation.mutateAsync({
        serviceId: selectedService,
        feedback: {
          feedback: [{ card: cardUuid, outcome: FEEDBACK_ACCEPTED }],
        },
      })
      showNotification(t('invoke.feedbackAccepted'), 'success')
    } catch {
      showNotification(t('invoke.feedbackFailed'), 'error')
    }
  }

  const handleOverrideClick = (cardUuid: string) => {
    setOverrideCardUuid(cardUuid)
    setOverrideReason('')
    setOverrideDialogOpen(true)
  }

  const handleOverrideSubmit = async () => {
    try {
      await feedbackMutation.mutateAsync({
        serviceId: selectedService,
        feedback: {
          feedback: [
            {
              card: overrideCardUuid,
              outcome: FEEDBACK_OVERRIDDEN,
              overrideReason: { code: FEEDBACK_OVERRIDE_CODE, display: overrideReason || FEEDBACK_OVERRIDE_DEFAULT_DISPLAY },
            },
          ],
        },
      })
      setOverrideDialogOpen(false)
      showNotification(t('invoke.feedbackOverridden'), 'success')
    } catch {
      showNotification(t('invoke.feedbackFailed'), 'error')
    }
  }

  return (
    <Stack spacing={2}>
      <FormControl fullWidth size="small">
        {/* Explicit id/labelId so React Testing Library's getByLabelText can
            resolve the Select via its associated InputLabel. MUI's auto-
            generated ids work in production but jsdom tests can't follow
            them reliably. */}
        <InputLabel id="invoke-service-label">{t('invoke.serviceLabel')}</InputLabel>
        <Select labelId="invoke-service-label" value={selectedService} onChange={(e) => setSelectedService(e.target.value)} label={t('invoke.serviceLabel')}>
          {services.map((service: CdsServiceDefinition) => (
            <MenuItem key={service.id} value={service.id}>
              {service.title} ({service.hook})
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {selectedService && (() => {
        const service = services.find((s) => s.id === selectedService)
        if (!service) return null
        return (
          <Box>
            <Typography variant="body2" color="text.secondary">
              {service.description}
            </Typography>
          </Box>
        )
      })()}

      <FhirServerUrlField
        value={fhirServer}
        onChange={(value) => {
          setFhirServer(value)
          setFhirServerError(null)
        }}
        error={!!fhirServerError}
        helperText={fhirServerError}
      />

      {stringFields.map((field: CdsContextField) => (
        <TextField
          key={field.name}
          label={t(`sandbox.${field.name}Label`, { defaultValue: field.name })}
          placeholder={t(`sandbox.${field.name}Placeholder`, { defaultValue: '' })}
          value={contextFields[field.name] || ''}
          onChange={(e) => setContextFields((prev) => ({ ...prev, [field.name]: e.target.value }))}
          size="small"
          fullWidth
          required={field.required}
        />
      ))}

      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
        <DebugModeSwitch checked={debugMode} onChange={setDebugMode} label={t('sandbox.debugMode')} />
        <GradientButton
          onClick={handleInvoke}
          disabled={!selectedService || invokeMutation.isPending}
          startIcon={invokeMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
        >
          {invokeMutation.isPending ? t('invoke.invoking') : t('invoke.invokeButton')}
        </GradientButton>
      </Stack>

      {cdsResponse?.debug && <CdsDebugPanel debug={cdsResponse.debug} />}

      <Divider />

      {loadingServices && <CircularProgress />}

      {servicesError && (
        <Alert severity="error">{t('invoke.loadError')}</Alert>
      )}

      {invokeMutation.isError && (
        <Alert severity="error">{t('invoke.invokeError', { error: extractApiError(invokeMutation.error) })}</Alert>
      )}

      {/* Critical Card Dialog */}
      {currentCritical && (
        <CriticalCardDialog
          open={!!currentCritical}
          card={currentCritical}
          onAccept={handleAcceptCritical}
          onOverride={handleOverrideCritical}
        />
      )}

      {normalCards.length > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            {t('invoke.responseCards', { count: allCards.length })}
          </Typography>
          <Stack spacing={2}>
            {normalCards.map((card) => (
              <Card
                key={card.uuid}
                variant="outlined"
                sx={(theme) => ({
                  borderLeft: 4,
                  borderLeftColor: `${getIndicatorColor(card.indicator)}.main`,
                  transition: 'all 0.25s ease',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: `0 6px 20px ${alpha(theme.palette.primary.main, 0.12)}`,
                  },
                })}
              >
                <CardContent>
                  <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                    {getIndicatorIcon(card.indicator)}
                    <Typography variant="subtitle1" fontWeight="bold" sx={{ color: 'text.primary' }}>
                      {card.summary}
                    </Typography>
                    <Chip
                      label={card.indicator}
                      size="small"
                      color={getIndicatorColor(card.indicator)}
                    />
                  </Stack>

                  {card.detail && (
                    <Typography variant="body2" color="text.secondary" paragraph>
                      {card.detail}
                    </Typography>
                  )}

                  {card.source && (
                    <Typography variant="caption" color="text.secondary">
                      {t('invoke.source', { label: card.source.label })}
                    </Typography>
                  )}

                  {card.suggestions && card.suggestions.length > 0 && (
                    <Box mt={2}>
                      <Typography variant="subtitle2">{t('invoke.suggestions')}</Typography>
                      <List dense>
                        {card.suggestions.map((suggestion) => (
                          <ListItem key={suggestion.uuid}>
                            <ListItemIcon>
                              {suggestion.isRecommended ? <CheckIcon color="success" /> : <InfoIcon color="action" />}
                            </ListItemIcon>
                            <ListItemText
                              primary={suggestion.label}
                              secondary={suggestion.isRecommended ? t('invoke.recommended') : undefined}
                            />
                            {suggestion.actions && suggestion.actions.length > 0 && (
                              <Stack direction="row" spacing={0.5}>
                                {suggestion.actions.map((action, idx) => (
                                  <Chip
                                    key={`${action.type}-${idx}`}
                                    label={`${action.type}${action.description ? ': ' + action.description : ''}`}
                                    size="small"
                                    color={action.type === 'delete' ? 'error' : 'primary'}
                                    variant="outlined"
                                  />
                                ))}
                              </Stack>
                            )}
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}

                  {card.links && card.links.length > 0 && (
                    <Box mt={2}>
                      <Typography variant="subtitle2">{t('invoke.links')}</Typography>
                      <Stack direction="row" spacing={1} mt={1}>
                        {card.links.map((link, i) => (
                          <Button
                            key={`${link.label}-${i}`}
                            variant="outlined"
                            size="small"
                            href={isSafeHttpUrl(link.url) ? link.url : '#'}
                            target="_blank"
                            rel="noopener noreferrer"
                            sx={(theme) => ({
                              borderColor: 'primary.main',
                              color: 'primary.main',
                              '&:hover': {
                                borderColor: 'primary.dark',
                                bgcolor: alpha(theme.palette.primary.main, 0.04),
                              },
                            })}
                          >
                            {link.label}
                          </Button>
                        ))}
                      </Stack>
                    </Box>
                  )}
                </CardContent>
                <CardActions sx={{ px: 2, pb: 1.5 }}>
                  <Button size="small" sx={{ color: 'success.main' }} onClick={() => handleAccept(card.uuid)}>
                    {t('invoke.accept')}
                  </Button>
                  <Button size="small" sx={{ color: 'text.secondary' }} onClick={() => handleOverrideClick(card.uuid)}>
                    {t('invoke.override')}
                  </Button>
                </CardActions>
              </Card>
            ))}
          </Stack>
        </Box>
      )}

      {cdsResponse?.systemActions && cdsResponse.systemActions.length > 0 && (
        <Alert severity="info" sx={{ mt: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('invoke.systemActions', { count: cdsResponse.systemActions.length })}
          </Typography>
          <List dense>
            {cdsResponse.systemActions.map((action, i) => (
              <ListItem key={`${action.type}-${action.resourceId || i}`}>
                <ListItemText
                  primary={`${action.type}: ${action.description || t('invoke.noDescription')}`}
                  secondary={action.resourceId ? t('invoke.resource', { resourceId: action.resourceId }) : undefined}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {allCards.length === 0 && !invokeMutation.isPending && selectedService && (
        <Typography variant="body2" color="text.secondary" textAlign="center">
          {t('invoke.noCards')}
        </Typography>
      )}

      <Dialog open={overrideDialogOpen} onClose={() => setOverrideDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('invoke.overrideDialogTitle')}</DialogTitle>
        <DialogContent>
          <TextField
            label={t('invoke.overrideReasonLabel')}
            value={overrideReason}
            onChange={(e) => setOverrideReason(e.target.value)}
            fullWidth
            multiline
            rows={3}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOverrideDialogOpen(false)}>{tc('actions.cancel')}</Button>
          <Button onClick={handleOverrideSubmit} variant="contained" color="warning">
            {t('invoke.submitOverride')}
          </Button>
        </DialogActions>
      </Dialog>

    </Stack>
  )
}
