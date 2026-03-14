import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { getStoredUsername } from '../../utils/validation'
import { useNotification } from '../../hooks/useNotification'
import GradientButton from '../common/GradientButton'
import TableSkeleton from '../common/TableSkeleton'

export default function ApiKeyManager() {
  const { data: keys, isLoading, isError } = useApiKeys()
  const generateMutation = useGenerateApiKey()
  const revokeMutation = useRevokeApiKey()
  const { showNotification } = useNotification()
  const { t } = useTranslation('cds')
  const { t: tc } = useTranslation('common')

  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [newlyCreatedKey, setNewlyCreatedKey] = useState<string | null>(null)

  const username = getStoredUsername()
  const baseUrl = window.location.origin
  const perUserEndpoint = `${baseUrl}/cds-services/u/${username}`

  const handleGenerate = async () => {
    if (!keyName.trim()) return
    try {
      const result = await generateMutation.mutateAsync(keyName.trim())
      setNewlyCreatedKey(result.key || null)
      setKeyName('')
      showNotification(t('apiKeys.generateSuccess'), 'success')
    } catch {
      showNotification(t('apiKeys.generateFailed'), 'error')
    }
  }

  const handleRevoke = async (id: number) => {
    if (!window.confirm(t('apiKeys.revokeConfirm'))) return
    try {
      await revokeMutation.mutateAsync(id)
      showNotification(t('apiKeys.revokeSuccess'), 'success')
    } catch {
      showNotification(t('apiKeys.revokeFailed'), 'error')
    }
  }

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text)
    showNotification(t('apiKeys.copied'), 'info')
  }

  if (isLoading) return <TableSkeleton columns={4} rows={3} />
  if (isError) return <Alert severity="error">{t('apiKeys.loadError')}</Alert>

  return (
    <Stack spacing={3}>
      <Alert severity="info" icon={<KeyIcon />}>
        <Typography variant="subtitle2" gutterBottom>
          {t('apiKeys.endpointTitle')}
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
          <Tooltip title={t('apiKeys.copyEndpoint')}>
            <IconButton size="small" onClick={() => handleCopy(perUserEndpoint)} aria-label={t('apiKeys.copyEndpoint')}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          {t('apiKeys.endpointHelp')}
        </Typography>
      </Alert>

      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="subtitle1">{t('apiKeys.title')}</Typography>
        <GradientButton
          startIcon={<AddIcon />}
          onClick={() => {
            setCreateDialogOpen(true)
            setNewlyCreatedKey(null)
          }}
        >
          {t('apiKeys.generateNew')}
        </GradientButton>
      </Box>

      {(!keys || keys.length === 0) && (
        <Alert severity="info">{t('apiKeys.noKeys')}</Alert>
      )}

      {keys && keys.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell scope="col">{t('apiKeys.colName')}</TableCell>
                <TableCell scope="col">{t('apiKeys.colKey')}</TableCell>
                <TableCell scope="col">{t('apiKeys.colCreated')}</TableCell>
                <TableCell scope="col">{t('apiKeys.colLastUsed')}</TableCell>
                <TableCell scope="col">{t('apiKeys.colStatus')}</TableCell>
                <TableCell scope="col" align="right">{t('apiKeys.colActions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {keys.map((key) => (
                <TableRow key={key.id}>
                  <TableCell>{key.name || t('apiKeys.unnamed')}</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: '"Consolas", monospace', fontSize: '0.8rem' }}>
                      {key.keyPreview || '****'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {key.createdAt ? new Date(key.createdAt).toLocaleDateString() : '-'}
                  </TableCell>
                  <TableCell>
                    {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : t('apiKeys.never')}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={key.active ? t('apiKeys.active') : t('apiKeys.revoked')}
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
                        aria-label={t('apiKeys.revokeAriaLabel')}
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

      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('apiKeys.generateDialogTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {!newlyCreatedKey && (
              <TextField
                label={t('apiKeys.keyNameLabel')}
                value={keyName}
                onChange={(e) => setKeyName(e.target.value)}
                size="small"
                fullWidth
                placeholder={t('apiKeys.keyNamePlaceholder')}
                helperText={t('apiKeys.keyNameHelperText')}
              />
            )}

            {newlyCreatedKey && (
              <Alert severity="warning">
                <Typography variant="subtitle2" gutterBottom>
                  {t('apiKeys.saveKeyWarning')}
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
                  <Tooltip title={t('apiKeys.copyKey')}>
                    <IconButton onClick={() => handleCopy(newlyCreatedKey)} aria-label={t('apiKeys.copyKey')}>
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
            {newlyCreatedKey ? tc('actions.done') : tc('actions.cancel')}
          </Button>
          {!newlyCreatedKey && (
            <GradientButton
              onClick={handleGenerate}
              disabled={!keyName.trim() || generateMutation.isPending}
            >
              {generateMutation.isPending ? t('apiKeys.generating') : t('apiKeys.generate')}
            </GradientButton>
          )}
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
