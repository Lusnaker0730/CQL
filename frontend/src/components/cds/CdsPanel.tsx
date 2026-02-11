import { useState, useEffect, useRef, useCallback } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { setCqlContent } from '../../store/editorSlice'
import type { RootState } from '../../store'

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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
import {
  Info as InfoIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  CheckCircle as CheckIcon,
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  History as HistoryIcon,
  Analytics as AnalyticsIcon,
  VpnKey as KeyIcon,
  Save as SaveIcon,
  Code as CodeIcon,
  Share as ShareIcon,
  LibraryBooks as LibraryBooksIcon,
  ViewModule as BuilderIcon,
  Code as JsonIcon,
} from '@mui/icons-material'
import {
  useCdsServices,
  useInvokeCdsService,
  useCdsServiceConfigs,
  useCreateCdsService,
  useUpdateCdsService,
  useDeleteCdsService,
  useSubmitCdsFeedback,
  useCdsServiceVersions,
  useRollbackCdsService,
  useToggleCdsServiceShared,
  useCdsAnalytics,
  useSandboxInvoke,
} from '../../hooks/useCdsHooks'
import type {
  CdsCard,
  CdsResponse,
  CdsServiceDefinition,
  CdsServiceConfigRequest,
  CdsServiceConfigResponse,
  CdsServiceAnalytics,
} from '../../types'
import { useNotification } from '../../hooks/useNotification'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import { validateRequired } from '../../utils/validation'
import FhirServerUrlField from '../common/FhirServerUrlField'
import ApiKeyManager from './ApiKeyManager'
import LibraryPicker from '../common/LibraryPicker'
import GradientButton from '../common/GradientButton'
import TabPanel, { a11yProps } from '../common/TabPanel'
import TableSkeleton from '../common/TableSkeleton'
import CardListSkeleton from '../common/CardListSkeleton'
import {
  BundleBuilderProvider,
  useBundleBuilder,
  serializeToBundle,
  parseFromBundle,
} from '../../contexts/BundleBuilderContext'
import VisualBundleBuilder from '../testcase-builder/VisualBundleBuilder'
import { bundleToPrefetch, prefetchToBundle } from '../../utils/bundlePrefetchConverter'
import Editor from '@monaco-editor/react'

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

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)} variant="scrollable" scrollButtons="auto">
        <Tab label="Invoke Service" {...a11yProps(0, 'cds')} />
        <Tab label="Manage Services" {...a11yProps(1, 'cds')} />
        <Tab label="Analytics" icon={<AnalyticsIcon />} iconPosition="start" {...a11yProps(2, 'cds')} />
        <Tab label="Sandbox" {...a11yProps(3, 'cds')} />
        <Tab label="API Keys" icon={<KeyIcon />} iconPosition="start" {...a11yProps(4, 'cds')} />
      </Tabs>

      <TabPanel value={tabValue} index={0} prefix="cds" sx={{ pt: 2 }}>
        <InvokeServicePanel />
      </TabPanel>
      <TabPanel value={tabValue} index={1} prefix="cds" sx={{ pt: 2 }}>
        <ManageServicesPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={2} prefix="cds" sx={{ pt: 2 }}>
        <AnalyticsPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={3} prefix="cds" sx={{ pt: 2 }}>
        <SandboxPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={4} prefix="cds" sx={{ pt: 2 }}>
        <ApiKeyManager />
      </TabPanel>
    </Paper>
  )
}

