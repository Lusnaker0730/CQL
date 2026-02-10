import { useState } from 'react'
import {
  Box, Typography, Stack, IconButton, Tooltip, TextField, Menu, MenuItem, ListItemIcon, ListItemText,
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Alert,
} from '@mui/material'
import {
  ArrowBack as BackIcon, Save as SaveIcon, MoreVert as MoreIcon,
  CloudUpload as DeployIcon, LibraryBooks as LibraryIcon, Download as DownloadIcon,
} from '@mui/icons-material'
import StatusChip from '../common/StatusChip'
import { useDeployCdsService, useSaveAsLibrary } from '../../hooks/useArtifactTesting'
import { useGenerateArtifactCql } from '../../hooks/useArtifactCql'
import type { Artifact, ArtifactRequest } from '../../types/authoring'

interface ArtifactWorkspaceHeaderProps {
  artifact: Artifact
  isDirty: boolean
  onBack: () => void
  onSave: (request: ArtifactRequest) => void
  onNameChange: (name: string) => void
}

export default function ArtifactWorkspaceHeader({
  artifact,
  isDirty,
  onBack,
  onSave,
  onNameChange,
}: ArtifactWorkspaceHeaderProps) {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const [deployDialog, setDeployDialog] = useState(false)
  const [deployHook, setDeployHook] = useState('patient-view')
  const [deployResult, setDeployResult] = useState<string | null>(null)
  const [saveLibResult, setSaveLibResult] = useState<string | null>(null)

  const deployMutation = useDeployCdsService()
  const saveLibMutation = useSaveAsLibrary()
  const generateCqlMutation = useGenerateArtifactCql()

  const handleSave = () => {
    onSave({
      name: artifact.name,
      version: artifact.version,
      description: artifact.description,
      status: artifact.status,
      fhirVersion: artifact.fhirVersion,
      expTreeInclude: artifact.expTreeInclude,
      expTreeExclude: artifact.expTreeExclude,
      recommendations: artifact.recommendations,
      subpopulations: artifact.subpopulations,
      baseElements: artifact.baseElements,
      parameters: artifact.parameters,
      errorStatement: artifact.errorStatement,
      url: artifact.url,
      publisher: artifact.publisher,
      purpose: artifact.purpose,
      usageInfo: artifact.usageInfo,
      copyright: artifact.copyright,
      experimental: artifact.experimental,
      approvalDate: artifact.approvalDate,
      lastReviewDate: artifact.lastReviewDate,
      effectivePeriodStart: artifact.effectivePeriodStart,
      effectivePeriodEnd: artifact.effectivePeriodEnd,
      strengthOfRecommendation: artifact.strengthOfRecommendation,
      qualityOfEvidence: artifact.qualityOfEvidence,
      context: artifact.context,
      topic: artifact.topic,
      author: artifact.author,
      reviewer: artifact.reviewer,
      endorser: artifact.endorser,
      relatedArtifact: artifact.relatedArtifact,
    })
  }

  const handleDeploy = () => {
    const serviceId = artifact.name.replace(/[^a-zA-Z0-9_-]/g, '-').toLowerCase()
    deployMutation.mutate(
      { id: artifact.id, serviceId, hook: deployHook },
      {
        onSuccess: (data) => {
          setDeployResult(data.message)
          setDeployDialog(false)
        },
      }
    )
  }

  const handleSaveAsLibrary = () => {
    setAnchorEl(null)
    saveLibMutation.mutate(artifact.id, {
      onSuccess: (data) => setSaveLibResult(data.message),
    })
  }

  const handleDownloadCql = () => {
    setAnchorEl(null)
    generateCqlMutation.mutate(artifact.id, {
      onSuccess: (data) => {
        const blob = new Blob([data.cql], { type: 'text/plain' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${artifact.name.replace(/[^a-zA-Z0-9_-]/g, '_')}.cql`
        a.click()
        URL.revokeObjectURL(url)
      },
    })
  }

  return (
    <>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          p: 2,
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Tooltip title="Back to artifact list">
          <IconButton onClick={onBack} size="small">
            <BackIcon />
          </IconButton>
        </Tooltip>

        <TextField
          value={artifact.name}
          onChange={(e) => onNameChange(e.target.value)}
          variant="standard"
          sx={{
            '& .MuiInput-input': {
              fontSize: '1.1rem',
              fontWeight: 600,
            },
            minWidth: 200,
            maxWidth: 400,
          }}
        />

        <Typography variant="body2" color="text.secondary">
          v{artifact.version}
        </Typography>

        <StatusChip status={artifact.status} />

        {isDirty && (
          <Typography variant="caption" color="warning.main" sx={{ fontStyle: 'italic' }}>
            Unsaved changes
          </Typography>
        )}

        <Stack direction="row" spacing={1} sx={{ ml: 'auto' }}>
          <Tooltip title="Save (Ctrl+S)">
            <span>
              <IconButton
                onClick={handleSave}
                disabled={!isDirty}
                color={isDirty ? 'primary' : 'default'}
                size="small"
              >
                <SaveIcon />
              </IconButton>
            </span>
          </Tooltip>

          <Tooltip title="Actions">
            <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)}>
              <MoreIcon />
            </IconButton>
          </Tooltip>
          <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={() => setAnchorEl(null)}>
            <MenuItem onClick={() => { setAnchorEl(null); setDeployDialog(true) }}>
              <ListItemIcon><DeployIcon fontSize="small" /></ListItemIcon>
              <ListItemText>Deploy as CDS Service</ListItemText>
            </MenuItem>
            <MenuItem onClick={handleSaveAsLibrary} disabled={saveLibMutation.isPending}>
              <ListItemIcon><LibraryIcon fontSize="small" /></ListItemIcon>
              <ListItemText>Save as CQL Library</ListItemText>
            </MenuItem>
            <MenuItem onClick={handleDownloadCql} disabled={generateCqlMutation.isPending}>
              <ListItemIcon><DownloadIcon fontSize="small" /></ListItemIcon>
              <ListItemText>Download CQL</ListItemText>
            </MenuItem>
          </Menu>
        </Stack>
      </Box>

      {deployResult && (
        <Alert severity="success" onClose={() => setDeployResult(null)} sx={{ mx: 2, mt: 1 }}>
          {deployResult}
        </Alert>
      )}
      {saveLibResult && (
        <Alert severity="success" onClose={() => setSaveLibResult(null)} sx={{ mx: 2, mt: 1 }}>
          {saveLibResult}
        </Alert>
      )}
      {deployMutation.isError && (
        <Alert severity="error" sx={{ mx: 2, mt: 1 }}>
          Deploy failed: {(deployMutation.error as Error)?.message || 'Unknown error'}
        </Alert>
      )}
      {saveLibMutation.isError && (
        <Alert severity="error" sx={{ mx: 2, mt: 1 }}>
          Save as library failed: {(saveLibMutation.error as Error)?.message || 'Unknown error'}
        </Alert>
      )}

      {/* Deploy Dialog */}
      <Dialog open={deployDialog} onClose={() => setDeployDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Deploy as CDS Service</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              This will generate CQL from your artifact and register it as a CDS Hooks service.
            </Typography>
            <TextField
              label="Hook Type"
              value={deployHook}
              onChange={(e) => setDeployHook(e.target.value)}
              size="small"
              select
              SelectProps={{ native: true }}
            >
              <option value="patient-view">patient-view</option>
              <option value="order-select">order-select</option>
              <option value="order-sign">order-sign</option>
              <option value="encounter-start">encounter-start</option>
              <option value="encounter-discharge">encounter-discharge</option>
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeployDialog(false)}>Cancel</Button>
          <Button onClick={handleDeploy} variant="contained" disabled={deployMutation.isPending}>
            {deployMutation.isPending ? 'Deploying...' : 'Deploy'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
