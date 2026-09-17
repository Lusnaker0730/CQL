import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Stack,
  Box,
  Chip,
  CircularProgress,
  List,
  ListItem,
  Divider,
} from '@mui/material'
import {
  History as HistoryIcon,
  Add as CreateIcon,
  Edit as UpdateIcon,
  Delete as DeleteIcon,
  Share as ShareIcon,
  RateReview as ReviewIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Archive as RetireIcon,
  SwapHoriz as TransferIcon,
} from '@mui/icons-material'
import { useMeasureAuditTrail } from '../../hooks/useMeasures'
import { useTranslation } from 'react-i18next'

const ACTION_CONFIG: Record<string, { icon: React.ReactElement; color: 'primary' | 'success' | 'error' | 'warning' | 'info' | 'default' }> = {
  CREATE: { icon: <CreateIcon sx={{ fontSize: 16 }} />, color: 'success' },
  UPDATE: { icon: <UpdateIcon sx={{ fontSize: 16 }} />, color: 'primary' },
  DELETE: { icon: <DeleteIcon sx={{ fontSize: 16 }} />, color: 'error' },
  SHARE: { icon: <ShareIcon sx={{ fontSize: 16 }} />, color: 'info' },
  UNSHARE: { icon: <ShareIcon sx={{ fontSize: 16 }} />, color: 'warning' },
  TRANSFER_OWNERSHIP: { icon: <TransferIcon sx={{ fontSize: 16 }} />, color: 'warning' },
  ACCESS_LEVEL_CHANGE: { icon: <ShareIcon sx={{ fontSize: 16 }} />, color: 'info' },
  SUBMIT_FOR_REVIEW: { icon: <ReviewIcon sx={{ fontSize: 16 }} />, color: 'info' },
  APPROVE: { icon: <ApproveIcon sx={{ fontSize: 16 }} />, color: 'success' },
  REJECT: { icon: <RejectIcon sx={{ fontSize: 16 }} />, color: 'error' },
  RETIRE: { icon: <RetireIcon sx={{ fontSize: 16 }} />, color: 'warning' },
}

interface AuditTrailDialogProps {
  open: boolean
  onClose: () => void
  measureId: number | undefined
  measureName: string
}

export default function AuditTrailDialog({ open, onClose, measureId, measureName }: AuditTrailDialogProps) {
  const { t } = useTranslation('measures')
  const { data: auditEntries = [], isLoading } = useMeasureAuditTrail(measureId, open)

  const formatDate = (dateStr: string) => {
    const d = new Date(dateStr)
    return d.toLocaleString()
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <HistoryIcon color="primary" />
          <Typography variant="h6">{t('audit.title', { name: measureName })}</Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={24} />
          </Box>
        )}

        {!isLoading && auditEntries.length === 0 && (
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              py: 4,
              textAlign: 'center'
            }}>
            {t('audit.emptyState')}
          </Typography>
        )}

        {!isLoading && auditEntries.length > 0 && (
          <List disablePadding>
            {auditEntries.map((entry, idx) => {
              const config = ACTION_CONFIG[entry.action] || { icon: <UpdateIcon sx={{ fontSize: 16 }} />, color: 'default' as const }
              return (
                <Box key={entry.id}>
                  <ListItem sx={{ px: 0, py: 1 }}>
                    <Stack
                      direction="row"
                      spacing={1.5}
                      sx={{
                        alignItems: "flex-start",
                        width: '100%'
                      }}>
                      <Box
                        sx={{
                          mt: 0.25,
                          width: 28,
                          height: 28,
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          bgcolor: `${config.color}.50`,
                          color: `${config.color}.main`,
                          flexShrink: 0,
                        }}
                      >
                        {config.icon}
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack
                          direction="row"
                          spacing={1}
                          sx={{
                            alignItems: "center",
                            mb: 0.25
                          }}>
                          <Chip
                            label={entry.action.replace(/_/g, ' ')}
                            size="small"
                            color={config.color}
                            variant="outlined"
                            sx={{ fontSize: '0.7rem', height: 20 }}
                          />
                          {entry.performedBy && (
                            <Typography variant="caption" sx={{
                              color: "text.secondary"
                            }}>
                              {t('audit.by', { user: entry.performedBy })}
                            </Typography>
                          )}
                        </Stack>
                        {entry.details && (
                          <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                            {entry.details}
                          </Typography>
                        )}
                        <Typography variant="caption" sx={{
                          color: "text.secondary"
                        }}>
                          {formatDate(entry.createdAt)}
                        </Typography>
                      </Box>
                    </Stack>
                  </ListItem>
                  {idx < auditEntries.length - 1 && <Divider />}
                </Box>
              );
            })}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('audit.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
