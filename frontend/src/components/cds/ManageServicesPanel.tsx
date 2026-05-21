import { useEffect, useMemo, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useTranslation, Trans } from 'react-i18next'
import { alpha } from '@mui/material/styles'
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
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the old Proxy mock).
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import DeleteIcon from '@mui/icons-material/Delete'
import HistoryIcon from '@mui/icons-material/History'
import CodeIcon from '@mui/icons-material/Code'
import SaveIcon from '@mui/icons-material/Save'
import ShareIcon from '@mui/icons-material/Share'
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks'
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
import { extractApiError } from '../../utils/errorUtils'
import { validateRequired, getStoredUsername } from '../../utils/validation'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import LibraryPicker from '../common/LibraryPicker'
import GradientButton from '../common/GradientButton'
import TableSkeleton from '../common/TableSkeleton'
import { CDS_HOOK_IDS, CDS_INDICATOR_TYPES, DEFAULT_INDICATOR, getRequiredContextFields } from '../../constants/cdsHooks'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import { CDS_SERVICE } from '../../constants/fieldConstraints'

const DEFAULT_FORM_DATA: CdsServiceConfigRequest = {
  id: '',
  hook: CDS_HOOK_IDS[0],
  title: '',
  description: '',
  cqlContent: '',
  defaultIndicator: DEFAULT_INDICATOR,
  enabled: true,
}

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
  const { t } = useTranslation('cds')
  const { t: tc } = useTranslation('common')

  const currentUsername = useMemo(() => getStoredUsername(), [])

  // Guards post-await setState calls when the user navigates away mid-flight.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // Tracks the CQL we last loaded into the editor so handleSelectService can
  // tell whether the user has untouched-since-load content (safe to overwrite)
  // or in-progress edits (should warn).
  const lastLoadedCqlRef = useRef<string>('')

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingService, setEditingService] = useState<CdsServiceConfigResponse | null>(null)
  const [activeServiceId, setActiveServiceId] = useState<string | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [formData, setFormData] = useState<CdsServiceConfigRequest>(DEFAULT_FORM_DATA)

  const [formErrors, setFormErrors] = useState<{ id?: string; title?: string }>({})
  const [libraryPickerOpen, setLibraryPickerOpen] = useState(false)

  const [versionsDialogOpen, setVersionsDialogOpen] = useState(false)
  const [versionsServiceName, setVersionsServiceName] = useState<string | null>(null)
  const { data: versions } = useCdsServiceVersions(versionsServiceName)

  const handleOpenCreate = () => {
    setEditingService(null)
    setFormData(DEFAULT_FORM_DATA)
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
      defaultIndicator: service.defaultIndicator || DEFAULT_INDICATOR,
      enabled: service.enabled,
    })
    setDialogOpen(true)
  }

  const handleClose = () => {
    setDialogOpen(false)
    setEditingService(null)
    setFormData(DEFAULT_FORM_DATA)
    setFormErrors({})
  }

  const handleSave = async () => {
    const errors: { id?: string; title?: string } = {}
    const idErr = validateRequired(formData.id, t('services.fieldServiceId'))
    const titleErr = validateRequired(formData.title, t('services.fieldTitle'))
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
      if (!isMountedRef.current) return
      handleClose()
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('manage.saveFailed', { error: extractApiError(error) }), 'error')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteMutation.mutateAsync(id)
      if (!isMountedRef.current) return
      setPendingDeleteId(null)
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('manage.deleteFailed', { error: extractApiError(error) }), 'error')
    }
  }

  const handleShowVersions = (serviceName: string) => {
    setVersionsServiceName(serviceName)
    setVersionsDialogOpen(true)
  }

  const handleRollback = async (serviceName: string, version: number) => {
    try {
      await rollbackMutation.mutateAsync({ serviceName, version })
      if (!isMountedRef.current) return
      setVersionsDialogOpen(false)
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('manage.rollbackFailed', { error: extractApiError(error) }), 'error')
    }
  }

  const handleSelectService = (service: CdsServiceConfigResponse) => {
    if (activeServiceId === service.id) {
      setActiveServiceId(null)
      return
    }
    if (service.cqlContent) {
      // Warn before clobbering edits the user hasn't pushed back to a service.
      const hasUnsavedEdits =
        cqlContent.trim() !== '' &&
        cqlContent !== service.cqlContent &&
        cqlContent !== lastLoadedCqlRef.current
      if (hasUnsavedEdits) {
        const proceed = window.confirm(t('manage.switchServiceConfirm'))
        if (!proceed) return
      }
      dispatch(setCqlContent(service.cqlContent))
      lastLoadedCqlRef.current = service.cqlContent
    }
    setActiveServiceId(service.id)
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
          defaultIndicator: service.defaultIndicator || DEFAULT_INDICATOR,
          enabled: service.enabled,
        },
      })
      if (!isMountedRef.current) return
      // Once the user has pushed edits back to the service, what's in the
      // editor is no longer "unsaved" — update the baseline so the next
      // service switch doesn't trigger a false-positive confirm dialog.
      lastLoadedCqlRef.current = cqlContent
      showNotification(t('manage.cqlSavedToService'), 'success')
    } catch (error) {
      if (!isMountedRef.current) return
      showNotification(t('manage.cqlSaveFailed', { error: extractApiError(error) }), 'error')
    }
  }

  if (isLoading) return <TableSkeleton columns={5} />
  if (isError) return <Alert severity="error">{t('manage.loadError')}</Alert>

  return (
    <Stack spacing={2}>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1">{t('manage.title')}</Typography>
        <GradientButton
          startIcon={<AddIcon />}
          onClick={handleOpenCreate}
        >
          {t('manage.newService')}
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
              {updateMutation.isPending ? t('manage.saving') : t('manage.saveCql')}
            </Button>
          }
        >
          <Trans i18nKey="manage.editingAlert" ns="cds" values={{ name: services?.find((s) => s.id === activeServiceId)?.title }} components={{ strong: <strong /> }} />
        </Alert>
      )}

      {services?.length === 0 && (
        <Alert severity="info">{t('manage.noServices')}</Alert>
      )}

      {services?.map((service) => {
        const isOwner = !service.ownerUsername || service.ownerUsername === currentUsername
        const isShared = service.shared
        const ownershipLabel = isShared ? t('manage.shared') : isOwner ? t('manage.mine') : t('manage.ownerLabel', { owner: service.ownerUsername })

        return (
          <Card
            key={service.id}
            variant="outlined"
            onClick={() => handleSelectService(service)}
            sx={(theme) => ({
              transition: 'all 0.25s ease',
              cursor: 'pointer',
              ...(activeServiceId === service.id && {
                borderColor: 'primary.main',
                borderWidth: 2,
                bgcolor: alpha(theme.palette.primary.main, 0.04),
              }),
              '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: `0 6px 20px ${alpha(theme.palette.primary.main, 0.12)}`,
              },
            })}
          >
            <CardContent sx={{ pb: 1 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="subtitle1" fontWeight="bold" sx={{ color: 'text.primary' }}>
                      {service.title}
                    </Typography>
                    {service.version && <Chip label={t('manage.versionLabel', { version: service.version })} size="small" variant="outlined" />}
                    <Chip
                      label={ownershipLabel}
                      size="small"
                      color={isShared ? 'primary' : isOwner ? 'default' : 'secondary'}
                      variant="outlined"
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {t('manage.serviceDetails', { id: service.id, hook: service.hook })}
                    {service.serviceName && service.serviceName !== service.id && t('manage.serviceNameSuffix', { name: service.serviceName })}
                  </Typography>
                  {service.description && (
                    <Typography variant="body2" sx={{ mt: 1, color: 'text.primary' }}>
                      {service.description}
                    </Typography>
                  )}
                </Box>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip
                    label={service.enabled ? t('manage.enabled') : t('manage.disabled')}
                    color={service.enabled ? 'success' : 'default'}
                    size="small"
                  />
                  {service.serviceName && (
                    <IconButton
                      size="small"
                      onClick={(e) => { e.stopPropagation(); handleShowVersions(service.serviceName!) }}
                      title={t('manage.viewVersions')}
                      aria-label={t('manage.viewVersions')}
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
                    title={service.shared ? t('manage.unshare') : t('manage.shareService')}
                    aria-label={service.shared ? t('manage.unshare') : t('manage.shareService')}
                  >
                    <ShareIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={(e) => { e.stopPropagation(); handleOpenEdit(service) }}
                    sx={{ color: 'primary.main' }}
                    disabled={!isOwner && !isShared}
                    aria-label={t('manage.editService')}
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={(e) => { e.stopPropagation(); setPendingDeleteId(service.id) }}
                    disabled={!isOwner}
                    aria-label={t('manage.deleteService')}
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
        <DialogTitle>{editingService ? t('manage.editDialogTitle') : t('manage.createDialogTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label={t('manage.serviceIdLabel')}
              value={formData.id}
              onChange={(e) => {
                setFormData({ ...formData, id: e.target.value })
                setFormErrors((prev) => ({ ...prev, id: undefined }))
              }}
              size="small"
              fullWidth
              disabled={!!editingService}
              helperText={formErrors.id || t('manage.serviceIdHelperText')}
              error={!!formErrors.id}
              inputProps={{ maxLength: CDS_SERVICE.id.maxLength }}
            />

            <TextField
              label={t('manage.titleLabel')}
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
              inputProps={{ maxLength: CDS_SERVICE.title.maxLength }}
            />

            <Stack direction="row" spacing={1} alignItems="center">
              <FormControl fullWidth size="small">
                <InputLabel>{t('manage.hookTypeLabel')}</InputLabel>
                <Select
                  value={formData.hook}
                  onChange={(e) => setFormData({ ...formData, hook: e.target.value })}
                  label={t('manage.hookTypeLabel')}
                >
                  {CDS_HOOK_IDS.map((hook) => (
                    <MenuItem key={hook} value={hook}>
                      {hook}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <HelpTooltip text={helpContent.cds.hookType} />
            </Stack>

            {formData.hook && (
              <Alert severity="info" sx={{ py: 0.5 }}>
                {t('manage.contextFieldsHint', {
                  fields: getRequiredContextFields(formData.hook)
                    .map((f) => f.name)
                    .join(', '),
                })}
              </Alert>
            )}

            <TextField
              label={t('manage.descriptionLabel')}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              size="small"
              fullWidth
              multiline
              rows={2}
              inputProps={{ maxLength: CDS_SERVICE.description.maxLength }}
              helperText={`${(formData.description || '').length} / ${CDS_SERVICE.description.maxLength}`}
            />

            <FormControl fullWidth size="small">
              <InputLabel>{t('manage.defaultIndicatorLabel')}</InputLabel>
              <Select
                value={formData.defaultIndicator}
                onChange={(e) => setFormData({ ...formData, defaultIndicator: e.target.value })}
                label={t('manage.defaultIndicatorLabel')}
              >
                {CDS_INDICATOR_TYPES.map((indicator) => (
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
                {t('manage.loadFromLibrary')}
              </Button>
            </Stack>
            <TextField
              label={t('manage.cqlContentLabel')}
              value={formData.cqlContent}
              onChange={(e) => setFormData({ ...formData, cqlContent: e.target.value })}
              size="small"
              fullWidth
              multiline
              rows={8}
              placeholder={t('manage.cqlContentPlaceholder')}
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
              label={t('manage.enabledLabel')}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={handleClose} sx={{ color: 'text.secondary' }}>
            {tc('actions.cancel')}
          </Button>
          <GradientButton
            onClick={handleSave}
            disabled={!formData.id || !formData.title || createMutation.isPending || updateMutation.isPending}
          >
            {createMutation.isPending || updateMutation.isPending ? t('manage.saving') : tc('actions.save')}
          </GradientButton>
        </DialogActions>
      </Dialog>

      <Dialog open={versionsDialogOpen} onClose={() => setVersionsDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('manage.versionsDialogTitle', { name: versionsServiceName })}</DialogTitle>
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
                      {v.enabled ? t('manage.versionActive') : t('manage.versionRollback')}
                    </Button>
                  }
                >
                  <ListItemText
                    primary={t('manage.versionListEntry', { version: v.version, title: v.title })}
                    secondary={t('manage.versionInfo', { hook: v.hook, status: v.enabled ? t('manage.enabled') : t('manage.disabled'), date: v.createdAt || '' })}
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {t('manage.noVersions')}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionsDialogOpen(false)}>{tc('actions.close')}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDeleteDialog
        open={!!pendingDeleteId}
        itemName={t('manage.deleteItemName')}
        message={t('manage.deleteConfirmMessage')}
        onCancel={() => setPendingDeleteId(null)}
        onConfirm={() => pendingDeleteId && handleDelete(pendingDeleteId)}
        isPending={deleteMutation.isPending}
      />
    </Stack>
  )
}
