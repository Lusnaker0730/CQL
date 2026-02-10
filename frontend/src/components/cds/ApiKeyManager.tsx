import { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  Alert,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Tooltip,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  ContentCopy as CopyIcon,
  VpnKey as KeyIcon,
} from '@mui/icons-material'
import { useApiKeys, useGenerateApiKey, useRevokeApiKey } from '../../hooks/useCdsHooks'
import { useNotification } from '../../hooks/useNotification'
import GradientButton from '../common/GradientButton'
import TableSkeleton from '../common/TableSkeleton'

export default function ApiKeyManager() {
  const { data: keys, isLoading, isError } = useApiKeys()
  const generateMutation = useGenerateApiKey()
  const revokeMutation = useRevokeApiKey()
  const { showNotification } = useNotification()

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [newlyCreatedKey, setNewlyCreatedKey] = useState<string | null>(null)

  const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
  const username = currentUser?.username || 'unknown'
  const baseUrl = window.location.origin
  const perUserEndpoint = `${baseUrl}/cds-services/u/${username}`

  const handleGenerate = async () => {
    if (!keyName.trim()) return
    try {
      const result = await generateMutation.mutateAsync(keyName.trim())
      setNewlyCreatedKey(result.key || null)
      setKeyName('')
      showNotification('API key generated successfully', 'success')
    } catch {
      showNotification('Failed to generate API key', 'error')
    }
  }

  const handleRevoke = async (id: number) => {
    if (!window.confirm('Are you sure you want to revoke this API key? This cannot be undone.')) return
    try {
      await revokeMutation.mutateAsync(id)
      showNotification('API key revoked', 'success')
    } catch {
      showNotification('Failed to revoke API key', 'error')
    }
  }

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
    showNotification('Copied to clipboard', 'info')
  }

  if (isLoading) return <TableSkeleton columns={4} rows={3} />
  if (isError) return <Alert severity="error">Failed to load API keys</Alert>

  return (
    <Stack spacing={3}>
      <Alert severity="info" icon={<KeyIcon />}>
        <Typography variant="subtitle2" gutterBottom>
          Your Per-User CDS Endpoint
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography
            variant="body2"
            sx={{
              fontFamily: '"Consolas", monospace',
              bgcolor: 'rgba(0,0,0,0.04)',
              px: 1,
              py: 0.5,
              borderRadius: 1,
            }}
          >
            {perUserEndpoint}
          </Typography>
          <Tooltip title="Copy endpoint URL">
            <IconButton size="small" onClick={() => handleCopy(perUserEndpoint)} aria-label="Copy endpoint URL">
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          Use this URL for EHR CDS Hooks integration. Authenticate with an API key: Authorization: Bearer {'<your-api-key>'}
        </Typography>
      </Alert>

      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1">API Keys</Typography>
        <GradientButton
          startIcon={<AddIcon />}
          onClick={() => {
            setCreateDialogOpen(true)
            setNewlyCreatedKey(null)
          }}
        >
          Generate New Key
        </GradientButton>
      </Box>

      {(!keys || keys.length === 0) && (
        <Alert severity="info">No API keys yet. Generate one to enable external CDS Hooks access.</Alert>
      )}

      {keys && keys.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell scope="col">Name</TableCell>
                <TableCell scope="col">Key</TableCell>
                <TableCell scope="col">Created</TableCell>
                <TableCell scope="col">Last Used</TableCell>
                <TableCell scope="col">Status</TableCell>
                <TableCell scope="col" align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {keys.map((key) => (
                <TableRow key={key.id}>
                  <TableCell>{key.name || 'Unnamed'}</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: '"Consolas", monospace', fontSize: '0.8rem' }}>
                      {key.keyPreview || '****'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {key.createdAt ? new Date(key.createdAt).toLocaleDateString() : '-'}
                  </TableCell>
                  <TableCell>
                    {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : 'Never'}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={key.active ? 'Active' : 'Revoked'}
                      color={key.active ? 'success' : 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="right">
                    {key.active && (
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleRevoke(key.id)}
                        disabled={revokeMutation.isPending}
                        aria-label="Revoke API key"
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Generate Key Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Generate New API Key</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {!newlyCreatedKey && (
              <TextField
                label="Key Name"
                value={keyName}
                onChange={(e) => setKeyName(e.target.value)}
                size="small"
                fullWidth
                placeholder="e.g., EHR Integration, Test Key"
                helperText="A friendly label for this API key"
              />
            )}

            {newlyCreatedKey && (
              <Alert severity="warning">
                <Typography variant="subtitle2" gutterBottom>
                  Save this key now — it won't be shown again!
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                  <Typography
                    variant="body2"
                    sx={{
                      fontFamily: '"Consolas", monospace',
                      bgcolor: 'rgba(0,0,0,0.06)',
                      px: 1.5,
                      py: 1,
                      borderRadius: 1,
                      wordBreak: 'break-all',
                      flex: 1,
                    }}
                  >
                    {newlyCreatedKey}
                  </Typography>
                  <Tooltip title="Copy key">
                    <IconButton onClick={() => handleCopy(newlyCreatedKey)} aria-label="Copy API key">
                      <CopyIcon />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCreateDialogOpen(false)} sx={{ color: 'text.secondary' }}>
            {newlyCreatedKey ? 'Done' : 'Cancel'}
          </Button>
          {!newlyCreatedKey && (
            <GradientButton
              onClick={handleGenerate}
              disabled={!keyName.trim() || generateMutation.isPending}
            >
              {generateMutation.isPending ? 'Generating...' : 'Generate'}
            </GradientButton>
          )}
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
