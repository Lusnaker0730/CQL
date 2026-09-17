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
  const { t } = useTranslation('measures')
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
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <SharedIcon color="primary" />
          <Typography variant="h6">
            {t('share.title', { name: measure.title || measure.name })}
          </Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
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
            {t('share.levels.private')}
          </ToggleButton>
          <ToggleButton value="shared">
            <SharedIcon sx={{ fontSize: 16, mr: 0.5 }} />
            {t('share.levels.shared')}
          </ToggleButton>
          <ToggleButton value="public">
            <PublicIcon sx={{ fontSize: 16, mr: 0.5 }} />
            {t('share.levels.public')}
          </ToggleButton>
        </ToggleButtonGroup>

        {currentAccess === 'public' && (
          <Alert severity="info" sx={{ mb: 2, py: 0 }}>
            <Typography variant="caption">{t('share.publicInfo')}</Typography>
          </Alert>
        )}

        <Divider sx={{ my: 1.5 }} />

        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          {t('share.shareWithUsers')}
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
          <TextField
            size="small"
            placeholder={t('share.usernamePlaceholder')}
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
              background: (theme) =>
                `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.primary.light} 100%)`,
              whiteSpace: 'nowrap',
            }}
          >
            {t('share.shareButton')}
          </Button>
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
                    aria-label={t('share.unshare')}
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
            {t('share.noSharedUsers')}
          </Typography>
        )}

        <Divider sx={{ my: 1.5 }} />

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
            <Chip label={measure.ownerUsername || t('share.unassigned')} size="small" />
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
                placeholder={t('share.newOwnerPlaceholder')}
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
        <Button onClick={onClose}>{t('share.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
