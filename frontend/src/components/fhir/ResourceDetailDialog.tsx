import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Stack,
  IconButton,
  Tooltip,
  Snackbar,
  Alert,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Close as CloseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { formatJson } from '../../utils/fhirBrowserUtils'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import ResourceEditorDialog from './ResourceEditorDialog'

interface ResourceDetailDialogProps {
  open: boolean
  resource: object | null
  resourceType: string
  resourceId: string
  fhirServer: string
  onClose: () => void
  onDeleted?: () => void
  onUpdated?: () => void
}

export default function ResourceDetailDialog({
  open,
  resource,
  resourceType,
  resourceId,
  fhirServer,
  onClose,
  onDeleted,
  onUpdated,
}: ResourceDetailDialogProps) {
  const { t } = useTranslation('fhir')
  const { t: tc } = useTranslation('common')
  const [copied, setCopied] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const deleteMutation = useMutation({
    mutationFn: () => fhirApi.deleteResource(resourceType, resourceId, fhirServer),
    onSuccess: () => {
      setDeleteOpen(false)
      onClose()
      onDeleted?.()
    },
    onError: (err) => {
      setDeleteOpen(false)
      setDeleteError(err instanceof Error ? err.message : 'Delete failed')
    },
  })

  const handleCopy = () => {
    if (resource) {
      navigator.clipboard.writeText(formatJson(resource))
      setCopied(true)
    }
  }

  const jsonString = resource ? formatJson(resource) : ''

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {resourceType}/{resourceId}
          <IconButton onClick={onClose} size="small" aria-label={t('detail.closeDialog')}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          {deleteError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setDeleteError(null)}>
              {deleteError}
            </Alert>
          )}
          <Box
            component="pre"
            sx={{
              p: 2,
              bgcolor: '#1e1e1e',
              color: '#d4d4d4',
              borderRadius: '8px',
              fontSize: '0.75rem',
              overflow: 'auto',
              maxHeight: 500,
              fontFamily: '"Consolas", "Monaco", monospace',
              m: 0,
            }}
          >
            {jsonString}
          </Box>
        </DialogContent>
        <DialogActions>
          <Stack direction="row" spacing={1}>
            <Tooltip title={t('detail.copyJson')}>
              <Button size="small" startIcon={<CopyIcon />} onClick={handleCopy}>
                {t('detail.copy')}
              </Button>
            </Tooltip>
            <Tooltip title={t('detail.editResource')}>
              <Button size="small" startIcon={<EditIcon />} onClick={() => setEditOpen(true)}>
                {tc('actions.edit')}
              </Button>
            </Tooltip>
            <Tooltip title={t('detail.deleteResource')}>
              <Button size="small" color="error" startIcon={<DeleteIcon />} onClick={() => setDeleteOpen(true)}>
                {tc('actions.delete')}
              </Button>
            </Tooltip>
          </Stack>
        </DialogActions>
      </Dialog>

      <ConfirmDeleteDialog
        open={deleteOpen}
        title={t('detail.deleteTitle')}
        itemName={`${resourceType}/${resourceId}`}
        onConfirm={() => deleteMutation.mutate()}
        onCancel={() => setDeleteOpen(false)}
        isPending={deleteMutation.isPending}
      />

      <ResourceEditorDialog
        open={editOpen}
        mode="edit"
        resourceType={resourceType}
        resourceId={resourceId}
        initialJson={jsonString}
        fhirServer={fhirServer}
        onClose={() => setEditOpen(false)}
        onSaved={() => {
          setEditOpen(false)
          onUpdated?.()
        }}
      />

      <Snackbar open={copied} autoHideDuration={2000} onClose={() => setCopied(false)}>
        <Alert severity="success" variant="filled">{t('detail.jsonCopied')}</Alert>
      </Snackbar>
    </>
  )
}
