import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import { AUTOSAVE_SLOW_MS, NOTIFICATION_DURATION_MS } from '../../constants/timing'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Chip, CircularProgress, Dialog, DialogTitle, DialogContent,
  DialogContentText, DialogActions, IconButton, Snackbar, Alert,
  Stack, Tab, Tabs, Tooltip, Typography,
  RadioGroup, Radio, FormControlLabel,
} from '@mui/material'
import {
  ArrowBack as BackIcon,
  Save as SaveIcon,
  CheckCircle as CheckIcon,
  ErrorOutline as ErrorIcon,
  NewReleases as VersionIcon,
  Download as ExportIcon,
} from '@mui/icons-material'
import type { CqlLibrary } from '../../types'
import { useUpdateCqlLibrary, useCreateCqlLibraryVersion, useExportCqlLibrary } from '../../hooks/useCqlLibraries'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import StatusChip from '../common/StatusChip'
import CqlLibraryEditorTab from './CqlLibraryEditorTab'
import CqlLibraryMetadataTab from './CqlLibraryMetadataTab'
import CqlLibraryDependencyTab from './CqlLibraryDependencyTab'
import CqlLibraryHistoryTab from './CqlLibraryHistoryTab'
import CqlLibrarySharingTab from './CqlLibrarySharingTab'
import { extractApiError } from '../../utils/errorUtils'

type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

interface CqlLibraryWorkspaceProps {
  library: CqlLibrary
  onBack: () => void
}

