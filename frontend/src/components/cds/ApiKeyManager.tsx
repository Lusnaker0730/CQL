import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { alpha } from '@mui/material/styles'
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
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the old Proxy mock).
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import CopyIcon from '@mui/icons-material/ContentCopy'
import KeyIcon from '@mui/icons-material/VpnKey'
import { useApiKeys, useGenerateApiKey, useRevokeApiKey } from '../../hooks/useCdsHooks'
import { getStoredUsername } from '../../utils/validation'
import { useNotification } from '../../hooks/useNotification'
import GradientButton from '../common/GradientButton'
import TableSkeleton from '../common/TableSkeleton'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'

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
  const [pendingRevokeId, setPendingRevokeId] = useState<number | null>(null)

  // localStorage read shouldn't run on every render.
  const username = useMemo(() => getStoredUsername(), [])
  const baseUrl = window.location.origin
  const perUserEndpoint = `${baseUrl}/cds-services/u/${username}`

  // Guards post-await setState calls when the user navigates away mid-flight.
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  const handleGenerate = async () => {
    if (!keyName.trim()) return
    try {
      const result = await generateMutation.mutateAsync(keyName.trim())
      if (!isMountedRef.current) return
      setNewlyCreatedKey(result.key || null)
      setKeyName('')
      showNotification(t('apiKeys.generateSuccess'), 'success')
    } catch {
      if (!isMountedRef.current) return
      showNotification(t('apiKeys.generateFailed'), 'error')
    }
  }

  const handleConfirmRevoke = async () => {
    if (pendingRevokeId == null) return
    const id = pendingRevokeId
    try {
      await revokeMutation.mutateAsync(id)
      if (!isMountedRef.current) return
      setPendingRevokeId(null)
      showNotification(t('apiKeys.revokeSuccess'), 'success')
    } catch {
      if (!isMountedRef.current) return
      setPendingRevokeId(null)
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
            sx={(theme) => ({
              fontFamily: '"Consolas", monospace',
              bgcolor: alpha(theme.palette.common.black, 0.04),
              px: 1,
              py: 0.5,
              borderRadius: 1,
            })}
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
                        onClick={() => setPendingRevokeId(key.id)}
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

      <ConfirmDeleteDialog
        open={pendingRevokeId != null}
        title={t('apiKeys.revokeDialogTitle')}
        itemName={t('apiKeys.revokeItemName')}
        message={t('apiKeys.revokeDialogMessage')}
        onCancel={() => setPendingRevokeId(null)}
        onConfirm={handleConfirmRevoke}
        isPending={revokeMutation.isPending}
      />

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
                    sx={(theme) => ({
                      fontFamily: '"Consolas", monospace',
                      bgcolor: alpha(theme.palette.common.black, 0.06),
                      px: 1.5,
                      py: 1,
                      borderRadius: 1,
                      wordBreak: 'break-all',
                      flex: 1,
                    })}
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
