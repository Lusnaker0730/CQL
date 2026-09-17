import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Stack,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  ToggleButtonGroup,
  ToggleButton,
  Divider,
  Alert,
  Box,
} from '@mui/material'
import {
  PersonAdd as ShareIcon,
  PersonRemove as UnshareIcon,
  Public as PublicIcon,
  Lock as PrivateIcon,
  Group as SharedIcon,
  SwapHoriz as TransferIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useShareLibrary, useUnshareLibrary, useTransferOwnership, useSetAccessLevel } from '../../hooks/useCql'
import type { CqlLibrary } from '../../types'

interface LibraryShareDialogProps {
  open: boolean
  onClose: () => void
  library: CqlLibrary | null
}

export default function LibraryShareDialog({ open, onClose, library }: LibraryShareDialogProps) {
  const { t } = useTranslation('editor')
  const [shareUsername, setShareUsername] = useState('')
  const [transferUsername, setTransferUsername] = useState('')
  const [showTransfer, setShowTransfer] = useState(false)

  const shareMutation = useShareLibrary()
  const unshareMutation = useUnshareLibrary()
  const transferMutation = useTransferOwnership()
  const accessMutation = useSetAccessLevel()

  if (!library) return null

  const handleShare = () => {
    if (!shareUsername.trim()) return
    shareMutation.mutate(
      { id: library.id, targetUsername: shareUsername.trim() },
      { onSuccess: () => setShareUsername('') }
    )
  }

  const handleUnshare = (username: string) => {
    unshareMutation.mutate({ id: library.id, targetUsername: username })
  }

  const handleAccessChange = (_: unknown, newAccess: string | null) => {
    if (newAccess) {
      accessMutation.mutate({ id: library.id, accessLevel: newAccess })
    }
  }

  const handleTransfer = () => {
    if (!transferUsername.trim()) return
    transferMutation.mutate(
      { id: library.id, newOwner: transferUsername.trim() },
      {
        onSuccess: () => {
          setTransferUsername('')
          setShowTransfer(false)
          onClose()
        },
      }
    )
  }

  const currentAccess = library.accessLevel || 'private'
  const sharedWith = library.sharedWith || []

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <SharedIcon color="primary" />
          <Typography variant="h6">
            {t('share.title', { name: library.name, version: library.version })}
          </Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
        {/* Access Level */}
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          {t('share.accessLevel')}
        </Typography>
        <ToggleButtonGroup
          value={currentAccess}
          exclusive
          onChange={handleAccessChange}
          size="small"
          fullWidth
          sx={{ mb: 2 }}
        >
          <ToggleButton value="private">
            <PrivateIcon sx={{ fontSize: 16, mr: 0.5 }} />
            {t('share.private')}
          </ToggleButton>
          <ToggleButton value="shared">
            <SharedIcon sx={{ fontSize: 16, mr: 0.5 }} />
            {t('share.shared')}
          </ToggleButton>
          <ToggleButton value="public">
            <PublicIcon sx={{ fontSize: 16, mr: 0.5 }} />
            {t('share.public')}
          </ToggleButton>
        </ToggleButtonGroup>

        {currentAccess === 'public' && (
          <Alert severity="info" sx={{ mb: 2, py: 0 }}>
            <Typography variant="caption">{t('share.publicDesc')}</Typography>
          </Alert>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Share with specific users */}
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          {t('share.shareWithUsers')}
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
          <TextField
            size="small"
            placeholder={t('share.enterUsername')}
            value={shareUsername}
            onChange={(e) => setShareUsername(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleShare()}
            fullWidth
          />
          <GradientButton
            size="small"
            startIcon={<ShareIcon />}
            onClick={handleShare}
            disabled={!shareUsername.trim() || shareMutation.isPending}
            sx={{ whiteSpace: 'nowrap' }}
          >
            {t('share.share')}
          </GradientButton>
        </Stack>

        {sharedWith.length > 0 ? (
          <List dense disablePadding>
            {sharedWith.map((username) => (
              <ListItem key={username} sx={{ py: 0.25 }}>
                <ListItemText
                  primary={username}
                  slotProps={{
                    primary: { variant: 'body2' }
                  }}
                />
                <ListItemSecondaryAction>
                  <IconButton
                    size="small"
                    aria-label={t('share.unshareWithUser')}
                    onClick={() => handleUnshare(username)}
                    disabled={unshareMutation.isPending}
                    color="error"
                  >
                    <UnshareIcon fontSize="small" />
                  </IconButton>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        ) : (
          <Typography variant="caption" sx={{
            color: "text.secondary"
          }}>
            {t('share.notShared')}
          </Typography>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Owner info & Transfer */}
        <Stack
          direction="row"
          sx={{
            alignItems: "center",
            justifyContent: "space-between"
          }}>
          <Stack direction="row" spacing={1} sx={{
            alignItems: "center"
          }}>
            <Typography variant="subtitle2">{t('share.owner')}</Typography>
            <Chip label={library.ownerUsername || t('share.unassigned')} size="small" />
          </Stack>
          <Button
            size="small"
            startIcon={<TransferIcon />}
            onClick={() => setShowTransfer(!showTransfer)}
            sx={{ textTransform: 'none' }}
          >
            {t('share.transfer')}
          </Button>
        </Stack>

        {showTransfer && (
          <Box sx={{ mt: 1 }}>
            <Alert severity="warning" sx={{ mb: 1, py: 0 }}>
              <Typography variant="caption">{t('share.transferWarning')}</Typography>
            </Alert>
            <Stack direction="row" spacing={1}>
              <TextField
                size="small"
                placeholder={t('share.newOwnerUsername')}
                value={transferUsername}
                onChange={(e) => setTransferUsername(e.target.value)}
                fullWidth
              />
              <Button
                variant="contained"
                color="warning"
                size="small"
                onClick={handleTransfer}
                disabled={!transferUsername.trim() || transferMutation.isPending}
              >
                {t('share.transfer')}
              </Button>
            </Stack>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
