import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  RadioGroup,
  FormControlLabel,
  Radio,
  Typography,
  Box,
  CircularProgress,
} from '@mui/material'
import GradientButton from '../common/GradientButton'

type VersionBump = 'major' | 'minor' | 'patch'

interface CreateVersionDialogProps {
  open: boolean
  onClose: () => void
  onConfirm: (type: VersionBump) => void
  currentVersion: string
  isPending: boolean
  entityType: 'library' | 'measure'
}

function computeNewVersion(current: string, bump: VersionBump): string {
  const parts = current.split('.').map(Number)
  const major = parts[0] || 0
  const minor = parts[1] || 0
  const patch = parts[2] || 0

  switch (bump) {
    case 'major':
      return `${major + 1}.0.0`
    case 'minor':
      return `${major}.${minor + 1}.0`
    case 'patch':
      return `${major}.${minor}.${patch + 1}`
  }
}

export default function CreateVersionDialog({
  open,
  onClose,
  onConfirm,
  currentVersion,
  isPending,
  entityType,
}: CreateVersionDialogProps) {
  const { t } = useTranslation('editor')
  const [bumpType, setBumpType] = useState<VersionBump>('minor')

  const newVersion = useMemo(
    () => computeNewVersion(currentVersion, bumpType),
    [currentVersion, bumpType],
  )

  const handleConfirm = () => {
    onConfirm(bumpType)
  }

  const label = entityType === 'library' ? 'Library' : 'Measure'

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t('version.createTitle', { type: label })}</DialogTitle>
      <DialogContent>
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {t('version.currentVersion')}<strong>{currentVersion}</strong>
          </Typography>
        </Box>

        <RadioGroup
          value={bumpType}
          onChange={(e) => setBumpType(e.target.value as VersionBump)}
        >
          <FormControlLabel
            value="major"
            control={<Radio size="small" />}
            label={
              <Box>
                <Typography variant="body2" fontWeight={500}>
                  {t('version.major')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('version.majorDesc')}
                </Typography>
              </Box>
            }
          />
          <FormControlLabel
            value="minor"
            control={<Radio size="small" />}
            label={
              <Box>
                <Typography variant="body2" fontWeight={500}>
                  {t('version.minor')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('version.minorDesc')}
                </Typography>
              </Box>
            }
          />
          <FormControlLabel
            value="patch"
            control={<Radio size="small" />}
            label={
              <Box>
                <Typography variant="body2" fontWeight={500}>
                  {t('version.patch')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('version.patchDesc')}
                </Typography>
              </Box>
            }
          />
        </RadioGroup>

        <Box
          sx={{
            mt: 2,
            p: 1.5,
            bgcolor: 'action.hover',
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {t('version.newVersion')}
          </Typography>
          <Typography variant="body1" fontWeight={600} color="primary">
            {newVersion}
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} size="small" disabled={isPending}>
          {t('common:actions.cancel')}
        </Button>
        <GradientButton
          onClick={handleConfirm}
          disabled={isPending}
          startIcon={
            isPending ? <CircularProgress size={16} color="inherit" /> : undefined
          }
        >
          {isPending ? t('version.creating') : t('version.createVersion')}
        </GradientButton>
      </DialogActions>
    </Dialog>
  )
}
