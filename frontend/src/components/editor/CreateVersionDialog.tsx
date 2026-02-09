import { useState, useMemo } from 'react'
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
      <DialogTitle>Create New {label} Version</DialogTitle>
      <DialogContent>
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Current version: <strong>{currentVersion}</strong>
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
                  Major
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Breaking changes or significant rework
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
                  Minor
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  New features or enhancements, backward-compatible
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
                  Patch
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Bug fixes or minor corrections
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
            New version:
          </Typography>
          <Typography variant="body1" fontWeight={600} color="primary">
            {newVersion}
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} size="small" disabled={isPending}>
          Cancel
        </Button>
        <GradientButton
          onClick={handleConfirm}
          disabled={isPending}
          startIcon={
            isPending ? <CircularProgress size={16} color="inherit" /> : undefined
          }
        >
          {isPending ? 'Creating...' : 'Create Version'}
        </GradientButton>
      </DialogActions>
    </Dialog>
  )
}
