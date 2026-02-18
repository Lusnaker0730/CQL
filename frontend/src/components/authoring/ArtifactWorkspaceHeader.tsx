import { useState } from 'react'
import {
  Box, Typography, Stack, IconButton, Tooltip, TextField, Menu, MenuItem, ListItemIcon, ListItemText,
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Alert, CircularProgress,
} from '@mui/material'
import {
  ArrowBack as BackIcon, Save as SaveIcon, MoreVert as MoreIcon,
  CloudUpload as DeployIcon, LibraryBooks as LibraryIcon, Download as DownloadIcon,
  Visibility as ViewIcon, Close as CloseIcon, Description as CpgIcon,
} from '@mui/icons-material'
import { useDeployCdsService, useSaveAsLibrary } from '../../hooks/useArtifactTesting'
import { useGenerateArtifactCql } from '../../hooks/useArtifactCql'
import CpgMetadataEditor from './CpgMetadataEditor'
import type { Artifact, ArtifactRequest } from '../../types/authoring'
import { CDS_HOOK_TYPES, getHookDescription } from '../../constants/cdsHooks'
import { FHIR_VERSION_OPTIONS } from '../../constants/authoringConstants'

interface ArtifactWorkspaceHeaderProps {
  artifact: Artifact
  isDirty: boolean
  onBack: () => void
  onSave: (request: ArtifactRequest) => void
  onSaveBeforeGenerate?: () => Promise<void>
  onNameChange: (name: string) => void
  onUpdate: (updates: Partial<Artifact>) => void
}

