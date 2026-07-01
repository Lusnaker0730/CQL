import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Chip, Divider, IconButton, List, ListItem, ListItemText,
  Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import {
  PersonAdd as ShareIcon,
  PersonRemove as UnshareIcon,
  SwapHoriz as TransferIcon,
} from '@mui/icons-material'
import type { CqlLibrary } from '../../types'
import {
  useShareCqlLibrary,
  useUnshareCqlLibrary,
  useTransferCqlLibraryOwnership,
  useSetCqlLibraryAccessLevel,
} from '../../hooks/useCqlLibraries'
import SectionHeader from '../common/SectionHeader'
import GradientButton from '../common/GradientButton'
import { extractApiError } from '../../utils/errorUtils'

interface CqlLibrarySharingTabProps {
  library: CqlLibrary
}

export default function CqlLibrarySharingTab({ library }: CqlLibrarySharingTabProps) {
  const { t } = useTranslation('cqlLibraries')

  const [shareUsername, setShareUsername] = useState('')
  const [transferUsername, setTransferUsername] = useState('')
  const [error, setError] = useState<string | null>(null)

  const shareMutation = useShareCqlLibrary()
  const unshareMutation = useUnshareCqlLibrary()
  const transferMutation = useTransferCqlLibraryOwnership()
  const accessLevelMutation = useSetCqlLibraryAccessLevel()

  const handleShare = useCallback(() => {
    if (!shareUsername.trim()) return
    setError(null)
    shareMutation.mutate(
      { id: library.id, targetUsername: shareUsername.trim() },
      {
        onSuccess: () => {
          setShareUsername('')
        },
        onError: (err) => setError(extractApiError(err)),
      }
    )
  }, [library.id, shareUsername, shareMutation])

  const handleUnshare = useCallback((username: string) => {
    setError(null)
    unshareMutation.mutate(
      { id: library.id, targetUsername: username },
      {
        onError: (err) => setError(extractApiError(err)),
      }
    )
  }, [library.id, unshareMutation])

  const handleTransfer = useCallback(() => {
    if (!transferUsername.trim()) return
    setError(null)
    transferMutation.mutate(
      { id: library.id, newOwner: transferUsername.trim() },
      {
        onSuccess: () => {
          setTransferUsername('')
        },
        onError: (err) => setError(extractApiError(err)),
      }
    )
  }, [library.id, transferUsername, transferMutation])

  const handleAccessLevelChange = useCallback((level: string) => {
    setError(null)
    accessLevelMutation.mutate(
      { id: library.id, accessLevel: level },
      {
        onError: (err) => setError(extractApiError(err)),
      }
    )
  }, [library.id, accessLevelMutation])

  const sharedUsers = library.sharedWith || []

  return (
    <Box sx={{ maxWidth: 700 }}>
      <SectionHeader title={t('sharing.title')} />
      {error && (
        <Typography variant="body2" color="error" sx={{ mb: 2 }}>
          {error}
        </Typography>
      )}
      {/* Access Level */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>{t('metadata.accessLevel')}</Typography>
        <Stack direction="row" spacing={1}>
          {['private', 'shared', 'public'].map((level) => (
            <Chip
              key={level}
              label={t(`list.access${level.charAt(0).toUpperCase() + level.slice(1)}` as 'list.accessPrivate')}
              color={library.accessLevel === level ? 'primary' : 'default'}
              variant={library.accessLevel === level ? 'filled' : 'outlined'}
              onClick={() => handleAccessLevelChange(level)}
              disabled={accessLevelMutation.isPending}
            />
          ))}
        </Stack>
      </Box>
      <Divider sx={{ my: 2 }} />
      {/* Share with user */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>{t('sharing.shareWith')}</Typography>
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            placeholder={t('sharing.enterUsername')}
            value={shareUsername}
            onChange={(e) => setShareUsername(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleShare() }}
            sx={{ flex: 1 }}
          />
          <GradientButton
            onClick={handleShare}
            disabled={!shareUsername.trim() || shareMutation.isPending}
            startIcon={<ShareIcon />}
          >
            {t('header.share')}
          </GradientButton>
        </Stack>
      </Box>
      {/* Shared users list */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>{t('sharing.sharedUsers')}</Typography>
        {sharedUsers.length === 0 ? (
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('sharing.noSharedUsers')}
          </Typography>
        ) : (
          <List dense disablePadding>
            {sharedUsers.map((username) => (
              <ListItem
                key={username}
                secondaryAction={
                  <Tooltip title={t('sharing.unshare')}>
                    <IconButton
                      edge="end"
                      size="small"
                      onClick={() => handleUnshare(username)}
                      disabled={unshareMutation.isPending}
                    >
                      <UnshareIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                }
                sx={{ pl: 0 }}
              >
                <ListItemText primary={username} />
              </ListItem>
            ))}
          </List>
        )}
      </Box>
      <Divider sx={{ my: 2 }} />
      {/* Transfer ownership */}
      <Box>
        <Typography variant="subtitle2" gutterBottom>{t('sharing.transfer')}</Typography>
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
            display: "block",
            mb: 1
          }}>
          {t('sharing.retainAccess')}
        </Typography>
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            placeholder={t('sharing.transferTo')}
            value={transferUsername}
            onChange={(e) => setTransferUsername(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleTransfer() }}
            sx={{ flex: 1 }}
          />
          <Button
            variant="outlined"
            color="warning"
            onClick={handleTransfer}
            disabled={!transferUsername.trim() || transferMutation.isPending}
            startIcon={<TransferIcon />}
          >
            {t('sharing.transfer')}
          </Button>
        </Stack>
      </Box>
    </Box>
  );
}
