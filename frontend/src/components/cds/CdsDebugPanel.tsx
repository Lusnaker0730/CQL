import { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  Alert,
  AlertTitle,
  Chip,
  Collapse,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection (vitest 4 chokes on the old Proxy mock).
import ExpandIcon from '@mui/icons-material/KeyboardArrowDown'
import CollapseIcon from '@mui/icons-material/KeyboardArrowUp'
import DebugIcon from '@mui/icons-material/BugReport'
import SuccessIcon from '@mui/icons-material/CheckCircle'
import ErrorIcon from '@mui/icons-material/Error'
import EmptyIcon from '@mui/icons-material/RadioButtonUnchecked'
import { useTranslation } from 'react-i18next'
import type { CdsDebugInfo, PrefetchStatus, FhirServerDiagnostics } from '../../types'
import ExpressionTraceTable from '../debug/ExpressionTraceTable'
import RetrieveTraceTable from '../debug/RetrieveTraceTable'
import ElmJsonViewer from '../debug/ElmJsonViewer'
import ExecutionErrorAlert from '../debug/ExecutionErrorAlert'

interface Props {
  debug: CdsDebugInfo
}

function prefetchStatusIcon(status: string) {
  if (status === 'success') return <SuccessIcon fontSize="small" color="success" />
  if (status === 'failed') return <ErrorIcon fontSize="small" color="error" />
  return <EmptyIcon fontSize="small" color="disabled" />
}

function fhirServerSeverity(status: string): 'error' | 'warning' | 'info' | 'success' {
  if (status === 'error') return 'error'
  if (status === 'not_attempted') return 'warning'
  if (status === 'success') return 'success'
  return 'info'
}

interface PrefetchSectionProps {
  statuses: PrefetchStatus[]
}

function PrefetchSection({ statuses }: PrefetchSectionProps) {
  const { t } = useTranslation('cds')
  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'secondary.main' }}>
        {t('debug.prefetchStatus')}
      </Typography>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 32 }} />
              <TableCell>{t('debug.prefetchKey')}</TableCell>
              <TableCell>{t('debug.prefetchCount')}</TableCell>
              <TableCell>{t('debug.prefetchTime')}</TableCell>
              <TableCell>{t('debug.prefetchDetail')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {statuses.map((s) => (
              <TableRow key={s.key} hover>
                <TableCell>{prefetchStatusIcon(s.status)}</TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                    {s.key}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip size="small" label={s.count ?? 0} variant="outlined" />
                </TableCell>
                <TableCell>
                  <Typography variant="caption">{s.elapsedMs != null ? `${s.elapsedMs}ms` : '-'}</Typography>
                </TableCell>
                <TableCell>
                  {s.error && (
                    <Typography variant="caption" color="error" sx={{ fontFamily: 'monospace' }}>
                      {s.error}
                    </Typography>
                  )}
                  {s.resolvedUrl && !s.error && (
                    <Typography
                      variant="caption"
                      sx={{
                        color: "text.secondary",
                        fontFamily: 'monospace',
                        wordBreak: 'break-all'
                      }}>
                      {s.resolvedUrl}
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}

interface FhirDiagProps {
  diag: FhirServerDiagnostics
}

function FhirServerSection({ diag }: FhirDiagProps) {
  const { t } = useTranslation('cds')
  return (
    <Alert severity={fhirServerSeverity(diag.status)} icon={false}>
      <AlertTitle>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Chip size="small" color={fhirServerSeverity(diag.status)} label={t(`debug.fhirStatus.${diag.status}`, { defaultValue: diag.status })} />
          <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
            {diag.url}
          </Typography>
        </Stack>
      </AlertTitle>
      {diag.errorCategory && (
        <Stack
          direction="row"
          spacing={1}
          sx={{
            alignItems: "center",
            mt: 0.5
          }}>
          <Chip size="small" variant="outlined" label={t(`debug.fhirCategory.${diag.errorCategory}`, { defaultValue: diag.errorCategory })} />
          {diag.httpStatus && <Chip size="small" variant="outlined" label={`HTTP ${diag.httpStatus}`} />}
          {diag.elapsedMs != null && <Typography variant="caption">{diag.elapsedMs}ms</Typography>}
        </Stack>
      )}
      {diag.errorMessage && (
        <Typography variant="body2" sx={{ mt: 1, fontFamily: 'monospace' }}>
          {diag.errorMessage}
        </Typography>
      )}
    </Alert>
  );
}

/**
 * CDS-specific debug panel. Thin orchestrator over the CDS-only context
 * sections (prefetch status, FHIR diagnostics, invocation context, dry-run)
 * plus the shared trace tables / error alert / ELM viewer from
 * components/debug/ (PAT-099 consolidation).
 */
export default function CdsDebugPanel({ debug }: Props) {
  const { t } = useTranslation('cds')
  const [contextOpen, setContextOpen] = useState(false)

  const { error, debugTrace, invocationContext, prefetchStatus, contextWarnings, fhirServerDiagnostics, dryRun, resourcesByType } = debug

  return (
    <Stack spacing={2} sx={{ mt: 2 }}>
      <Stack direction="row" spacing={1} sx={{
        alignItems: "center"
      }}>
        <DebugIcon fontSize="small" color="secondary" />
        <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'secondary.main' }}>
          {t('debug.title')}
        </Typography>
      </Stack>
      {error && <ExecutionErrorAlert errorInfo={error} />}
      {dryRun && (
        <Alert severity="warning" icon={false}>
          <AlertTitle>{t('debug.dryRunTitle')}</AlertTitle>
          <Typography variant="body2">{t('debug.dryRunDescription')}</Typography>
        </Alert>
      )}
      {resourcesByType && Object.keys(resourcesByType).length > 0 && (
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'secondary.main' }}>
            {t('debug.resourcesByType')}
          </Typography>
          <Stack direction="row" spacing={0.5} useFlexGap sx={{
            flexWrap: "wrap"
          }}>
            {Object.entries(resourcesByType).map(([type, count]) => (
              <Chip key={type} size="small" variant="outlined" label={`${type}: ${count}`} />
            ))}
          </Stack>
        </Box>
      )}
      {contextWarnings && contextWarnings.length > 0 && (
        <Alert severity="warning" icon={false}>
          <AlertTitle>{t('debug.contextWarnings')}</AlertTitle>
          <Box component="ul" sx={{ m: 0, pl: 2 }}>
            {contextWarnings.map((w, i) => (
              <li key={i}>
                <Typography variant="body2">{w}</Typography>
              </li>
            ))}
          </Box>
        </Alert>
      )}
      {fhirServerDiagnostics && <FhirServerSection diag={fhirServerDiagnostics} />}
      {prefetchStatus && prefetchStatus.length > 0 && <PrefetchSection statuses={prefetchStatus} />}
      {invocationContext && Object.keys(invocationContext).length > 0 && (
        <Box>
          <Stack direction="row" spacing={0.5} sx={{
            alignItems: "center"
          }}>
            <IconButton size="small" onClick={() => setContextOpen(!contextOpen)}>
              {contextOpen ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
            </IconButton>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>
              {t('debug.invocationContext')}
            </Typography>
          </Stack>
          <Collapse in={contextOpen} timeout="auto" unmountOnExit>
            <Paper variant="outlined" sx={{ p: 1, mt: 0.5, bgcolor: 'background.default' }}>
              <Box component="pre" sx={{ m: 0, fontSize: '0.75rem', overflowX: 'auto' }}>
                {JSON.stringify(invocationContext, null, 2)}
              </Box>
            </Paper>
          </Collapse>
        </Box>
      )}
      {debugTrace && debugTrace.expressionTraces.length > 0 && (
        <ExpressionTraceTable traces={debugTrace.expressionTraces} />
      )}
      {debugTrace && debugTrace.retrieveTraces.length > 0 && (
        <RetrieveTraceTable traces={debugTrace.retrieveTraces} />
      )}
      {debugTrace?.elmJson && <ElmJsonViewer elmJson={debugTrace.elmJson} />}
    </Stack>
  );
}
