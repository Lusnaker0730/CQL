import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { measureApi } from '../../api'
import { extractApiError } from '../../utils/errorUtils'
import type { ConformanceIssue } from '../../types'

interface MeasureExportConformanceDialogProps {
  measureId: number
  open: boolean
  onClose: () => void
  /** Same format keys as the export menu: 'bundle-json' | 'bundle-xml'. */
  onExport: (format: string) => void
}

const SEVERITY_ORDER: ConformanceIssue['severity'][] = ['error', 'warning', 'info']

/** "…/StructureDefinition/computable-measure-cqfm" → "computable-measure-cqfm". */
function shortProfile(url: string): string {
  return url.substring(url.lastIndexOf('/') + 1)
}

/**
 * PAT-229 — what the exchange package of a measure conforms to, and what keeps it from conforming
 * to more. Shown before the author hands the package to another organisation: a missing
 * improvement notation or an unresolved value set is much cheaper to fix here than after the
 * receiver's import fails.
 */
export default function MeasureExportConformanceDialog({
  measureId,
  open,
  onClose,
  onExport,
}: MeasureExportConformanceDialogProps) {
  const { t } = useTranslation('measures')
  const { data, isLoading, error } = useQuery({
    queryKey: ['measure-export-conformance', measureId],
    queryFn: () => measureApi.getExportConformance(measureId),
    enabled: open,
    staleTime: 0,
  })

  const issues = data?.issues ?? []

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('exportConformance.title')}</DialogTitle>
      <DialogContent dividers>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} aria-label={t('exportConformance.loading')} />
          </Box>
        )}
        {error != null && <Alert severity="error">{extractApiError(error)}</Alert>}
        {data && (
          <Stack spacing={2}>
            <Alert severity={data.exchangeReady ? 'success' : 'error'}>
              {data.exchangeReady ? t('exportConformance.ready') : t('exportConformance.notReady')}
            </Alert>

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                {t('exportConformance.measureProfiles')}
              </Typography>
              {data.profiles.length === 0 ? (
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('exportConformance.noProfiles')}
                </Typography>
              ) : (
                <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                  {data.profiles.map((p) => (
                    <Chip key={p} label={shortProfile(p)} size="small" color="primary" variant="outlined" title={p} />
                  ))}
                </Stack>
              )}
              {data.libraryProfiles.length > 0 && (
                <>
                  <Typography variant="subtitle2" sx={{ mt: 1.5 }} gutterBottom>
                    {t('exportConformance.libraryProfiles')}
                  </Typography>
                  <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                    {data.libraryProfiles.map((p) => (
                      <Chip key={p} label={shortProfile(p)} size="small" variant="outlined" title={p} />
                    ))}
                  </Stack>
                </>
              )}
            </Box>

            {data.valueSets.length > 0 && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('exportConformance.valueSets')}
                </Typography>
                <Table size="small" aria-label={t('exportConformance.valueSets')}>
                  <TableHead>
                    <TableRow>
                      <TableCell scope="col">{t('exportConformance.valueSetName')}</TableCell>
                      <TableCell scope="col">{t('exportConformance.valueSetPackaged')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.valueSets.map((vs) => (
                      <TableRow key={vs.url}>
                        <TableCell>
                          <Typography variant="body2">{vs.name || shortProfile(vs.url)}</Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary', wordBreak: 'break-all' }}>
                            {vs.url}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={vs.included ? 'success' : 'warning'}
                            label={
                              vs.included
                                ? t('exportConformance.valueSetIncluded', { source: vs.source })
                                : t('exportConformance.valueSetNameOnly')
                            }
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}

            <Box>
              <Typography variant="subtitle2" gutterBottom>
                {t('exportConformance.issues')}
              </Typography>
              {issues.length === 0 ? (
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('exportConformance.noIssues')}
                </Typography>
              ) : (
                <List dense disablePadding>
                  {SEVERITY_ORDER.flatMap((severity) =>
                    issues
                      .filter((i) => i.severity === severity)
                      .map((issue, index) => (
                        <ListItem key={`${severity}-${index}`} disableGutters sx={{ alignItems: 'flex-start' }}>
                          <Chip
                            size="small"
                            label={t(`exportConformance.severity.${severity}`)}
                            color={severity === 'error' ? 'error' : severity === 'warning' ? 'warning' : 'default'}
                            sx={{ mr: 1, mt: 0.5, minWidth: 64 }}
                          />
                          <ListItemText
                            primary={issue.message}
                            secondary={issue.element}
                            slotProps={{ primary: { variant: 'body2' }, secondary: { variant: 'caption' } }}
                          />
                        </ListItem>
                      )),
                  )}
                </List>
              )}
            </Box>

            {!data.canonicalBaseConfigured && (
              <Alert severity="warning">{t('exportConformance.canonicalBaseHint')}</Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('actions.close', { ns: 'common' })}</Button>
        <Button onClick={() => onExport('bundle-xml')} disabled={!data}>
          {t('editor.exportFormats.fhirXml')}
        </Button>
        <Button variant="contained" onClick={() => onExport('bundle-json')} disabled={!data}>
          {t('editor.exportFormats.fhirJson')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
