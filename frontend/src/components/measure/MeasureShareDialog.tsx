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
import {
  useShareMeasure,
  useUnshareMeasure,
  useTransferMeasureOwnership,
  useSetMeasureAccessLevel,
} from '../../hooks/useMeasures'
import type { MeasureDefinition } from '../../types'

interface MeasureShareDialogProps {
  open: boolean
  onClose: () => void
  measure: MeasureDefinition | null
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureShareDialog({ open, onClose, measure, onMeasureUpdate }: MeasureShareDialogProps) {
  const [shareUsername, setShareUsername] = useState('')
  const [transferUsername, setTransferUsername] = useState('')
  const [showTransfer, setShowTransfer] = useState(false)

  const shareMutation = useShareMeasure()
  const unshareMutation = useUnshareMeasure()
  const transferMutation = useTransferMeasureOwnership()
  const accessMutation = useSetMeasureAccessLevel()

  if (!measure) return null

  const handleShare = () => {
    if (!shareUsername.trim() || !measure.id) return
    shareMutation.mutate(
      { id: measure.id, targetUsername: shareUsername.trim() },
      {
        onSuccess: (updated) => {
          setShareUsername('')
          onMeasureUpdate(updated)
        },
      }
    )
  }

  const handleUnshare = (username: string) => {
    if (!measure.id) return
    unshareMutation.mutate(
      { id: measure.id, targetUsername: username },
      { onSuccess: (updated) => onMeasureUpdate(updated) }
    )
  }

  const handleAccessChange = (_: unknown, newAccess: string | null) => {
    if (newAccess && measure.id) {
      accessMutation.mutate(
        { id: measure.id, accessLevel: newAccess },
        { onSuccess: (updated) => onMeasureUpdate(updated) }
      )
    }
  }

  const handleTransfer = () => {
    if (!transferUsername.trim() || !measure.id) return
    transferMutation.mutate(
      { id: measure.id, newOwner: transferUsername.trim() },
      {
        onSuccess: (updated) => {
          setTransferUsername('')
          setShowTransfer(false)
          onMeasureUpdate(updated)
          onClose()
        },
      }
    )
  }

  const currentAccess = measure.accessLevel || 'private'
  const sharedWith = measure.sharedWith || []

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" alignItems="center" spacing={1}>
          <SharedIcon color="primary" />
          <Typography variant="h6">
            Share: {measure.title || measure.name}
          </Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
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
            <Typography variant="caption">All users can view and use this measure.</Typography>
          </Alert>
        )}

        <Divider sx={{ my: 1.5 }} />

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
                    aria-label="Unshare with user"
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

        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Stack direction="row" alignItems="center" spacing={1}>
            <Typography variant="subtitle2">Owner:</Typography>
            <Chip label={measure.ownerUsername || 'unassigned'} size="small" />
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