export default function ArtifactWorkspaceHeader({
  artifact,
  isDirty,
  onBack,
  onSave,
  onSaveBeforeGenerate,
  onNameChange,
  onUpdate,
}: ArtifactWorkspaceHeaderProps) {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const [deployDialog, setDeployDialog] = useState(false)
  const [deployHook, setDeployHook] = useState('patient-view')
  const [deployResult, setDeployResult] = useState<string | null>(null)
  const [saveLibResult, setSaveLibResult] = useState<string | null>(null)
  const [viewCqlDialog, setViewCqlDialog] = useState(false)
  const [viewCqlContent, setViewCqlContent] = useState<string | null>(null)
  const [cpgDialogOpen, setCpgDialogOpen] = useState(false)
  const [selectedFhirVersion, setSelectedFhirVersion] = useState(artifact.fhirVersion || 'R4')

  const [saving, setSaving] = useState(false)

  const deployMutation = useDeployCdsService()
  const saveLibMutation = useSaveAsLibrary()
  const generateCqlMutation = useGenerateArtifactCql()

  const saveFirst = async () => {
    if (isDirty && onSaveBeforeGenerate) {
      setSaving(true)
      try {
        await onSaveBeforeGenerate()
      } finally {
        setSaving(false)
      }
    }
  }

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

  const handleDeploy = async () => {
    await saveFirst()
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

  const handleSaveAsLibrary = async () => {
    setAnchorEl(null)
    await saveFirst()
    saveLibMutation.mutate(artifact.id, {
      onSuccess: (data) => setSaveLibResult(data.message),
    })
  }

  const handleViewCql = async (fhirVer?: string) => {
    await saveFirst()
    generateCqlMutation.mutate({ id: artifact.id, fhirVersion: fhirVer || selectedFhirVersion }, {
      onSuccess: (data) => {
        setViewCqlContent(data.cql)
        setViewCqlDialog(true)
      },
    })
  }

  const handleDownloadCql = async (fhirVer?: string) => {
    await saveFirst()
    generateCqlMutation.mutate({ id: artifact.id, fhirVersion: fhirVer || selectedFhirVersion }, {
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
          backgroundColor: (theme) => theme.palette.mode === 'dark' ? '#1a2332' : '#1B3A5C',
          color: '#fff',
        }}
      >
        <Tooltip title="Back to artifact list">
          <IconButton onClick={onBack} size="small" sx={{ color: '#fff' }} aria-label="Back to artifact list">
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
              color: '#fff',
            },
            '& .MuiInput-underline:before': { borderBottomColor: 'rgba(255,255,255,0.3)' },
            '& .MuiInput-underline:hover:before': { borderBottomColor: 'rgba(255,255,255,0.6)' },
            '& .MuiInput-underline:after': { borderBottomColor: '#fff' },
            minWidth: 200,
            maxWidth: 400,
          }}
        />

        {isDirty && (
          <Typography variant="caption" sx={{ fontStyle: 'italic', color: 'rgba(255,255,255,0.7)' }}>
            Unsaved changes
          </Typography>
        )}

        <Stack direction="row" spacing={1} sx={{ ml: 'auto' }} alignItems="center">
          {/* VIEW CQL - prominent button */}
          <Button
            variant="outlined"
            size="small"
            startIcon={(generateCqlMutation.isPending || saving) ? <CircularProgress size={14} color="inherit" /> : <ViewIcon />}
            onClick={() => handleViewCql()}
            disabled={generateCqlMutation.isPending || saving}
            sx={{
              color: '#fff',
              borderColor: 'rgba(255,255,255,0.5)',
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { borderColor: '#fff', backgroundColor: 'rgba(255,255,255,0.1)' },
            }}
          >
            View CQL
          </Button>

          {/* DOWNLOAD CQL - prominent button */}
          <Button
            variant="outlined"
            size="small"
            startIcon={<DownloadIcon />}
            onClick={() => handleDownloadCql()}
            disabled={generateCqlMutation.isPending || saving}
            sx={{
              color: '#fff',
              borderColor: 'rgba(255,255,255,0.5)',
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { borderColor: '#fff', backgroundColor: 'rgba(255,255,255,0.1)' },
            }}
          >
            Download CQL
          </Button>

          {/* SAVE - prominent button */}
          <Button
            variant="contained"
            size="small"
            startIcon={<SaveIcon />}
            onClick={handleSave}
            disabled={!isDirty}
            sx={{
              backgroundColor: 'rgba(255,255,255,0.15)',
              color: '#fff',
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { backgroundColor: 'rgba(255,255,255,0.25)' },
              '&.Mui-disabled': { color: 'rgba(255,255,255,0.3)' },
            }}
          >
            Save
          </Button>

          {/* More actions menu */}
          <Tooltip title="More actions">
            <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)} sx={{ color: '#fff' }} aria-label="More actions">
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
            <MenuItem onClick={() => { setAnchorEl(null); setCpgDialogOpen(true) }}>
              <ListItemIcon><CpgIcon fontSize="small" /></ListItemIcon>
              <ListItemText>CPG Metadata</ListItemText>
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
        <Alert severity="error" onClose={() => deployMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">Deploy Failed</Typography>
          <Typography variant="body2">
            {(deployMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Ensure your artifact has valid inclusion criteria and at least one recommendation before deploying.
          </Typography>
        </Alert>
      )}
      {saveLibMutation.isError && (
        <Alert severity="error" onClose={() => saveLibMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">Save as Library Failed</Typography>
          <Typography variant="body2">
            {(saveLibMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Verify that the artifact generates valid CQL before saving as a library.
          </Typography>
        </Alert>
      )}
      {generateCqlMutation.isError && (
        <Alert severity="error" onClose={() => generateCqlMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">CQL Generation Failed</Typography>
          <Typography variant="body2">
            {(generateCqlMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Check that all elements have required fields filled in and modifier chains are compatible.
          </Typography>
        </Alert>
      )}

      {/* View CQL Dialog */}
      <Dialog open={viewCqlDialog} onClose={() => setViewCqlDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          Generated CQL
          <IconButton onClick={() => setViewCqlDialog(false)} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary">FHIR Version:</Typography>
            <TextField
              select
              size="small"
              value={selectedFhirVersion}
              onChange={(e) => setSelectedFhirVersion(e.target.value)}
              sx={{ minWidth: 120 }}
            >
              {FHIR_VERSION_OPTIONS.map((v) => (
                <MenuItem key={v.key} value={v.key}>{v.label}</MenuItem>
              ))}
            </TextField>
            <Button
              size="small"
              variant="outlined"
              onClick={() => handleViewCql(selectedFhirVersion)}
              disabled={generateCqlMutation.isPending}
            >
              {generateCqlMutation.isPending ? 'Regenerating...' : 'Regenerate'}
            </Button>
          </Stack>
          {viewCqlContent ? (
            <Box
              sx={{
                p: 2,
                borderRadius: 1,
                backgroundColor: '#1e1e1e',
                color: '#d4d4d4',
                fontFamily: '"Fira Code", "Consolas", monospace',
                fontSize: '0.85rem',
                whiteSpace: 'pre-wrap',
                lineHeight: 1.6,
                maxHeight: '60vh',
                overflow: 'auto',
              }}
            >
              {viewCqlContent}
            </Box>
          ) : (
            <Typography color="text.secondary">No CQL generated.</Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewCqlDialog(false)}>Close</Button>
          <Button
            variant="contained"
            startIcon={<DownloadIcon />}
            onClick={() => {
              if (viewCqlContent) {
                const blob = new Blob([viewCqlContent], { type: 'text/plain' })
                const url = URL.createObjectURL(blob)
                const a = document.createElement('a')
                a.href = url
                a.download = `${artifact.name.replace(/[^a-zA-Z0-9_-]/g, '_')}.cql`
                a.click()
                URL.revokeObjectURL(url)
              }
            }}
          >
            Download
          </Button>
        </DialogActions>
      </Dialog>

      {/* CPG Metadata Dialog */}
      <CpgMetadataEditor
        open={cpgDialogOpen}
        onClose={() => setCpgDialogOpen(false)}
        artifact={artifact}
        onUpdate={onUpdate}
      />

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
              helperText={getHookDescription(deployHook)}
            >
              {CDS_HOOK_TYPES.map((hook) => (
                <option key={hook.id} value={hook.id}>{hook.id}</option>
              ))}
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
