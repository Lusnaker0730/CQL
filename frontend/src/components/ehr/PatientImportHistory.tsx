import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { STALE_1M } from '../../constants/queryConstants'
import {
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from '@mui/material'
import { ehrApi } from '../../api'

export default function PatientImportHistory() {
  const { t, i18n } = useTranslation('fhir')

  const { data: imports = [] } = useQuery({
    queryKey: ['ehr-import-history'],
    queryFn: () => ehrApi.getImportHistory(),
    staleTime: STALE_1M,
  })

  if (imports.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
        {t('ehr.noImports')}
      </Typography>
    )
  }

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ fontWeight: 600 }}>{t('ehr.patientName')}</TableCell>
            <TableCell sx={{ fontWeight: 600 }}>{t('ehr.identifier')}</TableCell>
            <TableCell sx={{ fontWeight: 600 }} align="right">{t('ehr.resourceCount')}</TableCell>
            <TableCell sx={{ fontWeight: 600 }}>{t('ehr.importedBy')}</TableCell>
            <TableCell sx={{ fontWeight: 600 }}>{t('ehr.importDate')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {imports.map((imp) => (
            <TableRow key={imp.id}>
              <TableCell>{imp.patientName || '--'}</TableCell>
              <TableCell>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                  {imp.patientIdentifier || imp.patientFhirId}
                </Typography>
              </TableCell>
              <TableCell align="right">{imp.resourceCount}</TableCell>
              <TableCell>{imp.importedBy || '--'}</TableCell>
              <TableCell>
                {imp.createdAt
                  ? new Date(imp.createdAt).toLocaleDateString(i18n.language, {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })
                  : '--'}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
