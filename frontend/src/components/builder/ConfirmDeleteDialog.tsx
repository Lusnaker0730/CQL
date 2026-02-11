import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
} from '@mui/material'

interface ConfirmDeleteDialogProps {
  open: boolean
  name: string
  onCancel: () => void
  onConfirm: () => void
}

export default function ConfirmDeleteDialog({
  open,
  name,
  onCancel,
  onConfirm,
}: ConfirmDeleteDialogProps) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>Delete Element</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Are you sure you want to delete <strong>{name}</strong>? This will remove the corresponding lines from the CQL editor.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button onClick={onConfirm} color="error" variant="contained">
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  )
}
