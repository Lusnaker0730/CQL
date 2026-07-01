import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { downloadBlob } from '../../utils/download'
import {
  Box,
  Tabs,
  Tab,
  Paper,
  Stack,
  Typography,
  Button,
  Divider,
  Alert,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@mui/material'
import {
  History as HistoryIcon,
  CompareArrows as CompareIcon,
  NewReleases as VersionIcon,
  Share as ShareIcon,
  RateReview as SubmitIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Archive as RetireIcon,
  Assignment as AuditIcon,
  Download as DownloadIcon,
  Lock as LockIcon,
  LockOpen as LockOpenIcon,
} from '@mui/icons-material'
import { Menu, MenuItem } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { MeasureDefinition } from '../../types'
import { measureApi } from '../../api'
import MeasureDetailsTab from './MeasureDetailsTab'
import MeasureCqlTab from './MeasureCqlTab'
import DataRequirementsTab from './DataRequirementsTab'
import PopulationCriteriaTab from './PopulationCriteriaTab'
import MeasureEvaluationTab from './MeasureEvaluationTab'
import MeasureReportHistory from './MeasureReportHistory'
import TestCasesTab from './TestCasesTab'
import WorkflowIndicator from './WorkflowIndicator'
import StatusChip from '../common/StatusChip'
import CreateVersionDialog from '../editor/CreateVersionDialog'
import VersionHistoryDialog from '../editor/VersionHistoryDialog'
import VersionDiffDialog from '../editor/VersionDiffDialog'
import MeasureShareDialog from './MeasureShareDialog'
import AuditTrailDialog from './AuditTrailDialog'
import MeasureValidationPanel from './MeasureValidationPanel'
import {
  useSubmitForReview,
  useApproveMeasure,
  useRejectMeasure,
  useRetireMeasure,
  useLockMeasure,
  useUnlockMeasure,
} from '../../hooks/useMeasures'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import { getStoredUsername } from '../../utils/validation'
import { MEASURE_STATUS } from '../../constants/measureConstants'
import { ALERT_DISMISS_MS, ALERT_DISMISS_ERROR_MS } from '../../constants/timing'

interface MeasureEditorProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureEditor({ measure, onMeasureUpdate }: MeasureEditorProps) {
  const { t } = useTranslation('measures')
  const [tab, setTab] = useState(0)
  const [versionDialogOpen, setVersionDialogOpen] = useState(false)
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [diffDialogOpen, setDiffDialogOpen] = useState(false)
  const [shareDialogOpen, setShareDialogOpen] = useState(false)
  const [auditDialogOpen, setAuditDialogOpen] = useState(false)
  const [workflowAlert, setWorkflowAlert] = useState<{ severity: 'success' | 'error'; message: string } | null>(null)
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null)
  const [versionAnchor, setVersionAnchor] = useState<HTMLElement | null>(null)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const queryClient = useQueryClient()

  const { showNotification } = useNotification()
  const currentUser = useMemo(() => getStoredUsername(), [])
  const isOwner = measure.ownerUsername === currentUser || !measure.ownerUsername
  const isReviewer = isOwner || (measure.sharedWith?.includes(currentUser) ?? false)

  const submitMutation = useSubmitForReview()
  const approveMutation = useApproveMeasure()
  const rejectMutation = useRejectMeasure()
  const retireMutation = useRetireMeasure()
  const lockMutation = useLockMeasure()
  const unlockMutation = useUnlockMeasure()

  const isLockedByOther = !!measure.lockedBy && measure.lockedBy !== currentUser
  const isLockedByMe = !!measure.lockedBy && measure.lockedBy === currentUser

  /**
   * PAT-130: shared workflow / lock action runner. Original code had two
   * shapes — `handleWorkflowAction` used by submit/approve/retire (showing a
   * success alert) and inline `mutate` calls for lock/unlock (no success
   * message + ad-hoc error fallback text). The split made it easy for new
   * actions to drift, and PAT-117 was a regression of that drift. Now every
   * workflow button funnels through this single helper. `successMsg` may be
   * null when we want a quiet success (lock/unlock) but errors always alert.
   */
  const runWorkflowMutation = (
    mutation: typeof submitMutation,
    successMsg: string | null,
    errorFallback?: string,
  ) => {
    if (!measure.id) return
    mutation.mutate(measure.id, {
      onSuccess: (updated) => {
        onMeasureUpdate(updated)
        queryClient.invalidateQueries({ queryKey: ['measures'] })
        if (successMsg) {
          setWorkflowAlert({ severity: 'success', message: successMsg })
          setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
        }
      },
      onError: (err) => {
        const fallback = errorFallback || t('editor.errors.actionFailed')
        setWorkflowAlert({ severity: 'error', message: extractApiError(err) || fallback })
        setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_ERROR_MS)
      },
    })
  }

  const handleWorkflowAction = (
    mutation: typeof submitMutation,
    successMsg: string,
  ) => runWorkflowMutation(mutation, successMsg)

  const versionMutation = useMutation({
    mutationFn: (type: string) => measureApi.createMeasureVersion(measure.id!, type),
    onSuccess: (newMeasure) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      onMeasureUpdate(newMeasure)
      setVersionDialogOpen(false)
    },
  })

  const { data: historyData = [] } = useQuery({
    queryKey: ['measure-history', measure.id],
    queryFn: () => measureApi.getMeasureHistory(measure.id!),
    enabled: historyDialogOpen && !!measure.id,
  })

  const handleSelectVersion = (id: string | number) => {
    const numId = typeof id === 'string' ? parseInt(id, 10) : id
    measureApi.getMeasure(numId).then((m) => {
      onMeasureUpdate(m)
      setHistoryDialogOpen(false)
    }).catch((err) => {
      showNotification(t('editor.errors.loadVersionFailed', { error: extractApiError(err) }), 'error')
    })
  }

  const handleCompare = async (oldId: string | number, newId: string | number) => {
    const oldNum = typeof oldId === 'string' ? parseInt(oldId, 10) : oldId
    const newNum = typeof newId === 'string' ? parseInt(newId, 10) : newId
    return measureApi.compareMeasureVersions(oldNum, newNum)
  }

  const handleExport = async (format: string) => {
    setExportAnchor(null)
    if (!measure.id) return
    try {
      let blob: Blob
      let filename: string
      switch (format) {
        case 'bundle-json':
          blob = await measureApi.exportBundle(measure.id, 'json')
          filename = `${measure.name}-bundle.json`
          break
        case 'bundle-xml':
          blob = await measureApi.exportBundle(measure.id, 'xml')
          filename = `${measure.name}-bundle.xml`
          break
        case 'cql':
          blob = await measureApi.exportCql(measure.id)
          filename = `${measure.name}.cql`
          break
        case 'elm':
          blob = await measureApi.exportElm(measure.id)
          filename = `${measure.name}-elm.json`
          break
        case 'hqmf':
          blob = await measureApi.exportHqmf(measure.id)
          filename = `${measure.name}-hqmf.xml`
          break
        case 'human-readable':
          blob = await measureApi.exportHumanReadable(measure.id)
          filename = `${measure.name}-narrative.html`
          break
        default:
          return
      }
      downloadBlob(blob, filename)
    } catch (err) {
      setWorkflowAlert({ severity: 'error', message: t('editor.errors.exportFailed', { error: extractApiError(err) }) })
      setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
    }
  }

  const historyVersions = useMemo(
    () =>
      historyData.map((m) => ({
        id: m.id!,
        version: m.version,
        status: m.status,
        createdAt: m.createdAt,
        updatedAt: m.updatedAt,
      })),
    [historyData]
  )

  const diffVersions = useMemo(
    () =>
      historyData.map((m) => ({
        id: m.id!,
        version: m.version,
      })),
    [historyData]
  )

  return (
    <Paper sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {workflowAlert && (
        <Alert
          severity={workflowAlert.severity}
          onClose={() => setWorkflowAlert(null)}
          sx={{ borderRadius: 0 }}
        >
          {workflowAlert.message}
        </Alert>
      )}
      {isLockedByOther && (
        <Alert severity="warning" icon={<LockIcon />} sx={{ borderRadius: 0 }}>
          {t('editor.lockedWarning', { user: measure.lockedBy })}
          {measure.lockedAt && ` at ${new Date(measure.lockedAt).toLocaleString()}`}
        </Alert>
      )}
      {measure.reviewComment && measure.status === MEASURE_STATUS.DRAFT && (
        <Alert severity="info" sx={{ borderRadius: 0 }}>
          {t('editor.rejectionNotice', { reviewer: measure.reviewedBy || '—', comment: measure.reviewComment })}
        </Alert>
      )}
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          px: 2,
          pt: 1,
          pb: 0.5
        }}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography variant="subtitle1" noWrap sx={{ maxWidth: 240 }}>
            {measure.title || measure.name}
          </Typography>
          <StatusChip status={measure.status || 'draft'} />
          <Typography
            variant="caption"
            sx={{
              color: "text.secondary",
              ml: 1
            }}>
            v{measure.version}
          </Typography>
          <Button
            size="small"
            startIcon={<HistoryIcon />}
            onClick={(e) => setVersionAnchor(e.currentTarget)}
            sx={{ textTransform: 'none', fontSize: '0.75rem', color: 'text.secondary', minWidth: 'auto' }}
          >
            {t('editor.buttons.versioning')}
          </Button>
        </Stack>
        <Stack direction="row" spacing={0.5} sx={{
          alignItems: "center"
        }}>
          {!measure.lockedBy && (
            <Button
              size="small"
              startIcon={<LockIcon />}
              onClick={() => runWorkflowMutation(lockMutation, null, t('editor.errors.lockFailed'))}
              disabled={lockMutation.isPending}
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              {t('editor.buttons.lock')}
            </Button>
          )}
          {isLockedByMe && (
            <Button
              size="small"
              startIcon={<LockOpenIcon />}
              onClick={() => runWorkflowMutation(unlockMutation, null, t('editor.errors.unlockFailed'))}
              disabled={unlockMutation.isPending}
              color="warning"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              {t('editor.buttons.unlock')}
            </Button>
          )}
          {isLockedByOther && (
            <Chip
              icon={<LockIcon />}
              label={t('editor.lockedBy', { user: measure.lockedBy })}
              size="small"
              color="warning"
              variant="outlined"
              sx={{ fontSize: '0.7rem', height: 24 }}
            />
          )}
          <Button
            size="small"
            startIcon={<ShareIcon />}
            onClick={() => setShareDialogOpen(true)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            {t('editor.buttons.share')}
          </Button>
          <Button
            size="small"
            startIcon={<AuditIcon />}
            onClick={() => setAuditDialogOpen(true)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            {t('editor.buttons.audit')}
          </Button>
          <Button
            size="small"
            startIcon={<DownloadIcon />}
            onClick={(e) => setExportAnchor(e.currentTarget)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            {t('editor.buttons.export')}
          </Button>
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          {/* Workflow actions based on current status */}
          {measure.status === MEASURE_STATUS.DRAFT && isOwner && (
            <Button
              size="small"
              startIcon={<SubmitIcon />}
              onClick={() => handleWorkflowAction(submitMutation, t('editor.workflowMessages.submitted'))}
              disabled={submitMutation.isPending}
              color="info"
              variant="outlined"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              {t('editor.buttons.submitForReview')}
            </Button>
          )}
          {measure.status === MEASURE_STATUS.IN_REVIEW && isReviewer && (
            <>
              <Button
                size="small"
                startIcon={<ApproveIcon />}
                onClick={() => handleWorkflowAction(approveMutation, t('editor.workflowMessages.approved'))}
                disabled={approveMutation.isPending}
                color="success"
                variant="outlined"
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                {t('editor.buttons.approve')}
              </Button>
              <Button
                size="small"
                startIcon={<RejectIcon />}
                onClick={() => { setRejectReason(''); setRejectDialogOpen(true) }}
                disabled={rejectMutation.isPending}
                color="error"
                variant="outlined"
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                {t('editor.buttons.reject')}
              </Button>
            </>
          )}
          {measure.status === MEASURE_STATUS.ACTIVE && isOwner && (
            <Button
              size="small"
              startIcon={<RetireIcon />}
              onClick={() => handleWorkflowAction(retireMutation, t('editor.workflowMessages.retired'))}
              disabled={retireMutation.isPending}
              color="warning"
              variant="outlined"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              {t('editor.buttons.retire')}
            </Button>
          )}
          <WorkflowIndicator measure={measure} />
        </Stack>
      </Stack>
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            '& .MuiTab-root': { textTransform: 'none', minHeight: 42, fontSize: '0.85rem' },
          }}
        >
          <Tab label={t('editor.tabs.details')} />
          <Tab label={t('editor.tabs.cql')} />
          <Tab label={t('editor.tabs.dataRequirements')} />
          <Tab label={t('editor.tabs.populationCriteria')} />
          <Tab label={t('editor.tabs.evaluate')} />
          <Tab label={t('editor.tabs.testCases')} />
          <Tab label={t('editor.tabs.reports')} />
          <Tab label={t('editor.tabs.validate')} />
        </Tabs>
      </Box>
      <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {tab === 0 && (
          <MeasureDetailsTab measure={measure} onMeasureUpdate={onMeasureUpdate} readOnly={isLockedByOther} />
        )}
        {tab === 1 && (
          <MeasureCqlTab measure={measure} onMeasureUpdate={onMeasureUpdate} readOnly={isLockedByOther} />
        )}
        {tab === 2 && (
          <DataRequirementsTab measure={measure} />
        )}
        {tab === 3 && (
          <PopulationCriteriaTab measure={measure} onMeasureUpdate={onMeasureUpdate} readOnly={isLockedByOther} />
        )}
        {tab === 4 && (
          <MeasureEvaluationTab measure={measure} />
        )}
        {tab === 5 && (
          <TestCasesTab measure={measure} readOnly={isLockedByOther} />
        )}
        {tab === 6 && (
          <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
            <MeasureReportHistory measureId={measure.id} />
          </Box>
        )}
        {tab === 7 && (
          <MeasureValidationPanel
            measureId={measure.id}
            onNavigateToTab={(tabIndex) => setTab(tabIndex)}
          />
        )}
      </Box>
      <CreateVersionDialog
        open={versionDialogOpen}
        onClose={() => setVersionDialogOpen(false)}
        onConfirm={(type) => versionMutation.mutate(type)}
        currentVersion={measure.version}
        isPending={versionMutation.isPending}
        entityType="measure"
      />
      <VersionHistoryDialog
        open={historyDialogOpen}
        onClose={() => setHistoryDialogOpen(false)}
        versions={historyVersions}
        onSelectVersion={handleSelectVersion}
        entityType="measure"
      />
      <VersionDiffDialog
        open={diffDialogOpen}
        onClose={() => setDiffDialogOpen(false)}
        versions={diffVersions}
        onCompare={handleCompare}
      />
      <MeasureShareDialog
        open={shareDialogOpen}
        onClose={() => setShareDialogOpen(false)}
        measure={measure}
        onMeasureUpdate={onMeasureUpdate}
      />
      <AuditTrailDialog
        open={auditDialogOpen}
        onClose={() => setAuditDialogOpen(false)}
        measureId={measure.id}
        measureName={measure.title || measure.name}
      />
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('editor.rejectDialog.title')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={t('editor.rejectDialog.reasonLabel')}
            placeholder={t('editor.rejectDialog.reasonPlaceholder')}
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => {
              setRejectDialogOpen(false)
              if (!measure.id) return
              rejectMutation.mutate({ id: measure.id, reason: rejectReason || undefined }, {
                onSuccess: (updated) => {
                  onMeasureUpdate(updated)
                  setWorkflowAlert({ severity: 'success', message: t('editor.workflowMessages.rejected') })
                  queryClient.invalidateQueries({ queryKey: ['measures'] })
                  setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
                },
                onError: (err) => {
                  setWorkflowAlert({ severity: 'error', message: extractApiError(err) || t('editor.errors.actionFailed') })
                  setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_ERROR_MS)
                },
              })
            }}
          >
            {t('editor.buttons.reject')}
          </Button>
        </DialogActions>
      </Dialog>
      <Menu
        anchorEl={exportAnchor}
        open={Boolean(exportAnchor)}
        onClose={() => setExportAnchor(null)}
      >
        <MenuItem onClick={() => handleExport('bundle-json')}>{t('editor.exportFormats.fhirJson')}</MenuItem>
        <MenuItem onClick={() => handleExport('bundle-xml')}>{t('editor.exportFormats.fhirXml')}</MenuItem>
        <MenuItem onClick={() => handleExport('cql')}>{t('editor.exportFormats.cqlOnly')}</MenuItem>
        <MenuItem onClick={() => handleExport('elm')}>{t('editor.exportFormats.elmOnly')}</MenuItem>
        <MenuItem onClick={() => handleExport('hqmf')}>{t('editor.exportFormats.hqmfXml')}</MenuItem>
        <MenuItem onClick={() => handleExport('human-readable')}>{t('editor.exportFormats.humanReadable')}</MenuItem>
      </Menu>
      <Menu
        anchorEl={versionAnchor}
        open={Boolean(versionAnchor)}
        onClose={() => setVersionAnchor(null)}
      >
        <MenuItem onClick={() => { setVersionAnchor(null); setVersionDialogOpen(true) }}>
          <VersionIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> {t('editor.versionMenu.createVersion')}
        </MenuItem>
        <MenuItem onClick={() => { setVersionAnchor(null); setHistoryDialogOpen(true) }}>
          <HistoryIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> {t('editor.versionMenu.versionHistory')}
        </MenuItem>
        <MenuItem onClick={() => { setVersionAnchor(null); setDiffDialogOpen(true) }}>
          <CompareIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> {t('editor.versionMenu.compareVersions')}
        </MenuItem>
      </Menu>
    </Paper>
  );
}
