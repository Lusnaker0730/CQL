import { useTranslation } from 'react-i18next'
import {
  Box, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Grid, Typography,
} from '@mui/material'
import type { CqlLibrary } from '../../types'

const cqlPreSx = {
  p: 1.5,
  bgcolor: 'grey.50',
  border: 1,
  borderColor: 'divider',
  borderRadius: 1,
  overflow: 'auto',
  maxHeight: 480,
  fontSize: '0.8rem',
  fontFamily: 'monospace',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
  m: 0,
} as const

interface VersionCompareDialogProps {
  open: boolean
  onClose: () => void
  oldVersion: CqlLibrary | null
  newVersion: CqlLibrary | null
}

export default function VersionCompareDialog({
  open, onClose, oldVersion, newVersion,
}: VersionCompareDialogProps) {
  const { t } = useTranslation('cqlLibraries')

  const oldCql = oldVersion?.cqlContent || ''
  const newCql = newVersion?.cqlContent || ''
  const noDifferences = oldCql === newCql

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>{t('compare.title')}</DialogTitle>
      <DialogContent dividers>
        {noDifferences && oldVersion && newVersion ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            {t('compare.noDifferences')}
          </Typography>
        ) : (
          <Grid container spacing={2}>
            <Grid size={6}>
              <Typography variant="subtitle2" gutterBottom>
                {t('compare.oldVersion')}: v{oldVersion?.version || '?'}
              </Typography>
              <Box component="pre" sx={cqlPreSx}>
                {oldCql || '-'}
              </Box>
            </Grid>
            <Grid size={6}>
              <Typography variant="subtitle2" gutterBottom>
                {t('compare.newVersion')}: v{newVersion?.version || '?'}
              </Typography>
              <Box component="pre" sx={cqlPreSx}>
                {newCql || '-'}
              </Box>
            </Grid>
          </Grid>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.close')}</Button>
      </DialogActions>
    </Dialog>
  )
}
