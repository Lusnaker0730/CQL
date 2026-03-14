import { useTranslation } from 'react-i18next'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Stack,
  Alert,
  CircularProgress,
  Box,
} from '@mui/material'
import { ehrApi } from '../../api'
import type { PatientSearchResult } from '../../types'

interface PatientImportDialogProps {
  open: boolean
  connectionId: number
  patient: PatientSearchResult
  measureId?: number
  onClose: () => void
  onImported?: () => void
}

export default function PatientImportDialog({
  open,
  connectionId,
  patient,
  measureId,
  onClose,
  onImported,
}: PatientImportDialogProps) {
  const { t } = useTranslation('fhir')

  const { data: preview, isLoading } = useQuery({
    queryKey: ['ehr-patient-preview', connectionId, patient.id],
    queryFn: () => ehrApi.getPatientPreview(connectionId, patient.id),
    enabled: open,
  })

  const importMutation = useMutation({
    mutationFn: () => ehrApi.importPatient(connectionId, patient.id, measureId),
    onSuccess: () => {
      onImported?.()
      onClose()
    },
  })

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('ehr.importPreview')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="subtitle2" color="text.secondary">
              {t('ehr.patientName')}
            </Typography>
            <Typography variant="body1" sx={{ fontWeight: 500 }}>
              {patient.name}
            </Typography>
          </Box>

          {patient.birthDate && (
            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                {t('ehr.birthDate')}
              </Typography>
              <Typography variant="body2">{patient.birthDate}</Typography>
            </Box>
          )}

          {importMutation.isError && (
            <Alert severity="error">
              {t('ehr.importFailed', { error: (importMutation.error as Error).message })}
            </Alert>
          )}

          {importMutation.isSuccess && (
            <Alert severity="success">{t('ehr.importSuccess')}</Alert>
          )}

          {isLoading ? (
            <Box sx={{ textAlign: 'center', py: 3 }}>
              <CircularProgress size={24} />
            </Box>
          ) : preview ? (
            <>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>{t('ehr.resourceType')}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>{t('ehr.count')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {Object.entries(preview.resourceCounts).map(([type, count]) => (
                    <TableRow key={type}>
                      <TableCell>{type}</TableCell>
                      <TableCell align="right">{count}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {t('ehr.totalResources')}: {preview.totalResources}
              </Typography>
            </>
          ) : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('ehr.cancel')}</Button>
        <Button
          variant="contained"
          onClick={() => importMutation.mutate()}
          disabled={isLoading || importMutation.isPending || importMutation.isSuccess}
        >
          {importMutation.isPending ? t('ehr.importing') : t('ehr.importPatient')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
