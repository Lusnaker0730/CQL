import { useMemo } from 'react'
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  LinearProgress,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useTranslation } from 'react-i18next'
import type { RetrieveTrace } from '../../types'

interface RetrieveTraceTableProps {
  traces: RetrieveTrace[]
}

/**
 * Shared FHIR-retrieve trace table. Rendered by both the editor and CDS
 * debug panels — same shape, same labels.
 */
export default function RetrieveTraceTable({ traces }: RetrieveTraceTableProps) {
  const { t } = useTranslation('common')

  const maxRetrieveTime = useMemo(
    () => Math.max(...traces.map((rt) => rt.retrieveTimeMs), 1),
    [traces]
  )

  if (traces.length === 0) return null

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'secondary.main' }}>
        {t('debug.fhirRetrieveTrace')}
      </Typography>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col">{t('debug.resourceType')}</TableCell>
              <TableCell scope="col">{t('debug.count')}</TableCell>
              <TableCell scope="col" width={160}>{t('debug.time')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {traces.map((rt, i) => (
              <TableRow key={`${rt.resourceType}-${i}`} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {rt.resourceType}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={rt.resourceCount}
                    size="small"
                    sx={(theme) => ({
                      bgcolor: alpha(theme.palette.primary.main, 0.08),
                      color: 'primary.dark',
                      fontWeight: 600,
                    })}
                  />
                </TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <LinearProgress
                      variant="determinate"
                      value={(rt.retrieveTimeMs / maxRetrieveTime) * 100}
                      sx={(theme) => ({
                        flexGrow: 1,
                        height: 6,
                        borderRadius: 3,
                        bgcolor: alpha(theme.palette.secondary.main, 0.08),
                        '& .MuiLinearProgress-bar': {
                          bgcolor: 'secondary.main',
                          borderRadius: 3,
                        },
                      })}
                    />
                    <Typography variant="caption" sx={{ minWidth: 40, textAlign: 'right' }}>
                      {rt.retrieveTimeMs}ms
                    </Typography>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
