import { useTranslation } from 'react-i18next'
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'

interface Props {
  open: boolean
  onCancel: () => void
  onOverwrite: () => void
}

/**
 * PAT-238 — the measure was edited on the measure page since the last publish; publishing again
 * would replace those edits with the builder's logic. The author decides: cancel (and bring the
 * edits into the builder) or overwrite (re-publish with force).
 */
export default function PublishConflictDialog({ open, onCancel, onOverwrite }: Props) {
  const { t } = useTranslation('ecqm')
  return (
    <Dialog open={open} onClose={onCancel}>
      <DialogTitle>{t('publishConflict.title')}</DialogTitle>
      <DialogContent>
        <DialogContentText>{t('publishConflict.message')}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>{t('common:actions.cancel')}</Button>
        <Button color="warning" variant="contained" onClick={onOverwrite}>
          {t('publishConflict.overwrite')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