function InvokeServicePanel() {
  const { data: servicesData, isLoading: loadingServices, isError: servicesError } = useCdsServices()
  const { data: serviceConfigs } = useCdsServiceConfigs()
  const dispatch = useDispatch()
  const invokeMutation = useInvokeCdsService()
  const feedbackMutation = useSubmitCdsFeedback()

  const { showNotification } = useNotification()

  const [selectedService, setSelectedService] = useState<string>('')
  const [patientId, setPatientId] = useState('')
  const [fhirServer, setFhirServer] = useState('http://localhost:8090/fhir')
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [cdsResponse, setCdsResponse] = useState<CdsResponse | null>(null)
  const [overrideDialogOpen, setOverrideDialogOpen] = useState(false)
  const [overrideCardUuid, setOverrideCardUuid] = useState('')
  const [overrideReason, setOverrideReason] = useState('')

  const services = Array.isArray(servicesData?.services) ? servicesData.services : []
  const cards = cdsResponse?.cards ?? []

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
      console.error('CDS invocation failed:', error)
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
                                    key={idx}
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

      {/* System Actions */}
      {cdsResponse?.systemActions && cdsResponse.systemActions.length > 0 && (
        <Alert severity="info" sx={{ mt: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            System Actions ({cdsResponse.systemActions.length})
          </Typography>
          <List dense>
            {cdsResponse.systemActions.map((action, i) => (
              <ListItem key={i}>
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

      {/* Override Dialog */}
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

function ManageServicesPanel() {
  const dispatch = useDispatch()
  const cqlContent = useSelector((state: RootState) => state.editor.cqlContent)
  const { data: services, isLoading, isError } = useCdsServiceConfigs()
  const createMutation = useCreateCdsService()
  const updateMutation = useUpdateCdsService()
  const deleteMutation = useDeleteCdsService()
  const rollbackMutation = useRollbackCdsService()
  const shareMutation = useToggleCdsServiceShared()
  const { showNotification } = useNotification()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingService, setEditingService] = useState<CdsServiceConfigResponse | null>(null)
  const [activeServiceId, setActiveServiceId] = useState<string | null>(null)
  const [formData, setFormData] = useState<CdsServiceConfigRequest>({
    id: '',
    hook: 'patient-view',
    title: '',
    description: '',
    cqlContent: '',
    defaultIndicator: 'info',
    enabled: true,
  })

  const [formErrors, setFormErrors] = useState<{ id?: string; title?: string }>({})
  const [libraryPickerOpen, setLibraryPickerOpen] = useState(false)

  // Versioning state
  const [versionsDialogOpen, setVersionsDialogOpen] = useState(false)
  const [versionsServiceName, setVersionsServiceName] = useState<string | null>(null)
  const { data: versions } = useCdsServiceVersions(versionsServiceName)

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
    const errors: { id?: string; title?: string } = {}
    const idErr = validateRequired(formData.id, 'Service ID')
    const titleErr = validateRequired(formData.title, 'Title')
    if (idErr) errors.id = idErr
    if (titleErr) errors.title = titleErr
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      return
    }
    setFormErrors({})

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

  const handleShowVersions = (serviceName: string) => {
    setVersionsServiceName(serviceName)
    setVersionsDialogOpen(true)
  }

  const handleRollback = async (serviceName: string, version: number) => {
    try {
      await rollbackMutation.mutateAsync({ serviceName, version })
      setVersionsDialogOpen(false)
    } catch (error) {
      console.error('Failed to rollback:', error)
    }
  }

  const handleSelectService = (service: CdsServiceConfigResponse) => {
    if (activeServiceId === service.id) {
      setActiveServiceId(null)
      return
    }
    setActiveServiceId(service.id)
    if (service.cqlContent) {
      dispatch(setCqlContent(service.cqlContent))
    }
  }

  const handleSaveCqlToService = async () => {
    if (!activeServiceId) return
    const service = services?.find((s) => s.id === activeServiceId)
    if (!service) return

    try {
      await updateMutation.mutateAsync({
        id: service.id,
        request: {
          id: service.id,
          hook: service.hook,
          title: service.title,
          description: service.description || '',
          cqlContent: cqlContent,
          cqlLibraryId: service.cqlLibraryId,
          defaultIndicator: service.defaultIndicator || 'info',
          enabled: service.enabled,
        },
      })
      showNotification('CQL content saved to service', 'success')
    } catch (error) {
      console.error('Failed to save CQL to service:', error)
    }
  }

  if (isLoading) return <TableSkeleton columns={5} />
  if (isError) return <Alert severity="error">Failed to load services</Alert>

  return (
    <Stack spacing={2}>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1">Configured Services</Typography>
        <GradientButton
          startIcon={<AddIcon />}
          onClick={handleOpenCreate}
        >
          New Service
        </GradientButton>
      </Box>

      {activeServiceId && (
        <Alert
          severity="info"
          icon={<CodeIcon />}
          action={
            <Button
              color="primary"
              size="small"
              startIcon={<SaveIcon />}
              onClick={handleSaveCqlToService}
              disabled={updateMutation.isPending}
            >
              {updateMutation.isPending ? 'Saving...' : 'Save CQL'}
            </Button>
          }
        >
          Editing <strong>{services?.find((s) => s.id === activeServiceId)?.title}</strong> — edit CQL in the left editor, then click Save.
        </Alert>
      )}

      {services?.length === 0 && (
        <Alert severity="info">No CDS services configured. Create one to get started.</Alert>
      )}

      {services?.map((service) => {
        const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
        const isOwner = !service.ownerUsername || service.ownerUsername === currentUser?.username
        const isShared = service.shared
        const ownershipLabel = isShared ? 'Shared' : isOwner ? 'Mine' : `Owner: ${service.ownerUsername}`

        return (
          <Card
            key={service.id}
            variant="outlined"
            onClick={() => handleSelectService(service)}
            sx={{
              transition: 'all 0.25s ease',
              cursor: 'pointer',
              ...(activeServiceId === service.id && {
                borderColor: 'primary.main',
                borderWidth: 2,
                bgcolor: 'rgba(13,115,119,0.04)',
              }),
              '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: '0 6px 20px rgba(13,115,119,0.12)',
              },
            }}
          >
            <CardContent sx={{ pb: 1 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="subtitle1" fontWeight="bold" sx={{ color: 'text.primary' }}>
                      {service.title}
                    </Typography>
                    {service.version && <Chip label={`v${service.version}`} size="small" variant="outlined" />}
                    <Chip
                      label={ownershipLabel}
                      size="small"
                      color={isShared ? 'primary' : isOwner ? 'default' : 'secondary'}
                      variant="outlined"
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    ID: {service.id} | Hook: {service.hook}
                    {service.serviceName && service.serviceName !== service.id && ` | Name: ${service.serviceName}`}
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
                  {service.serviceName && (
                    <IconButton
                      size="small"
                      onClick={(e) => { e.stopPropagation(); handleShowVersions(service.serviceName!) }}
                      title="View versions"
                      aria-label="View versions"
                    >
                      <HistoryIcon fontSize="small" />
                    </IconButton>
                  )}
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation()
                      shareMutation.mutate({ id: service.id, shared: !service.shared })
                    }}
                    sx={{ color: service.shared ? 'primary.main' : 'text.secondary' }}
                    title={service.shared ? 'Unshare service' : 'Share service'}
                    aria-label={service.shared ? 'Unshare service' : 'Share service'}
                  >
                    <ShareIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={(e) => { e.stopPropagation(); handleOpenEdit(service) }}
                    sx={{ color: 'primary.main' }}
                    disabled={!isOwner && !isShared}
                    aria-label="Edit service"
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={(e) => { e.stopPropagation(); handleDelete(service.id) }}
                    disabled={!isOwner}
                    aria-label="Delete service"
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        )
      })}

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={handleClose} maxWidth="md" fullWidth>
        <DialogTitle>{editingService ? 'Edit CDS Service' : 'Create CDS Service'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Service ID"
              value={formData.id}
              onChange={(e) => {
                setFormData({ ...formData, id: e.target.value })
                setFormErrors((prev) => ({ ...prev, id: undefined }))
              }}
              size="small"
              fullWidth
              disabled={!!editingService}
              helperText={formErrors.id || 'Unique identifier for the service (cannot be changed after creation)'}
              error={!!formErrors.id}
            />

            <TextField
              label="Title"
              value={formData.title}
              onChange={(e) => {
                setFormData({ ...formData, title: e.target.value })
                setFormErrors((prev) => ({ ...prev, title: undefined }))
              }}
              size="small"
              fullWidth
              required
              error={!!formErrors.title}
              helperText={formErrors.title}
            />

            <Stack direction="row" spacing={1} alignItems="center">
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
              <HelpTooltip text={helpContent.cds.hookType} />
            </Stack>

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

            <Stack direction="row" justifyContent="flex-end">
              <Button
                size="small"
                startIcon={<LibraryBooksIcon />}
                onClick={() => setLibraryPickerOpen(true)}
                sx={{ color: 'primary.main' }}
              >
                Load from Library
              </Button>
            </Stack>
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
            <LibraryPicker
              open={libraryPickerOpen}
              onClose={() => setLibraryPickerOpen(false)}
              onSelect={(cql) => setFormData({ ...formData, cqlContent: cql })}
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
          <Button onClick={handleClose} sx={{ color: 'text.secondary' }}>
            Cancel
          </Button>
          <GradientButton
            onClick={handleSave}
            disabled={!formData.id || !formData.title || createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? 'Saving...' : 'Save'}
          </GradientButton>
        </DialogActions>
      </Dialog>

      {/* Versions Dialog */}
      <Dialog open={versionsDialogOpen} onClose={() => setVersionsDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Service Versions: {versionsServiceName}</DialogTitle>
        <DialogContent>
          {versions && versions.length > 0 ? (
            <List>
              {versions.map((v) => (
                <ListItem
                  key={v.id}
                  secondaryAction={
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleRollback(versionsServiceName!, v.version!)}
                      disabled={rollbackMutation.isPending}
                    >
                      {v.enabled ? 'Active' : 'Rollback'}
                    </Button>
                  }
                >
                  <ListItemText
                    primary={`v${v.version} - ${v.title}`}
                    secondary={`Hook: ${v.hook} | ${v.enabled ? 'Enabled' : 'Disabled'} | ${v.createdAt || ''}`}
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography variant="body2" color="text.secondary">
              No versions found.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionsDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function AnalyticsPanel() {
  const { data: analytics, isLoading, isError } = useCdsAnalytics()

  if (isLoading) return <CardListSkeleton />
  if (isError) return <Alert severity="error">Failed to load analytics</Alert>

  if (!analytics || analytics.length === 0) {
    return (
      <Alert severity="info">No analytics data available yet. Invoke CDS services to generate analytics.</Alert>
    )
  }

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell scope="col">Service</TableCell>
            <TableCell scope="col" align="right">Invocations</TableCell>
            <TableCell scope="col" align="right">Errors</TableCell>
            <TableCell scope="col" align="right">Error Rate</TableCell>
            <TableCell scope="col" align="right">Avg Time (ms)</TableCell>
            <TableCell scope="col" align="right">Accepted</TableCell>
            <TableCell scope="col" align="right">Overridden</TableCell>
            <TableCell scope="col">Last Invoked</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {analytics.map((row: CdsServiceAnalytics) => (
            <TableRow key={row.serviceId}>
              <TableCell>{row.serviceId}</TableCell>
              <TableCell align="right">{row.invocationCount}</TableCell>
              <TableCell align="right">{row.errorCount}</TableCell>
              <TableCell
                align="right"
                sx={{ color: row.errorRate > 10 ? 'error.main' : row.errorRate > 5 ? 'warning.main' : 'inherit' }}
              >
                {row.errorRate}%
              </TableCell>
              <TableCell align="right">{row.avgResponseTimeMs}</TableCell>
              <TableCell align="right">{row.feedbackAcceptedCount}</TableCell>
              <TableCell align="right">{row.feedbackOverriddenCount}</TableCell>
              <TableCell>{row.lastInvokedAt ? new Date(row.lastInvokedAt).toLocaleString() : '-'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

const DEFAULT_PREFETCH = {
  patient: {
    resourceType: 'Patient',
    id: 'test-patient-1',
    name: [{ given: ['Test'], family: 'Patient' }],
    gender: 'male',
    birthDate: '1980-01-01',
  },
  observations: {
    resourceType: 'Bundle',
    type: 'searchset',
    entry: [
      {
        resource: {
          resourceType: 'Observation',
          id: 'obs-1',
          status: 'final',
          category: [
            {
              coding: [
                {
                  system: 'http://terminology.hl7.org/CodeSystem/observation-category',
                  code: 'vital-signs',
                },
              ],
            },
          ],
          code: {
            coding: [{ system: 'http://loinc.org', code: '29463-7', display: 'Body Weight' }],
          },
          subject: { reference: 'Patient/test-patient-1' },
          valueQuantity: { value: 85, unit: 'kg' },
        },
      },
    ],
  },
}

function SandboxPanel() {
  return (
    <BundleBuilderProvider>
      <SandboxPanelInner />
    </BundleBuilderProvider>
  )
}

function SandboxPanelInner() {
  const { data: servicesData } = useCdsServices()
  const sandboxMutation = useSandboxInvoke()
  const { state, dispatch } = useBundleBuilder()

  const [selectedService, setSelectedService] = useState('')
  const [patientId, setPatientId] = useState('test-patient-1')
  const [testDataJson, setTestDataJson] = useState(JSON.stringify(DEFAULT_PREFETCH, null, 2))
  const [sandboxResponse, setSandboxResponse] = useState<CdsResponse | null>(null)
  const [dataTab, setDataTab] = useState(0) // 0 = Visual Builder, 1 = JSON (Prefetch)

  const syncingRef = useRef(false)
  const initializedRef = useRef(false)

  const services = Array.isArray(servicesData?.services) ? servicesData.services : []

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
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Sync: Visual Builder → JSON (prefetch)
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

  // Debounced sync: JSON (prefetch) → Visual Builder
  const jsonSyncTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
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
          // Invalid JSON — don't sync
        }
      }, 500)
    },
    [dispatch]
  )

  const handleSandboxInvoke = async () => {
    if (!selectedService) return
    const service = services.find((s) => s.id === selectedService)
    if (!service) return

    try {
      const testData = JSON.parse(testDataJson)
      const response = await sandboxMutation.mutateAsync({
        serviceId: selectedService,
        request: {
          serviceId: selectedService,
          hook: service.hook,
          hookInstance: crypto.randomUUID(),
          context: { patientId },
          testData,
        },
      })
      setSandboxResponse(response)
    } catch (error) {
      console.error('Sandbox invocation failed:', error)
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
      <Alert severity="info">
        Test CDS services without a real FHIR server. Build test data visually or edit JSON directly.
      </Alert>

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

      <TextField
        label="Patient ID"
        value={patientId}
        onChange={(e) => setPatientId(e.target.value)}
        size="small"
        fullWidth
      />

      <Box>
        <Tabs value={dataTab} onChange={(_, v) => setDataTab(v)} sx={{ mb: 1 }}>
          <Tab icon={<BuilderIcon />} iconPosition="start" label="Visual Builder" sx={{ textTransform: 'none', minHeight: 42 }} />
          <Tab icon={<JsonIcon />} iconPosition="start" label="JSON (Prefetch)" sx={{ textTransform: 'none', minHeight: 42 }} />
        </Tabs>

        {dataTab === 0 && (
          <VisualBundleBuilder onDirty={() => {}} />
        )}

        {dataTab === 1 && (
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
            <Editor
              height="350px"
              language="json"
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
        {sandboxMutation.isPending ? 'Invoking...' : 'Invoke in Sandbox'}
      </GradientButton>

      {sandboxMutation.isError && (
        <Alert severity="error">Sandbox invocation failed: {(sandboxMutation.error as Error).message}</Alert>
      )}

      {sandboxResponse && sandboxResponse.cards && sandboxResponse.cards.length > 0 && (
        <Box>
          <Typography variant="subtitle1" gutterBottom>
            Sandbox Results ({sandboxResponse.cards.length} cards)
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
                      color={getIndicatorColor(card.indicator) as 'info' | 'warning' | 'error'}
                    />
                    <Typography variant="subtitle1" fontWeight="bold">
                      {card.summary}
                    </Typography>
                  </Stack>
                  {card.detail && (
                    <Typography variant="body2" color="text.secondary">
                      {card.detail}
                    </Typography>
                  )}
                  {card.suggestions && card.suggestions.length > 0 && (
                    <Box mt={1}>
                      <Typography variant="caption" fontWeight="bold">
                        Suggestions:
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
          <Typography variant="subtitle2">System Actions</Typography>
          {sandboxResponse.systemActions.map((action, i) => (
            <Typography key={i} variant="body2">
              {action.type}: {action.description || 'No description'}
            </Typography>
          ))}
        </Alert>
      )}
    </Stack>
  )
}
