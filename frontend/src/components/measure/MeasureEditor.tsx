import { useState } from 'react'
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
} from '@mui/icons-material'
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
import {
  useSubmitForReview,
  useApproveMeasure,
  useRejectMeasure,
  useRetireMeasure,
} from '../../hooks/useMeasures'

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
  const queryClient = useQueryClient()

  const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username
  const isOwner = measure.ownerUsername === currentUser || !measure.ownerUsername
  const isReviewer = isOwner || (measure.sharedWith?.includes(currentUser) ?? false)

  const submitMutation = useSubmitForReview()
  const approveMutation = useApproveMeasure()
  const rejectMutation = useRejectMeasure()
  const retireMutation = useRetireMeasure()

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
        setTimeout(() => setWorkflowAlert(null), 5000)
      },
      onError: (err) => {
        setWorkflowAlert({ severity: 'error', message: (err as Error).message || 'Action failed' })
        setTimeout(() => setWorkflowAlert(null), 8000)
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
    })
  }

  const handleCompare = async (oldId: string | number, newId: string | number) => {
    const oldNum = typeof oldId === 'string' ? parseInt(oldId, 10) : oldId
    const newNum = typeof newId === 'string' ? parseInt(newId, 10) : newId
    return measureApi.compareMeasureVersions(oldNum, newNum)
  }

  const historyVersions = historyData.map((m) => ({
    id: m.id!,
    version: m.version,
    status: m.status,
    createdAt: m.createdAt,
    updatedAt: m.updatedAt,
  }))

  const diffVersions = historyData.map((m) => ({
    id: m.id!,
    version: m.version,
  }))

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
        </Stack>
        <Stack direction="row" spacing={0.5} alignItems="center">
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
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          {/* Workflow actions based on current status */}
          {measure.status === 'draft' && isOwner && (
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
          {measure.status === 'in-review' && isReviewer && (
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
          {measure.status === 'active' && isOwner && (
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
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          <Button
            size="small"
            startIcon={<VersionIcon />}
            onClick={() => setVersionDialogOpen(true)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            Version
          </Button>
          <Button
            size="small"
            startIcon={<HistoryIcon />}
            onClick={() => setHistoryDialogOpen(true)}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            History
          </Button>
          <Button
            size="small"
            startIcon={<CompareIcon />}
            onClick={() => { setHistoryDialogOpen(false); setDiffDialogOpen(true) }}
            sx={{ textTransform: 'none', fontSize: '0.75rem' }}
          >
            Compare
          </Button>
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
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {tab === 0 && (
          <MeasureDetailsTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 1 && (
          <MeasureCqlTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 2 && (
          <PopulationCriteriaTab measure={measure} onMeasureUpdate={onMeasureUpdate} />
        )}
        {tab === 3 && (
          <MeasureEvaluationTab measure={measure} />
        )}
        {tab === 4 && (
          <TestCasesTab measure={measure} />
        )}
        {tab === 5 && (
          <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
            <MeasureReportHistory />
          </Box>
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
    </Paper>
  )
}
