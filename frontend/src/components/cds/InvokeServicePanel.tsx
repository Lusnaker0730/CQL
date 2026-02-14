import { useState, useEffect, useMemo } from 'react'
import { useDispatch } from 'react-redux'
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
import {
  useCdsServices,
  useInvokeCdsService,
  useCdsServiceConfigs,
  useSubmitCdsFeedback,
} from '../../hooks/useCdsHooks'
import type { CdsServiceDefinition, CdsResponse } from '../../types'
import { useNotification } from '../../hooks/useNotification'
import FhirServerUrlField from '../common/FhirServerUrlField'
import GradientButton from '../common/GradientButton'

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

export default function InvokeServicePanel() {
  const { data: servicesData, isLoading: loadingServices, isError: servicesError } = useCdsServices()
  const { data: serviceConfigs } = useCdsServiceConfigs()
  const dispatch = useDispatch()
  const invokeMutation = useInvokeCdsService()
  const feedbackMutation = useSubmitCdsFeedback()

  const { showNotification } = useNotification()

  const [selectedService, setSelectedService] = useState<string>('')
  const [patientId, setPatientId] = useState('')
  const [fhirServer, setFhirServer] = useState('http://hapi-fhir:8080/fhir')
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [cdsResponse, setCdsResponse] = useState<CdsResponse | null>(null)
  const [overrideDialogOpen, setOverrideDialogOpen] = useState(false)
  const [overrideCardUuid, setOverrideCardUuid] = useState('')
  const [overrideReason, setOverrideReason] = useState('')

  const services = useMemo(
    () => (Array.isArray(servicesData?.services) ? servicesData.services : []),
    [servicesData?.services]
  )
  const cards = useMemo(() => cdsResponse?.cards ?? [], [cdsResponse?.cards])

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
          hookInstance: crypto.randomUUID(),
          fhirServer,
          context: {
            ...(patientId ? { patientId } : {}),
          },
        },
      })
      setCdsResponse(response)
    } catch (error) {
      showNotification('CDS invocation failed: ' + (error as Error).message, 'error')
    }
  }

  const handleAccept = async (cardUuid: string) => {
    try {
      await feedbackMutation.mutateAsync({
        serviceId: selectedService,
        feedback: {
          feedback: [{ card: cardUuid, outcome: 'accepted' }],
        },
      })
      showNotification('Feedback submitted: accepted', 'success')
    } catch {
      showNotification('Failed to submit feedback', 'error')
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
              outcome: 'overridden',
              overrideReason: { code: 'override', display: overrideReason || 'No reason provided' },
            },
          ],
        },
      })
      setOverrideDialogOpen(false)
      showNotification('Feedback submitted: overridden', 'success')
    } catch {
      showNotification('Failed to submit feedback', 'error')
    }
  }

  return (
    <Stack spacing={2}>
      <FormControl fullWidth size="small">
        <InputLabel>CDS Service</InputLabel>
        <Select value={selectedService} onChange={(e) => setSelectedService(e.target.value)} label="CDS Service">
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
        label="Patient ID (Optional)"
        value={patientId}
        onChange={(e) => setPatientId(e.target.value)}
        size="small"
        fullWidth
        placeholder="e.g., example-patient-1"
        helperText="Leave empty if service doesn't require patient context"
      />

      <GradientButton
        onClick={handleInvoke}
        disabled={!selectedService || invokeMutation.isPending}
        startIcon={invokeMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
        sx={{
          '&.Mui-disabled': {
            background: 'rgba(0,0,0,0.12)',
          },
        }}
      >
        {invokeMutation.isPending ? 'Invoking...' : 'Invoke Service'}
      </GradientButton>

      <Divider />

      {loadingServices && <CircularProgress />}

      {servicesError && (
        <Alert severity="error">Failed to load CDS services. Please check the backend connection.</Alert>
      )}

      {invokeMutation.isError && (
        <Alert severity="error">Failed to invoke CDS service: {(invokeMutation.error as Error).message}</Alert>
      )}

      {cards.length > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            Response Cards ({cards.length})
          </Typography>
          <Stack spacing={2}>
            {cards.map((card) => (
              <Card
                key={card.uuid}
                variant="outlined"
                sx={{
                  borderLeft: 4,
                  borderLeftColor: `${getIndicatorColor(card.indicator)}.main`,
                  transition: 'all 0.25s ease',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: '0 6px 20px rgba(13,115,119,0.12)',
                  },
                }}
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
                      Source: {card.source.label}
                    </Typography>
                  )}

                  {card.suggestions && card.suggestions.length > 0 && (
                    <Box mt={2}>
                      <Typography variant="subtitle2">Suggestions</Typography>
                      <List dense>
                        {card.suggestions.map((suggestion) => (
                          <ListItem key={suggestion.uuid}>
                            <ListItemIcon>
                              {suggestion.isRecommended ? <CheckIcon color="success" /> : <InfoIcon color="action" />}
                            </ListItemIcon>
                            <ListItemText
                              primary={suggestion.label}
                              secondary={suggestion.isRecommended ? 'Recommended' : undefined}
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
                      <Typography variant="subtitle2">Links</Typography>
                      <Stack direction="row" spacing={1} mt={1}>
                        {card.links.map((link, i) => (
                          <Button
                            key={`${link.label}-${i}`}
                            variant="outlined"
                            size="small"
                            href={link.url && (link.url.startsWith('https://') || link.url.startsWith('http://')) ? link.url : '#'}
                            target="_blank"
                            rel="noopener noreferrer"
                            sx={{
                              borderColor: 'primary.main',
                              color: 'primary.main',
                              '&:hover': {
                                borderColor: 'primary.dark',
                                bgcolor: 'rgba(13,115,119,0.04)',
                              },
                            }}
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
                    Accept
                  </Button>
                  <Button size="small" sx={{ color: 'text.secondary' }} onClick={() => handleOverrideClick(card.uuid)}>
                    Override
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
            System Actions ({cdsResponse.systemActions.length})
          </Typography>
          <List dense>
            {cdsResponse.systemActions.map((action, i) => (
              <ListItem key={`${action.type}-${action.resourceId || i}`}>
                <ListItemText
                  primary={`${action.type}: ${action.description || 'No description'}`}
                  secondary={action.resourceId ? `Resource: ${action.resourceId}` : undefined}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}

      {cards.length === 0 && !invokeMutation.isPending && selectedService && (
        <Typography variant="body2" color="text.secondary" textAlign="center">
          No cards returned. Invoke a CDS service to see results.
        </Typography>
      )}

      <Dialog open={overrideDialogOpen} onClose={() => setOverrideDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Override Reason</DialogTitle>
        <DialogContent>
          <TextField
            label="Reason for override"
            value={overrideReason}
            onChange={(e) => setOverrideReason(e.target.value)}
            fullWidth
            multiline
            rows={3}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOverrideDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleOverrideSubmit} variant="contained" color="warning">
            Submit Override
          </Button>
        </DialogActions>
      </Dialog>

    </Stack>
  )
}
