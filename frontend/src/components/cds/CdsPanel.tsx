import { useState } from 'react'
import {
  Box,
  Paper,
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
} from '@mui/material'
import {
  Info as InfoIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  CheckCircle as CheckIcon,
} from '@mui/icons-material'
import { useCdsServices, useInvokeCdsService } from '../../hooks/useCdsHooks'
import type { CdsCard, CdsServiceDefinition } from '../../types'

export default function CdsPanel() {
  const { data: servicesData, isLoading: loadingServices } = useCdsServices()
  const invokeMutation = useInvokeCdsService()

  const [selectedService, setSelectedService] = useState<string>('')
  const [patientId, setPatientId] = useState('')
  const [fhirServer, setFhirServer] = useState('http://hapi.fhir.org/baseR4')
  const [cards, setCards] = useState<CdsCard[]>([])

  const services = servicesData?.services || []

  const handleInvoke = async () => {
    if (!selectedService || !patientId) return

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
            patientId,
          },
        },
      })
      setCards(response.cards)
    } catch (error) {
      console.error('CDS invocation failed:', error)
    }
  }

  const getIndicatorIcon = (indicator: string) => {
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

  const getIndicatorColor = (indicator: string) => {
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

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        CDS Hooks
      </Typography>

      <Stack spacing={2}>
        <FormControl fullWidth size="small">
          <InputLabel>CDS Service</InputLabel>
          <Select
            value={selectedService}
            onChange={(e) => setSelectedService(e.target.value)}
            label="CDS Service"
          >
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

        <TextField
          label="FHIR Server URL"
          value={fhirServer}
          onChange={(e) => setFhirServer(e.target.value)}
          size="small"
          fullWidth
        />

        <TextField
          label="Patient ID"
          value={patientId}
          onChange={(e) => setPatientId(e.target.value)}
          size="small"
          fullWidth
          placeholder="e.g., example-patient-1"
        />

        <Button
          variant="contained"
          onClick={handleInvoke}
          disabled={!selectedService || !patientId || invokeMutation.isPending}
          startIcon={invokeMutation.isPending ? <CircularProgress size={20} /> : null}
        >
          {invokeMutation.isPending ? 'Invoking...' : 'Invoke Service'}
        </Button>

        <Divider />

        {loadingServices && <CircularProgress />}

        {invokeMutation.isError && (
          <Alert severity="error">
            Failed to invoke CDS service: {(invokeMutation.error as Error).message}
          </Alert>
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
                  }}
                >
                  <CardContent>
                    <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                      {getIndicatorIcon(card.indicator)}
                      <Typography variant="subtitle1" fontWeight="bold">
                        {card.summary}
                      </Typography>
                      <Chip
                        label={card.indicator}
                        size="small"
                        color={getIndicatorColor(card.indicator) as 'info' | 'warning' | 'error'}
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
                                {suggestion.isRecommended ? (
                                  <CheckIcon color="success" />
                                ) : (
                                  <InfoIcon color="action" />
                                )}
                              </ListItemIcon>
                              <ListItemText
                                primary={suggestion.label}
                                secondary={
                                  suggestion.isRecommended ? 'Recommended' : undefined
                                }
                              />
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
                              key={i}
                              variant="outlined"
                              size="small"
                              href={link.url}
                              target="_blank"
                            >
                              {link.label}
                            </Button>
                          ))}
                        </Stack>
                      </Box>
                    )}
                  </CardContent>
                  <CardActions>
                    <Button size="small">Accept</Button>
                    <Button size="small">Override</Button>
                  </CardActions>
                </Card>
              ))}
            </Stack>
          </Box>
        )}

        {cards.length === 0 && !invokeMutation.isPending && selectedService && (
          <Typography variant="body2" color="text.secondary" textAlign="center">
            No cards returned. Invoke a CDS service to see results.
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
