import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Grid, Typography,
} from '@mui/material'
import type { CqlLibrary } from '../../types'

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

  const oldCql = useMemo(() => oldVersion?.cqlContent || '', [oldVersion])
  const newCql = useMemo(() => newVersion?.cqlContent || '', [newVersion])

  const noDifferences = useMemo(() => oldCql === newCql, [oldCql, newCql])

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
            {/* Old Version */}
            <Grid size={6}>
              <Typography variant="subtitle2" gutterBottom>
                {t('compare.oldVersion')}: v{oldVersion?.version || '?'}
              </Typography>
              <Box
                component="pre"
                sx={{
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
                }}
              >
                {oldCql || '-'}
              </Box>
            </Grid>
            {/* New Version */}
            <Grid size={6}>
              <Typography variant="subtitle2" gutterBottom>
                {t('compare.newVersion')}: v{newVersion?.version || '?'}
              </Typography>
              <Box
                component="pre"
                sx={{
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
                }}
              >
                {newCql || '-'}
              </Box>
            </Grid>
          </Grid>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common:actions.close', 'Close')}</Button>
      </DialogActions>
    </Dialog>
  )
}
