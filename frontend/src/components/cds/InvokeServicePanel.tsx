import { useState, useEffect, useMemo } from 'react'
import { useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import { setCqlContent } from '../../store/editorSlice'

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
import {
  Info as InfoIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  CheckCircle as CheckIcon,
} from '@mui/icons-material'
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
} from '../../constants/cdsHooks'

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
  const invokeMutation = useInvokeCdsService()
  const feedbackMutation = useSubmitCdsFeedback()
  const { t } = useTranslation('cds')
  const { t: tc } = useTranslation('common')

  const { showNotification } = useNotification()

  const [selectedService, setSelectedService] = useState<string>('')
  const [patientId, setPatientId] = useState('')
  const [fhirServer, setFhirServer] = useState(FHIR_SERVER_PRESETS[0].url)
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [cdsResponse, setCdsResponse] = useState<CdsResponse | null>(null)
  const [overrideDialogOpen, setOverrideDialogOpen] = useState(false)
  const [overrideCardUuid, setOverrideCardUuid] = useState('')
  const [overrideReason, setOverrideReason] = useState('')

  // Critical card queue state
  const [criticalQueue, setCriticalQueue] = useState<CdsCard[]>([])
  const [currentCritical, setCurrentCritical] = useState<CdsCard | null>(null)
  const [normalCards, setNormalCards] = useState<CdsCard[]>([])

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )
  const allCards = useMemo(() => cdsResponse?.cards ?? [], [cdsResponse?.cards])

  useEffect(() => {
    if (selectedService && serviceConfigs) {
      const config = serviceConfigs.find((s) => s.id === selectedService)
      if (config && config.cqlContent) {
        dispatch(setCqlContent(config.cqlContent))
      }
    }
  }, [selectedService, serviceConfigs, dispatch])

  const handleInvoke = async () => {
    if (!selectedService) return

    const service = services.find((s) => s.id === selectedService)
    if (!service) return

    try {
      const response = await invokeMutation.mutateAsync({
        serviceId: selectedService,
        request: {
          hook: service.hook,
          hookInstance: generateId(),
          fhirServer,
          context: {
            ...(patientId ? { patientId } : {}),
          },
        },
      })
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
      showNotification(t('invoke.invokeFailed', { error: extractApiError(error) }), 'error')
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
        <InputLabel>{t('invoke.serviceLabel')}</InputLabel>
        <Select value={selectedService} onChange={(e) => setSelectedService(e.target.value)} label={t('invoke.serviceLabel')}>
          {services.map((service: CdsServiceDefinition) => (
            <MenuItem key={service.id} value={service.id}>
              {service.title} ({service.hook})
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {selectedService && (
        <Box>
          {services
            .filter((s) => s.id === selectedService)
            .map((service) => (
              <Typography key={service.id} variant="body2" color="text.secondary">
                {service.description}
              </Typography>
            ))}
        </Box>
      )}

      <FhirServerUrlField
        value={fhirServer}
        onChange={(value) => {
          setFhirServer(value)
          setFhirServerError(null)
        }}
        error={!!fhirServerError}
        helperText={fhirServerError}
      />

      <TextField
        label={t('invoke.patientIdLabel')}
        value={patientId}
        onChange={(e) => setPatientId(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('invoke.patientIdPlaceholder')}
        helperText={t('invoke.patientIdHelperText')}
      />

      <GradientButton
        onClick={handleInvoke}
        disabled={!selectedService || invokeMutation.isPending}
        startIcon={invokeMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
      >
        {invokeMutation.isPending ? t('invoke.invoking') : t('invoke.invokeButton')}
      </GradientButton>

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
                            href={link.url && (link.url.startsWith('https://') || link.url.startsWith('http://')) ? link.url : '#'}
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
