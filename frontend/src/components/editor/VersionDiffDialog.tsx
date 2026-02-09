import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Typography,
  CircularProgress,
  Alert,
  Tabs,
  Tab,
  Chip,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material'
import {
  ChangeCircle as ChangeIcon,
} from '@mui/icons-material'
import ReactDiffViewer, { DiffMethod } from 'react-diff-viewer-continued'
import GradientButton from '../common/GradientButton'

interface VersionOption {
  id: string | number
  version: string
}

interface DiffResult {
  oldCql: string
  newCql: string
  oldVersion: string
  newVersion: string
  metadataChanges?: string[]
  populationChanges?: string[]
}

interface VersionDiffDialogProps {
  open: boolean
  onClose: () => void
  versions: VersionOption[]
  onCompare: (
    oldId: string | number,
    newId: string | number,
  ) => Promise<DiffResult>
}

export default function VersionDiffDialog({
  open,
  onClose,
  versions,
  onCompare,
}: VersionDiffDialogProps) {
  const [oldVersionId, setOldVersionId] = useState<string | number>('')
  const [newVersionId, setNewVersionId] = useState<string | number>('')
  const [diffResult, setDiffResult] = useState<DiffResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [diffTab, setDiffTab] = useState(0)

  const handleCompare = async () => {
    if (!oldVersionId || !newVersionId) return
    setLoading(true)
    setError(null)
    setDiffResult(null)
    try {
      const result = await onCompare(oldVersionId, newVersionId)
      setDiffResult(result)
      setDiffTab(0)
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to load version diff',
      )
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setOldVersionId('')
    setNewVersionId('')
    setDiffResult(null)
    setError(null)
    setDiffTab(0)
    onClose()
  }

  const canCompare =
    oldVersionId !== '' &&
    newVersionId !== '' &&
    oldVersionId !== newVersionId &&
    !loading

  const totalChanges =
    (diffResult?.metadataChanges?.length || 0) +
    (diffResult?.populationChanges?.length || 0)

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xl" fullWidth>
      <DialogTitle>Compare Versions</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2 }}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>Old Version</InputLabel>
            <Select
              value={oldVersionId}
              label="Old Version"
              onChange={(e) => {
                setOldVersionId(e.target.value)
                setDiffResult(null)
              }}
            >
              {versions.map((v) => (
                <MenuItem key={v.id} value={v.id}>
                  {v.version}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <Typography variant="body2" color="text.secondary">
            vs
          </Typography>

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>New Version</InputLabel>
            <Select
              value={newVersionId}
              label="New Version"
              onChange={(e) => {
                setNewVersionId(e.target.value)
                setDiffResult(null)
              }}
            >
              {versions.map((v) => (
                <MenuItem key={v.id} value={v.id}>
                  {v.version}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <GradientButton
            onClick={handleCompare}
            disabled={!canCompare}
            startIcon={
              loading ? (
                <CircularProgress size={16} color="inherit" />
              ) : undefined
            }
          >
            {loading ? 'Comparing...' : 'Compare'}
          </GradientButton>

          {diffResult && totalChanges > 0 && (
            <Chip label={`${totalChanges} changes`} size="small" color="info" />
          )}
        </Box>

        {oldVersionId !== '' &&
          newVersionId !== '' &&
          oldVersionId === newVersionId && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Please select two different versions to compare.
            </Alert>
          )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {diffResult && (
          <>
            <Tabs
              value={diffTab}
              onChange={(_, v) => setDiffTab(v)}
              sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none' } }}
            >
              <Tab label="CQL Diff" />
              <Tab
                label={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    Metadata Diff
                    {(diffResult.metadataChanges?.length || 0) > 0 && (
                      <Chip label={diffResult.metadataChanges!.length} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
                    )}
                  </Box>
                }
              />
              <Tab
                label={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    Population Diff
                    {(diffResult.populationChanges?.length || 0) > 0 && (
                      <Chip label={diffResult.populationChanges!.length} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
                    )}
                  </Box>
                }
              />
            </Tabs>

            {diffTab === 0 && (
              <Box
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1,
                  overflow: 'auto',
                  maxHeight: '55vh',
                  '& pre': { fontSize: '13px !important' },
                }}
              >
                <ReactDiffViewer
                  oldValue={diffResult.oldCql}
                  newValue={diffResult.newCql}
                  splitView
                  leftTitle={`v${diffResult.oldVersion}`}
                  rightTitle={`v${diffResult.newVersion}`}
                  compareMethod={DiffMethod.WORDS}
                  useDarkTheme={false}
                />
              </Box>
            )}

            {diffTab === 1 && (
              <Box sx={{ maxHeight: '55vh', overflow: 'auto' }}>
                {(!diffResult.metadataChanges || diffResult.metadataChanges.length === 0) ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                    No metadata changes detected between versions.
                  </Typography>
                ) : (
                  <List dense>
                    {diffResult.metadataChanges.map((change, i) => (
                      <ListItem key={i}>
                        <ListItemIcon sx={{ minWidth: 32 }}>
                          <ChangeIcon fontSize="small" color="info" />
                        </ListItemIcon>
                        <ListItemText primary={change} primaryTypographyProps={{ variant: 'body2' }} />
                      </ListItem>
                    ))}
                  </List>
                )}
              </Box>
            )}

            {diffTab === 2 && (
              <Box sx={{ maxHeight: '55vh', overflow: 'auto' }}>
                {(!diffResult.populationChanges || diffResult.populationChanges.length === 0) ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                    No population changes detected between versions.
                  </Typography>
                ) : (
                  <List dense>
                    {diffResult.populationChanges.map((change, i) => (
                      <ListItem key={i}>
                        <ListItemIcon sx={{ minWidth: 32 }}>
                          <ChangeIcon fontSize="small" color="warning" />
                        </ListItemIcon>
                        <ListItemText primary={change} primaryTypographyProps={{ variant: 'body2' }} />
                      </ListItem>
                    ))}
                  </List>
                )}
              </Box>
            )}
          </>
        )}

        {!diffResult && !loading && !error && (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              py: 8,
              color: 'text.secondary',
            }}
          >
            <Typography variant="body2">
              Select two versions and click Compare to view differences.
            </Typography>
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} size="small">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  )
}
