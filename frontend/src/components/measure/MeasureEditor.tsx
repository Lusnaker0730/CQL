import { useState, useMemo } from 'react'
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
import { getStoredUsername } from '../../utils/validation'
import { MEASURE_STATUS } from '../../constants/measureConstants'
import { ALERT_DISMISS_MS, ALERT_DISMISS_ERROR_MS } from '../../constants/timing'

interface MeasureEditorProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureEditor({ measure, onMeasureUpdate }: MeasureEditorProps) {
  const [tab, setTab] = useState(0)
  const [versionDialogOpen, setVersionDialogOpen] = useState(false)
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [diffDialogOpen, setDiffDialogOpen] = useState(false)
  const [shareDialogOpen, setShareDialogOpen] = useState(false)
  const [auditDialogOpen, setAuditDialogOpen] = useState(false)
  const [workflowAlert, setWorkflowAlert] = useState<{ severity: 'success' | 'error'; message: string } | null>(null)
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null)
  const [versionAnchor, setVersionAnchor] = useState<HTMLElement | null>(null)
  const queryClient = useQueryClient()

  const { showNotification } = useNotification()
  const currentUser = getStoredUsername()
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

  const handleWorkflowAction = (
    mutation: typeof submitMutation,
    successMsg: string
  ) => {
    if (!measure.id) return
    mutation.mutate(measure.id, {
      onSuccess: (updated) => {
        onMeasureUpdate(updated)
        setWorkflowAlert({ severity: 'success', message: successMsg })
        queryClient.invalidateQueries({ queryKey: ['measures'] })
        setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
      },
      onError: (err) => {
        setWorkflowAlert({ severity: 'error', message: (err as Error).message || 'Action failed' })
        setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_ERROR_MS)
      },
    })
  }

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
      showNotification('Failed to load measure version: ' + (err as Error).message, 'error')
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
        default:
          return
      }
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setWorkflowAlert({ severity: 'error', message: 'Export failed: ' + (err as Error).message })
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
          Locked by {measure.lockedBy}
          {measure.lockedAt && ` at ${new Date(measure.lockedAt).toLocaleString()}`}
          . Editing disabled.
        </Alert>
      )}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ px: 2, pt: 1, pb: 0.5 }}
      >
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="subtitle1" noWrap sx={{ maxWidth: 240 }}>
            {measure.title || measure.name}
          </Typography>
          <StatusChip status={measure.status || 'draft'} />
          <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
            v{measure.version}
          </Typography>
          <Button
            size="small"
            startIcon={<HistoryIcon />}
            onClick={(e) => setVersionAnchor(e.currentTarget)}
            sx={{ textTransform: 'none', fontSize: '0.75rem', color: 'text.secondary', minWidth: 'auto' }}
          >
            Versioning
          </Button>
        </Stack>
        <Stack direction="row" spacing={0.5} alignItems="center">
          {!measure.lockedBy && (
            <Button
              size="small"
              startIcon={<LockIcon />}
              onClick={() => {
                if (!measure.id) return
                lockMutation.mutate(measure.id, {
                  onSuccess: (updated) => {
                    onMeasureUpdate(updated)
                    queryClient.invalidateQueries({ queryKey: ['measures'] })
                  },
                  onError: (err) => {
                    setWorkflowAlert({ severity: 'error', message: (err as Error).message || 'Lock failed' })
                    setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
                  },
                })
              }}
              disabled={lockMutation.isPending}
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              Lock
            </Button>
          )}
          {isLockedByMe && (
            <Button
              size="small"
              startIcon={<LockOpenIcon />}
              onClick={() => {
                if (!measure.id) return
                unlockMutation.mutate(measure.id, {
                  onSuccess: (updated) => {
                    onMeasureUpdate(updated)
                    queryClient.invalidateQueries({ queryKey: ['measures'] })
                  },
                  onError: (err) => {
                    setWorkflowAlert({ severity: 'error', message: (err as Error).message || 'Unlock failed' })
                    setTimeout(() => setWorkflowAlert(null), ALERT_DISMISS_MS)
                  },
                })
              }}
              disabled={unlockMutation.isPending}
              color="warning"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              Unlock
            </Button>
          )}
          {isLockedByOther && (
            <Chip
              icon={<LockIcon />}
              label={`Locked by ${measure.lockedBy}`}
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
            Share
          </Button>
          <Button
            size="small"
            startIcon={<AuditIcon />}
            onClick={() => setAuditDialogOpen(true)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            Audit
          </Button>
          <Button
            size="small"
            startIcon={<DownloadIcon />}
            onClick={(e) => setExportAnchor(e.currentTarget)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            Export
          </Button>
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          {/* Workflow actions based on current status */}
          {measure.status === MEASURE_STATUS.DRAFT && isOwner && (
            <Button
              size="small"
              startIcon={<SubmitIcon />}
              onClick={() => handleWorkflowAction(submitMutation, 'Measure submitted for review')}
              disabled={submitMutation.isPending}
              color="info"
              variant="outlined"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              Submit for Review
            </Button>
          )}
          {measure.status === MEASURE_STATUS.IN_REVIEW && isReviewer && (
            <>
              <Button
                size="small"
                startIcon={<ApproveIcon />}
                onClick={() => handleWorkflowAction(approveMutation, 'Measure approved and set to active')}
                disabled={approveMutation.isPending}
                color="success"
                variant="outlined"
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                Approve
              </Button>
              <Button
                size="small"
                startIcon={<RejectIcon />}
                onClick={() => handleWorkflowAction(rejectMutation, 'Measure rejected, returned to draft')}
                disabled={rejectMutation.isPending}
                color="error"
                variant="outlined"
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                Reject
              </Button>
            </>
          )}
          {measure.status === MEASURE_STATUS.ACTIVE && isOwner && (
            <Button
              size="small"
              startIcon={<RetireIcon />}
              onClick={() => handleWorkflowAction(retireMutation, 'Measure retired')}
              disabled={retireMutation.isPending}
              color="warning"
              variant="outlined"
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
            >
              Retire
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
          <Tab label="Details" />
          <Tab label="CQL" />
          <Tab label="Population Criteria" />
          <Tab label="Evaluate" />
          <Tab label="Test Cases" />
          <Tab label="Reports" />
          <Tab label="Validate" />
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
          <PopulationCriteriaTab measure={measure} onMeasureUpdate={onMeasureUpdate} readOnly={isLockedByOther} />
        )}
        {tab === 3 && (
          <MeasureEvaluationTab measure={measure} />
        )}
        {tab === 4 && (
          <TestCasesTab measure={measure} readOnly={isLockedByOther} />
        )}
        {tab === 5 && (
          <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
            <MeasureReportHistory />
          </Box>
        )}
        {tab === 6 && (
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

      <Menu
        anchorEl={exportAnchor}
        open={Boolean(exportAnchor)}
        onClose={() => setExportAnchor(null)}
      >
        <MenuItem onClick={() => handleExport('bundle-json')}>FHIR Bundle (JSON)</MenuItem>
        <MenuItem onClick={() => handleExport('bundle-xml')}>FHIR Bundle (XML)</MenuItem>
        <MenuItem onClick={() => handleExport('cql')}>CQL Only</MenuItem>
        <MenuItem onClick={() => handleExport('elm')}>ELM Only</MenuItem>
        <MenuItem onClick={() => handleExport('hqmf')}>HQMF (XML)</MenuItem>
      </Menu>

      <Menu
        anchorEl={versionAnchor}
        open={Boolean(versionAnchor)}
        onClose={() => setVersionAnchor(null)}
      >
        <MenuItem onClick={() => { setVersionAnchor(null); setVersionDialogOpen(true) }}>
          <VersionIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> Create Version
        </MenuItem>
        <MenuItem onClick={() => { setVersionAnchor(null); setHistoryDialogOpen(true) }}>
          <HistoryIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> Version History
        </MenuItem>
        <MenuItem onClick={() => { setVersionAnchor(null); setDiffDialogOpen(true) }}>
          <CompareIcon sx={{ mr: 1, fontSize: '1.1rem' }} /> Compare Versions
        </MenuItem>
      </Menu>
    </Paper>
  )
}
