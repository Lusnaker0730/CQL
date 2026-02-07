import { useState, useEffect } from 'react'
import { useDispatch } from 'react-redux'
import { setCqlContent } from '../../store/editorSlice'

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
  Tabs,
  Tab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Switch,
  FormControlLabel,
} from '@mui/material'
import {
  Info as InfoIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  CheckCircle as CheckIcon,
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import {
  useCdsServices,
  useInvokeCdsService,
  useCdsServiceConfigs,
  useCreateCdsService,
  useUpdateCdsService,
  useDeleteCdsService,
} from '../../hooks/useCdsHooks'
import type { CdsCard, CdsServiceDefinition, CdsServiceConfigRequest, CdsServiceConfigResponse } from '../../types'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
    </div>
  )
}

const HOOK_TYPES = [
  'patient-view',
  'order-select',
  'order-sign',
  'appointment-book',
  'encounter-start',
  'encounter-discharge',
]

const INDICATOR_TYPES = ['info', 'warning', 'critical']

export default function CdsPanel() {
  const [tabValue, setTabValue] = useState(0)

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        CDS Hooks
      </Typography>

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
        <Tab label="Invoke Service" />
        <Tab label="Manage Services" />
      </Tabs>

      <TabPanel value={tabValue} index={0}>
        <InvokeServicePanel />
      </TabPanel>
      <TabPanel value={tabValue} index={1}>
        <ManageServicesPanel />
      </TabPanel>
    </Paper>
  )
}

