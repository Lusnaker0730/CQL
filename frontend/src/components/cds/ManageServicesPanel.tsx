import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
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
  Chip,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  IconButton,
  Switch,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material'
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  History as HistoryIcon,
  Code as CodeIcon,
  Save as SaveIcon,
  Share as ShareIcon,
  LibraryBooks as LibraryBooksIcon,
} from '@mui/icons-material'
import {
  useCdsServiceConfigs,
  useCreateCdsService,
  useUpdateCdsService,
  useDeleteCdsService,
  useRollbackCdsService,
  useToggleCdsServiceShared,
  useCdsServiceVersions,
} from '../../hooks/useCdsHooks'
import type { CdsServiceConfigRequest, CdsServiceConfigResponse } from '../../types'
import { useNotification } from '../../hooks/useNotification'
import { validateRequired, safeParseJson } from '../../utils/validation'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import LibraryPicker from '../common/LibraryPicker'
import GradientButton from '../common/GradientButton'
import TableSkeleton from '../common/TableSkeleton'

const HOOK_TYPES = [
  'patient-view',
  'order-select',
  'order-sign',
  'appointment-book',
  'encounter-start',
  'encounter-discharge',
]

const INDICATOR_TYPES = ['info', 'warning', 'critical']

export default function ManageServicesPanel() {
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
      showNotification('Failed to save service: ' + (error as Error).message, 'error')
    }
  }

  const handleDelete = async (id: string) => {
    if (window.confirm('Are you sure you want to delete this service?')) {
      try {
        await deleteMutation.mutateAsync(id)
      } catch (error) {
        showNotification('Failed to delete service: ' + (error as Error).message, 'error')
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
      showNotification('Failed to rollback: ' + (error as Error).message, 'error')
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
      showNotification('Failed to save CQL to service: ' + (error as Error).message, 'error')
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
        const currentUser = safeParseJson<{ username?: string }>(localStorage.getItem('user'), {})
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
