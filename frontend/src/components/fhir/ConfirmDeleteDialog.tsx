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
  resourceType: string
  resourceId: string
  onConfirm: () => void
  onCancel: () => void
  isPending: boolean
}

export default function ConfirmDeleteDialog({
  open,
  resourceType,
  resourceId,
  onConfirm,
  onCancel,
  isPending,
}: ConfirmDeleteDialogProps) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>Delete Resource</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Are you sure you want to delete <strong>{resourceType}/{resourceId}</strong>? This action cannot be undone.
        </DialogContentText>
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