function InvokeServicePanel() {
  const { data: servicesData, isLoading: loadingServices, isError: servicesError } = useCdsServices()
  const { data: serviceConfigs } = useCdsServiceConfigs()
  const dispatch = useDispatch()
  const invokeMutation = useInvokeCdsService()

  const [selectedService, setSelectedService] = useState<string>('')
  const [patientId, setPatientId] = useState('')
  const [fhirServer, setFhirServer] = useState('http://hapi.fhir.org/baseR4')
  const [cards, setCards] = useState<CdsCard[]>([])

  const services = Array.isArray(servicesData?.services) ? servicesData.services : []

  useEffect(() => {
    if (selectedService && serviceConfigs) {
      const config = serviceConfigs.find(s => s.id === selectedService)
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
        label="Patient ID (Optional)"
        value={patientId}
        onChange={(e) => setPatientId(e.target.value)}
        size="small"
        fullWidth
        placeholder="e.g., example-patient-1"
        helperText="Leave empty if service doesn't require patient context"
      />

      <Button
        variant="contained"
        onClick={handleInvoke}
        disabled={!selectedService || invokeMutation.isPending}
        startIcon={invokeMutation.isPending ? <CircularProgress size={20} color="inherit" /> : null}
        sx={{
          background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
          '&:hover': {
            background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)',
          },
          '&.Mui-disabled': {
            background: 'rgba(0,0,0,0.12)',
          },
        }}
      >
        {invokeMutation.isPending ? 'Invoking...' : 'Invoke Service'}
      </Button>

      <Divider />

      {loadingServices && <CircularProgress />}

      {servicesError && (
        <Alert severity="error">
          Failed to load CDS services. Please check the backend connection.
        </Alert>
      )}

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
                  <Button size="small" sx={{ color: 'success.main' }}>Accept</Button>
                  <Button size="small" sx={{ color: 'text.secondary' }}>Override</Button>
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
  )
}

function ManageServicesPanel() {
  const { data: services, isLoading, isError } = useCdsServiceConfigs()
  const createMutation = useCreateCdsService()
  const updateMutation = useUpdateCdsService()
  const deleteMutation = useDeleteCdsService()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingService, setEditingService] = useState<CdsServiceConfigResponse | null>(null)
  const [formData, setFormData] = useState<CdsServiceConfigRequest>({
    id: '',
    hook: 'patient-view',
    title: '',
    description: '',
    cqlContent: '',
    defaultIndicator: 'info',
    enabled: true,
  })

  const handleOpenCreate = () => {
    setEditingService(null)
    setFormData({
      id: '',
      hook: 'patient-view',
      title: '',
      description: '',
      cqlContent: '',
      defaultIndicator: 'info',
      enabled: true,
    })
    setDialogOpen(true)
  }

  const handleOpenEdit = (service: CdsServiceConfigResponse) => {
    setEditingService(service)
    setFormData({
      id: service.id,
      hook: service.hook,
      title: service.title,
      description: service.description || '',
      cqlContent: service.cqlContent || '',
      cqlLibraryId: service.cqlLibraryId,
      defaultIndicator: service.defaultIndicator || 'info',
      enabled: service.enabled,
    })
    setDialogOpen(true)
  }

  const handleClose = () => {
    setDialogOpen(false)
    setEditingService(null)
  }

  const handleSave = async () => {
    try {
      if (editingService) {
        await updateMutation.mutateAsync({ id: editingService.id, request: formData })
      } else {
        await createMutation.mutateAsync(formData)
      }
      handleClose()
    } catch (error) {
      console.error('Failed to save service:', error)
    }
  }

  const handleDelete = async (id: string) => {
    if (window.confirm('Are you sure you want to delete this service?')) {
      try {
        await deleteMutation.mutateAsync(id)
      } catch (error) {
        console.error('Failed to delete service:', error)
      }
    }
  }

  if (isLoading) return <CircularProgress />
  if (isError) return <Alert severity="error">Failed to load services</Alert>

  return (
    <Stack spacing={2}>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1">Configured Services</Typography>
        <Button
          startIcon={<AddIcon />}
          variant="contained"
          size="small"
          onClick={handleOpenCreate}
          sx={{
            background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
            '&:hover': {
              background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)',
            },
          }}
        >
          New Service
        </Button>
      </Box>

      {services?.length === 0 && (
        <Alert severity="info">No CDS services configured. Create one to get started.</Alert>
      )}

      {services?.map((service) => (
        <Card
          key={service.id}
          variant="outlined"
          sx={{
            transition: 'all 0.25s ease',
            '&:hover': {
              transform: 'translateY(-2px)',
              boxShadow: '0 6px 20px rgba(13,115,119,0.12)',
            },
          }}
        >
          <CardContent sx={{ pb: 1 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
              <Box>
                <Typography variant="subtitle1" fontWeight="bold" sx={{ color: 'text.primary' }}>
                  {service.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  ID: {service.id} | Hook: {service.hook}
                </Typography>
                {service.description && (
                  <Typography variant="body2" sx={{ mt: 1, color: 'text.primary' }}>
                    {service.description}
                  </Typography>
                )}
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  label={service.enabled ? 'Enabled' : 'Disabled'}
                  color={service.enabled ? 'success' : 'default'}
                  size="small"
                />
                <IconButton size="small" onClick={() => handleOpenEdit(service)} sx={{ color: 'primary.main' }}>
                  <EditIcon fontSize="small" />
                </IconButton>
                <IconButton size="small" color="error" onClick={() => handleDelete(service.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ))}

      <Dialog open={dialogOpen} onClose={handleClose} maxWidth="md" fullWidth>
        <DialogTitle>{editingService ? 'Edit CDS Service' : 'Create CDS Service'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Service ID"
              value={formData.id}
              onChange={(e) => setFormData({ ...formData, id: e.target.value })}
              size="small"
              fullWidth
              disabled={!!editingService}
              helperText="Unique identifier for the service (cannot be changed after creation)"
            />

            <TextField
              label="Title"
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              size="small"
              fullWidth
              required
            />

            <FormControl fullWidth size="small">
              <InputLabel>Hook Type</InputLabel>
              <Select
                value={formData.hook}
                onChange={(e) => setFormData({ ...formData, hook: e.target.value })}
                label="Hook Type"
              >
                {HOOK_TYPES.map((hook) => (
                  <MenuItem key={hook} value={hook}>
                    {hook}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="Description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              size="small"
              fullWidth
              multiline
              rows={2}
            />

            <FormControl fullWidth size="small">
              <InputLabel>Default Indicator</InputLabel>
              <Select
                value={formData.defaultIndicator}
                onChange={(e) => setFormData({ ...formData, defaultIndicator: e.target.value })}
                label="Default Indicator"
              >
                {INDICATOR_TYPES.map((indicator) => (
                  <MenuItem key={indicator} value={indicator}>
                    {indicator}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="CQL Content"
              value={formData.cqlContent}
              onChange={(e) => setFormData({ ...formData, cqlContent: e.target.value })}
              size="small"
              fullWidth
              multiline
              rows={8}
              placeholder="Enter CQL code for this service..."
              sx={{
                '& .MuiInputBase-root': {
                  fontFamily: '"Consolas", monospace',
                },
              }}
            />

            <FormControlLabel
              control={
                <Switch
                  checked={formData.enabled}
                  onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                  color="primary"
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleClose} sx={{ color: 'text.secondary' }}>Cancel</Button>
          <Button
            onClick={handleSave}
            variant="contained"
            disabled={!formData.id || !formData.title || createMutation.isPending || updateMutation.isPending}
            sx={{
              background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
              '&:hover': {
                background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)',
              },
            }}
          >
            {createMutation.isPending || updateMutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
