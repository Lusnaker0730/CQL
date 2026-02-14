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
  title = 'Delete',
  itemName,
  message,
  onCancel,
  onConfirm,
  isPending = false,
}: ConfirmDeleteDialogProps): JSX.Element {
  const defaultMessage = `Are you sure you want to delete ${itemName}? This action cannot be undone.`

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message || defaultMessage}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={isPending}>
          Cancel
        </Button>
        <Button
          onClick={onConfirm}
          color="error"
          variant="contained"
          disabled={isPending}
          startIcon={isPending ? <CircularProgress size={16} color="inherit" /> : <DeleteIcon />}
        >
          {isPending ? 'Deleting...' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
