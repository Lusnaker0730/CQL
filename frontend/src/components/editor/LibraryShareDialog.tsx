import { useState } from 'react'
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
import { useShareLibrary, useUnshareLibrary, useTransferOwnership, useSetAccessLevel } from '../../hooks/useCql'
import type { CqlLibrary } from '../../types'

interface LibraryShareDialogProps {
  open: boolean
  onClose: () => void
  library: CqlLibrary | null
}

export default function LibraryShareDialog({ open, onClose, library }: LibraryShareDialogProps) {
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
        <Stack direction="row" alignItems="center" spacing={1}>
          <SharedIcon color="primary" />
          <Typography variant="h6">
            Share: {library.name} v{library.version}
          </Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
        {/* Access Level */}
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Access Level
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
            Private
          </ToggleButton>
          <ToggleButton value="shared">
            <SharedIcon sx={{ fontSize: 16, mr: 0.5 }} />
            Shared
          </ToggleButton>
          <ToggleButton value="public">
            <PublicIcon sx={{ fontSize: 16, mr: 0.5 }} />
            Public
          </ToggleButton>
        </ToggleButtonGroup>

        {currentAccess === 'public' && (
          <Alert severity="info" sx={{ mb: 2, py: 0 }}>
            <Typography variant="caption">All users can view and use this library.</Typography>
          </Alert>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Share with specific users */}
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Share with Users
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
          <TextField
            size="small"
            placeholder="Enter username..."
            value={shareUsername}
            onChange={(e) => setShareUsername(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleShare()}
            fullWidth
          />
          <Button
            variant="contained"
            size="small"
            startIcon={<ShareIcon />}
            onClick={handleShare}
            disabled={!shareUsername.trim() || shareMutation.isPending}
            sx={{
              background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
              whiteSpace: 'nowrap',
            }}
          >
            Share
          </Button>
        </Stack>

        {sharedWith.length > 0 ? (
          <List dense disablePadding>
            {sharedWith.map((username) => (
              <ListItem key={username} sx={{ py: 0.25 }}>
                <ListItemText
                  primary={username}
                  primaryTypographyProps={{ variant: 'body2' }}
                />
                <ListItemSecondaryAction>
                  <IconButton
                    size="small"
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
          <Typography variant="caption" color="text.secondary">
            Not shared with any specific users.
          </Typography>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Owner info & Transfer */}
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Stack direction="row" alignItems="center" spacing={1}>
            <Typography variant="subtitle2">Owner:</Typography>
            <Chip label={library.ownerUsername || 'unassigned'} size="small" />
          </Stack>
          <Button
            size="small"
            startIcon={<TransferIcon />}
            onClick={() => setShowTransfer(!showTransfer)}
            sx={{ textTransform: 'none' }}
          >
            Transfer
          </Button>
        </Stack>

        {showTransfer && (
          <Box sx={{ mt: 1 }}>
            <Alert severity="warning" sx={{ mb: 1, py: 0 }}>
              <Typography variant="caption">Transferring ownership cannot be undone by you.</Typography>
            </Alert>
            <Stack direction="row" spacing={1}>
              <TextField
                size="small"
                placeholder="New owner username..."
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
                Transfer
              </Button>
            </Stack>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  )
}