export default function CqlLibraryWorkspace({ library, onBack }: CqlLibraryWorkspaceProps) {
  const { t } = useTranslation('cqlLibraries')
  const [tab, setTab] = useState(0)
  const [snack, setSnack] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [showBackConfirm, setShowBackConfirm] = useState(false)
  const [showVersionDialog, setShowVersionDialog] = useState(false)
  const [versionType, setVersionType] = useState<'major' | 'minor'>('minor')

  const versionMutation = useCreateCqlLibraryVersion()
  const exportMutation = useExportCqlLibrary()

  // Local optimistic state
  const [localOverrides, setLocalOverrides] = useState<Partial<CqlLibrary>>({})
  const merged = useMemo<CqlLibrary>(
    () => ({ ...library, ...localOverrides }) as CqlLibrary,
    [library, localOverrides]
  )

  const updateMutation = useUpdateCqlLibrary()

  const isDirty = saveStatus === 'dirty' || saveStatus === 'saving'
  useUnsavedChangesGuard(isDirty)

  const readOnly = library.status !== 'draft' && library.status !== 'active'

  // Clear local overrides when server library changes (refetch completed)
  const lastUpdatedRef = useRef(library.updatedAt)
  useEffect(() => {
    if (library.updatedAt !== lastUpdatedRef.current) {
      lastUpdatedRef.current = library.updatedAt
      setLocalOverrides({})
    }
  }, [library.updatedAt])

  // Use refs to avoid unstable callback dependencies
  const libraryRef = useRef(library)
  libraryRef.current = library

  // Auto-save timer
  const pendingRef = useRef<Partial<CqlLibrary> | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  const save = useCallback((updates: Partial<CqlLibrary>) => {
    const lib = libraryRef.current
    setSaveStatus('saving')
    updateMutation.mutate(
      {
        id: lib.id,
        cql: updates.cqlContent ?? lib.cqlContent,
        description: updates.description ?? lib.description,
      },
      {
        onSuccess: () => {
          setSaveStatus('saved')
        },
        onError: (error) => {
          setSaveStatus('error')
          const msg = extractApiError(error)
          setSnack({ message: msg || t('workspace.saveFailed'), severity: 'error' })
        },
      }
    )
  }, [updateMutation, t])

  const debouncedSave = useCallback((updates: Partial<CqlLibrary>) => {
    setLocalOverrides(prev => ({ ...prev, ...updates }))
    setSaveStatus('dirty')
    pendingRef.current = { ...(pendingRef.current || {}), ...updates }
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      if (pendingRef.current) {
        save(pendingRef.current)
        pendingRef.current = null
      }
    }, AUTOSAVE_SLOW_MS)
  }, [save])

  // Immediate save (flush pending + save now)
  const flushSave = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
    if (pendingRef.current) {
      save(pendingRef.current)
      pendingRef.current = null
    } else {
      save({})
    }
  }, [save])

  // Flush on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
      if (pendingRef.current) {
        save(pendingRef.current)
        pendingRef.current = null
      }
    }
  }, [save])

  // Ctrl+S keyboard shortcut
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        flushSave()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [flushSave])

  const handleBack = useCallback(() => {
    if (saveStatus === 'dirty' || saveStatus === 'saving') {
      setShowBackConfirm(true)
    } else {
      onBack()
    }
  }, [saveStatus, onBack])

  const handleSaveAndLeave = useCallback(() => {
    setShowBackConfirm(false)
    if (timerRef.current) clearTimeout(timerRef.current)
    if (pendingRef.current) {
      const lib = libraryRef.current
      const payload = pendingRef.current
      pendingRef.current = null
      updateMutation.mutate(
        {
          id: lib.id,
          cql: payload.cqlContent ?? lib.cqlContent,
          description: payload.description ?? lib.description,
        },
        { onSuccess: () => onBack(), onError: () => onBack() }
      )
    } else {
      onBack()
    }
  }, [updateMutation, onBack])

  const handleCreateVersion = useCallback(() => {
    versionMutation.mutate(
      { name: library.name, type: versionType },
      {
        onSuccess: () => {
          setSnack({ message: t('version.versionCreated'), severity: 'success' })
          setShowVersionDialog(false)
        },
        onError: (error) => {
          const msg = extractApiError(error)
          setSnack({ message: msg || t('errors.versionFailed', { error: 'Unknown' }), severity: 'error' })
          setShowVersionDialog(false)
        },
      },
    )
  }, [versionMutation, library.name, versionType, t])

  const handleExport = useCallback(() => {
    exportMutation.mutate(library.id, {
      onSuccess: (data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${library.name}-v${library.version}.json`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        setSnack({ message: t('export.exportSuccess'), severity: 'success' })
      },
      onError: (error) => {
        const msg = extractApiError(error)
        setSnack({ message: msg || t('export.exportFailed'), severity: 'error' })
      },
    })
  }, [exportMutation, library.id, library.name, library.version, t])

  const handleCqlChange = useCallback((content: string) => {
    debouncedSave({ cqlContent: content })
  }, [debouncedSave])

  const handleMetadataChange = useCallback((updates: Partial<CqlLibrary>) => {
    debouncedSave(updates)
  }, [debouncedSave])

  // Save indicator
  const saveIndicator = useMemo(() => {
    switch (saveStatus) {
      case 'saving':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'text.secondary' }}>
            <CircularProgress size={14} color="inherit" />
            <Typography variant="caption">{t('workspace.saving')}</Typography>
          </Stack>
        )
      case 'saved':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'success.main' }}>
            <CheckIcon fontSize="small" />
            <Typography variant="caption">{t('workspace.saved')}</Typography>
          </Stack>
        )
      case 'dirty':
        return (
          <Typography variant="caption" sx={{ color: 'warning.main' }}>
            {t('workspace.unsavedChanges')}
          </Typography>
        )
      case 'error':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ color: 'error.main' }}>
            <ErrorIcon fontSize="small" />
            <Typography variant="caption">{t('workspace.saveFailed')}</Typography>
          </Stack>
        )
      default:
        return null
    }
  }, [saveStatus, t])

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Header */}
      <Box sx={{ px: 3, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Stack direction="row" alignItems="center" spacing={2}>
          <Tooltip title={t('header.backToList')}>
            <IconButton onClick={handleBack}><BackIcon /></IconButton>
          </Tooltip>
          <Box sx={{ flex: 1 }}>
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <Typography variant="h6" fontWeight={600}>{merged.name}</Typography>
              {saveIndicator}
              {readOnly && (
                <Chip label={t('workspace.readOnly')} size="small" color="warning" variant="outlined" />
              )}
            </Stack>
            <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
              <Chip label={`v${merged.version}`} size="small" variant="outlined" />
              <StatusChip status={merged.status} />
              {merged.accessLevel && (
                <Chip label={merged.accessLevel} size="small" variant="outlined" />
              )}
            </Stack>
          </Box>
          {library.status === 'draft' && (
            <Button
              variant="outlined"
              size="small"
              startIcon={versionMutation.isPending ? <CircularProgress size={14} /> : <VersionIcon />}
              onClick={() => setShowVersionDialog(true)}
              disabled={versionMutation.isPending}
            >
              {t('header.createVersion')}
            </Button>
          )}
          <Button
            variant="outlined"
            size="small"
            startIcon={exportMutation.isPending ? <CircularProgress size={14} /> : <ExportIcon />}
            onClick={handleExport}
            disabled={exportMutation.isPending}
          >
            {t('header.export')}
          </Button>
          <Tooltip title="Ctrl+S">
            <span>
              <Button
                variant="outlined"
                startIcon={saveStatus === 'saving' ? <CircularProgress size={14} /> : <SaveIcon />}
                onClick={flushSave}
                disabled={saveStatus === 'saving' || readOnly}
              >
                {t('header.save')}
              </Button>
            </span>
          </Tooltip>
        </Stack>
      </Box>

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
          <Tab label={t('workspace.tabs.cqlEditor')} />
          <Tab label={t('workspace.tabs.metadata')} />
          <Tab label={t('workspace.tabs.dependencies')} />
          <Tab label={t('workspace.tabs.history')} />
          <Tab label={t('workspace.tabs.sharing')} />
        </Tabs>
      </Box>

      {/* Tab content */}
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tab === 0 && (
          <CqlLibraryEditorTab
            cqlContent={merged.cqlContent}
            onChange={handleCqlChange}
            readOnly={readOnly}
          />
        )}
        {tab === 1 && (
          <Box sx={{ p: 3 }}>
            <CqlLibraryMetadataTab
              library={merged}
              onChange={handleMetadataChange}
              readOnly={readOnly}
            />
          </Box>
        )}
        {tab === 2 && (
          <Box sx={{ p: 3 }}>
            <CqlLibraryDependencyTab
              libraryId={library.id}
              libraryName={library.name}
            />
          </Box>
        )}
        {tab === 3 && (
          <Box sx={{ p: 3 }}>
            <CqlLibraryHistoryTab
              libraryName={library.name}
              currentVersion={merged.version}
            />
          </Box>
        )}
        {tab === 4 && (
          <Box sx={{ p: 3 }}>
            <CqlLibrarySharingTab library={merged} />
          </Box>
        )}
      </Box>

      {/* Unsaved changes confirmation dialog */}
      <Dialog open={showBackConfirm} onClose={() => setShowBackConfirm(false)}>
        <DialogTitle>{t('workspace.unsavedTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('workspace.unsavedMessage')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowBackConfirm(false)}>{t('common:actions.cancel', 'Cancel')}</Button>
          <Button color="error" onClick={() => { setShowBackConfirm(false); onBack() }}>
            {t('workspace.discardChanges')}
          </Button>
          <Button variant="contained" onClick={handleSaveAndLeave}>
            {t('workspace.saveAndLeave')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Create Version dialog */}
      <Dialog open={showVersionDialog} onClose={() => setShowVersionDialog(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('version.createConfirmTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            {t('version.createConfirmMessage')}
          </DialogContentText>
          <RadioGroup
            value={versionType}
            onChange={(e) => setVersionType(e.target.value as 'major' | 'minor')}
          >
            <FormControlLabel
              value="major"
              control={<Radio />}
              label={t('header.versionMajor')}
            />
            <FormControlLabel
              value="minor"
              control={<Radio />}
              label={t('header.versionMinor')}
            />
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowVersionDialog(false)} disabled={versionMutation.isPending}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleCreateVersion}
            disabled={versionMutation.isPending}
          >
            {versionMutation.isPending ? t('version.creatingVersion') : t('header.createVersion')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snack} autoHideDuration={NOTIFICATION_DURATION_MS}
        onClose={() => setSnack(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setSnack(null)} severity={snack?.severity} variant="filled">
          {snack?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
