import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  CircularProgress,
} from '@mui/material'
import { Delete as DeleteIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

interface ConfirmDeleteDialogProps {
  open: boolean
  title?: string
  itemName: string
  message?: string
  onCancel: () => void
  onConfirm: () => void
  isPending?: boolean
}

export default function ConfirmDeleteDialog({
  open,
  title,
  itemName,
  message,
  onCancel,
  onConfirm,
  isPending = false,
}: ConfirmDeleteDialogProps): JSX.Element {
  const { t } = useTranslation()
  const defaultMessage = t('dialogs.deleteConfirm.message', { itemName })

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title || t('dialogs.deleteConfirm.title')}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message || defaultMessage}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isPending}>
          {t('actions.cancel')}
        </Button>
        <Button
          onClick={onConfirm}
          color="error"
          variant="contained"
          disabled={isPending}
          startIcon={isPending ? <CircularProgress size={16} color="inherit" /> : <DeleteIcon />}
        >
          {isPending ? t('actions.deleting') : t('actions.delete')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
