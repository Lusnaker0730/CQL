import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Typography, Stack, IconButton, Tooltip, TextField, Menu, MenuItem, ListItemIcon, ListItemText,
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Alert, CircularProgress,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  ArrowBack as BackIcon, Save as SaveIcon, MoreVert as MoreIcon,
  CloudUpload as DeployIcon, LibraryBooks as LibraryIcon, Download as DownloadIcon,
  Visibility as ViewIcon, Close as CloseIcon, Description as CpgIcon,
  FolderZip as ZipIcon,
} from '@mui/icons-material'
import { useDeployCdsService, useSaveAsLibrary } from '../../hooks/useArtifactTesting'
import { useGenerateArtifactCql, useExportArtifactZip } from '../../hooks/useArtifactCql'
import { extractApiError, extractApiErrorDetails } from '../../utils/errorUtils'
import CpgMetadataEditor from './CpgMetadataEditor'
import type { Artifact, ArtifactRequest } from '../../types/authoring'
import { CDS_HOOK_TYPES, getHookDescription } from '../../constants/cdsHooks'
import { codeBlockSx } from '../../constants/authoringConstants'
import { downloadBlob } from '../../utils/download'

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
  const { t } = useTranslation('authoring')
  const { t: tc } = useTranslation('common')
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const [deployDialog, setDeployDialog] = useState(false)
  const [deployHook, setDeployHook] = useState('patient-view')
  const [deployResult, setDeployResult] = useState<string | null>(null)
  const [saveLibResult, setSaveLibResult] = useState<string | null>(null)
  const [viewCqlDialog, setViewCqlDialog] = useState(false)
  const [viewCqlContent, setViewCqlContent] = useState<string | null>(null)
  const [cpgDialogOpen, setCpgDialogOpen] = useState(false)

  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveErrorDetails, setSaveErrorDetails] = useState<string[]>([])

  const deployMutation = useDeployCdsService()
  const saveLibMutation = useSaveAsLibrary()
  const generateCqlMutation = useGenerateArtifactCql()
  const exportZipMutation = useExportArtifactZip()

  const saveFirst = async () => {
    if (isDirty && onSaveBeforeGenerate) {
      setSaving(true)
      setSaveError(null)
      try {
        await onSaveBeforeGenerate()
      } catch (err) {
        setSaveError(extractApiError(err))
        setSaveErrorDetails(extractApiErrorDetails(err) || [])
        throw err
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
    try { await saveFirst() } catch { return }
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
    try { await saveFirst() } catch { return }
    saveLibMutation.mutate(artifact.id, {
      onSuccess: (data) => setSaveLibResult(data.message),
    })
  }

  const handleViewCql = async () => {
    try {
      await saveFirst()
    } catch { return }
    generateCqlMutation.mutate(artifact.id, {
      onSuccess: (data) => {
        setViewCqlContent(data.cql)
        setViewCqlDialog(true)
      },
    })
  }

  const safeName = artifact.name.replace(/[^a-zA-Z0-9_-]/g, '_')

  const handleExportZip = async () => {
    setAnchorEl(null)
    try { await saveFirst() } catch { return }
    exportZipMutation.mutate(artifact.id, {
      onSuccess: (blob) => downloadBlob(blob, `${safeName}.zip`),
    })
  }

  const handleDownloadCql = async () => {
    try { await saveFirst() } catch { return }
    generateCqlMutation.mutate(artifact.id, {
      onSuccess: (data) => {
        downloadBlob(new Blob([data.cql], { type: 'text/plain' }), `${safeName}.cql`)
      },
    })
  }

  return (
    <>
      <Box
        sx={(theme) => ({
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          p: 2,
          borderBottom: 1,
          borderColor: 'divider',
          backgroundColor: theme.palette.mode === 'dark' ? theme.palette.secondary.dark : theme.palette.secondary.main,
          color: theme.palette.common.white,
        })}
      >
        <Tooltip title={t('header.backToList')}>
          <IconButton onClick={onBack} size="small" sx={{ color: 'common.white' }} aria-label={t('header.backToList')}>
            <BackIcon />
          </IconButton>
        </Tooltip>

        <TextField
          value={artifact.name}
          onChange={(e) => onNameChange(e.target.value)}
          variant="standard"
          sx={(theme) => ({
            '& .MuiInput-input': {
              fontSize: '1.1rem',
              fontWeight: 600,
              color: theme.palette.common.white,
            },
            '& .MuiInput-underline:before': { borderBottomColor: alpha(theme.palette.common.white, 0.3) },
            '& .MuiInput-underline:hover:before': { borderBottomColor: alpha(theme.palette.common.white, 0.6) },
            '& .MuiInput-underline:after': { borderBottomColor: theme.palette.common.white },
            minWidth: 200,
            maxWidth: 400,
          })}
        />

        {isDirty && (
          <Typography variant="caption" sx={(theme) => ({ fontStyle: 'italic', color: alpha(theme.palette.common.white, 0.7) })}>
            {t('header.unsavedChanges')}
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
            sx={(theme) => ({
              color: theme.palette.common.white,
              borderColor: alpha(theme.palette.common.white, 0.5),
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { borderColor: theme.palette.common.white, backgroundColor: alpha(theme.palette.common.white, 0.1) },
            })}
          >
            {t('header.viewCql')}
          </Button>

          {/* DOWNLOAD CQL - prominent button */}
          <Button
            variant="outlined"
            size="small"
            startIcon={<DownloadIcon />}
            onClick={() => handleDownloadCql()}
            disabled={generateCqlMutation.isPending || saving}
            sx={(theme) => ({
              color: theme.palette.common.white,
              borderColor: alpha(theme.palette.common.white, 0.5),
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { borderColor: theme.palette.common.white, backgroundColor: alpha(theme.palette.common.white, 0.1) },
            })}
          >
            {t('header.downloadCql')}
          </Button>

          {/* SAVE - prominent button */}
          <Button
            variant="contained"
            size="small"
            startIcon={<SaveIcon />}
            onClick={handleSave}
            disabled={!isDirty}
            sx={(theme) => ({
              backgroundColor: alpha(theme.palette.common.white, 0.15),
              color: theme.palette.common.white,
              textTransform: 'uppercase',
              fontWeight: 600,
              fontSize: '0.8rem',
              '&:hover': { backgroundColor: alpha(theme.palette.common.white, 0.25) },
              '&.Mui-disabled': { color: alpha(theme.palette.common.white, 0.3) },
            })}
          >
            {tc('actions.save')}
          </Button>

          {/* More actions menu */}
          <Tooltip title={t('header.moreActions')}>
            <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)} sx={{ color: 'common.white' }} aria-label={t('header.moreActions')}>
              <MoreIcon />
            </IconButton>
          </Tooltip>
          <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={() => setAnchorEl(null)}>
            <MenuItem onClick={() => { setAnchorEl(null); setDeployDialog(true) }}>
              <ListItemIcon><DeployIcon fontSize="small" /></ListItemIcon>
              <ListItemText>{t('header.deployCdsService')}</ListItemText>
            </MenuItem>
            <MenuItem onClick={handleSaveAsLibrary} disabled={saveLibMutation.isPending}>
              <ListItemIcon><LibraryIcon fontSize="small" /></ListItemIcon>
              <ListItemText>{t('header.saveAsLibrary')}</ListItemText>
            </MenuItem>
            <MenuItem onClick={() => { setAnchorEl(null); setCpgDialogOpen(true) }}>
              <ListItemIcon><CpgIcon fontSize="small" /></ListItemIcon>
              <ListItemText>{t('header.cpgMetadata')}</ListItemText>
            </MenuItem>
            <MenuItem onClick={handleExportZip} disabled={exportZipMutation.isPending}>
              <ListItemIcon><ZipIcon fontSize="small" /></ListItemIcon>
              <ListItemText>{exportZipMutation.isPending ? t('header.exporting') : t('header.exportZip')}</ListItemText>
            </MenuItem>
          </Menu>
        </Stack>
      </Box>

      {saveError && (
        <Alert severity="error" onClose={() => { setSaveError(null); setSaveErrorDetails([]) }} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">{t('header.saveFailed')}</Typography>
          <Typography variant="body2">{saveError}</Typography>
          {saveErrorDetails.map((detail, i) => (
            <Typography key={i} variant="caption" display="block" sx={{ mt: 0.5, fontFamily: 'monospace' }}>
              • {detail}
            </Typography>
          ))}
        </Alert>
      )}
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
          <Typography variant="subtitle2">{t('header.deployFailed')}</Typography>
          <Typography variant="body2">
            {(deployMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('header.deployHint')}
          </Typography>
        </Alert>
      )}
      {saveLibMutation.isError && (
        <Alert severity="error" onClose={() => saveLibMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">{t('header.saveLibFailed')}</Typography>
          <Typography variant="body2">
            {(saveLibMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('header.saveLibHint')}
          </Typography>
        </Alert>
      )}
      {exportZipMutation.isError && (
        <Alert severity="error" onClose={() => exportZipMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">{t('header.exportZipFailed')}</Typography>
          <Typography variant="body2">
            {(exportZipMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
        </Alert>
      )}
      {generateCqlMutation.isError && (
        <Alert severity="error" onClose={() => generateCqlMutation.reset()} sx={{ mx: 2, mt: 1 }}>
          <Typography variant="subtitle2">{t('header.cqlGenFailed')}</Typography>
          <Typography variant="body2">
            {(generateCqlMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('header.cqlGenHint')}
          </Typography>
        </Alert>
      )}

      {/* View CQL Dialog */}
      <Dialog open={viewCqlDialog} onClose={() => setViewCqlDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {t('header.generatedCql')}
          <IconButton onClick={() => setViewCqlDialog(false)} size="small" aria-label={tc('actions.close')}><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('header.fhirVersion')}: R4 (4.0.1)
          </Typography>
          {viewCqlContent ? (
            <Box
              sx={{
                ...codeBlockSx,
                p: 2,
                color: 'text.primary',
                maxHeight: '60vh',
                overflow: 'auto',
              }}
            >
              {viewCqlContent}
            </Box>
          ) : (
            <Typography color="text.secondary">{t('header.noCqlGenerated')}</Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewCqlDialog(false)}>{tc('actions.close')}</Button>
          <Button
            variant="contained"
            startIcon={<DownloadIcon />}
            onClick={() => {
              if (viewCqlContent) {
                downloadBlob(new Blob([viewCqlContent], { type: 'text/plain' }), `${safeName}.cql`)
              }
            }}
          >
            {t('header.download')}
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
        <DialogTitle>{t('header.deployTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              {t('header.deployDescription')}
            </Typography>
            <TextField
              label={t('header.hookTypeLabel')}
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
          <Button onClick={() => setDeployDialog(false)}>{tc('actions.cancel')}</Button>
          <Button onClick={handleDeploy} variant="contained" disabled={deployMutation.isPending}>
            {deployMutation.isPending ? t('header.deploying') : t('header.deploy')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
