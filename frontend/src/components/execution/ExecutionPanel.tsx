import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { alpha } from '@mui/material/styles'
import {
  Box,
  Paper,
  TextField,
  Typography,
  Stack,
  Divider,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Collapse,
  IconButton,
  FormControlLabel,
  Switch,
} from '@mui/material'
import {
  PlayArrow as PlayIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Timer as TimerIcon,
  BugReport as DebugIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useDispatch, useSelector } from 'react-redux'
import type { RootState } from '../../store'
import { setPatientId, setFhirServerUrl } from '../../store/executionSlice'
import { useExecute } from '../../hooks/useCql'
import { freshPrecompiledElm } from '../../utils/executionElm'
import DebugPanel from './DebugPanel'
import { usePreferences } from '../../hooks/usePreferences'
import FhirServerUrlField from '../common/FhirServerUrlField'

interface ExecutionPanelProps {
  /** Returns the latest CQL content from the editor, flushing to Redux if needed */
  getLatestCql?: () => string
}

export default function ExecutionPanel({ getLatestCql }: ExecutionPanelProps) {
  const { t } = useTranslation('editor')
  const dispatch = useDispatch()
  const cqlContentFromRedux = useSelector((state: RootState) => state.editor.cqlContent)
  const editorElmJson = useSelector((state: RootState) => state.editor.elmJson)
  const elmSourceCql = useSelector((state: RootState) => state.editor.elmSourceCql)
  const { patientId, fhirServerUrl, isExecuting, results, errors, warnings, executionTimeMs, debugTrace } = useSelector(
    (state: RootState) => state.execution
  )
  const { preferences } = usePreferences()

  // Set default FHIR server URL from preferences if not already set
  React.useEffect(() => {
    if (!fhirServerUrl && preferences.defaultFhirServerUrl) {
      dispatch(setFhirServerUrl(preferences.defaultFhirServerUrl))
    }
  }, [fhirServerUrl, preferences.defaultFhirServerUrl, dispatch])
  const executeMutation = useExecute()
  const [expandedResults, setExpandedResults] = useState<Set<string>>(new Set())
  const [debugMode, setDebugMode] = useState(false)

  const handleExecute = () => {
    const cql = getLatestCql ? getLatestCql() : cqlContentFromRedux
    // Reuse the already-translated ELM only when it matches this exact text, so
    // the backend skips re-translation. Any edit since the last translate makes
    // the strings differ → elmJson is undefined → backend translates. This guards
    // against executing stale ELM (wrong clinical results).
    const elmJson = freshPrecompiledElm(cql, editorElmJson, elmSourceCql)
    executeMutation.mutate({
      cql,
      patientId: patientId || undefined,
      fhirServerUrl: fhirServerUrl || undefined,
      debugMode,
      elmJson,
    })
  }

  const hasCql = !!(getLatestCql ? getLatestCql() : cqlContentFromRedux)

  const toggleExpanded = (name: string) => {
    const newExpanded = new Set(expandedResults)
    if (newExpanded.has(name)) {
      newExpanded.delete(name)
    } else {
      newExpanded.add(name)
    }
    setExpandedResults(newExpanded)
  }

  const renderValue = (value: unknown): React.ReactNode => {
    if (value === null || value === undefined) {
      return <Chip label={t('execution.nullValue')} size="small" sx={(theme) => ({ bgcolor: alpha(theme.palette.text.secondary, 0.1), color: 'text.secondary' })} />
    }
    if (typeof value === 'boolean') {
      return (
        <Chip
          label={value ? t('execution.trueValue') : t('execution.falseValue')}
          color={value ? 'success' : 'default'}
          size="small"
        />
      )
    }
    if (typeof value === 'number') {
      return <Typography variant="body2" sx={{ color: 'primary.dark', fontWeight: 600 }}>{value}</Typography>
    }
    if (typeof value === 'string') {
      return <Typography variant="body2" sx={{ color: 'text.primary' }}>"{value}"</Typography>
    }
    if (Array.isArray(value)) {
      return (
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          {t('execution.listItems', { count: value.length })}
        </Typography>
      );
    }
    if (typeof value === 'object') {
      return (
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          {JSON.stringify(value).substring(0, 50)}...
                  </Typography>
      );
    }
    return <Typography variant="body2">{String(value)}</Typography>
  }

  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="h6" gutterBottom>
        {t('execution.title')}
      </Typography>
      <Stack spacing={2}>
        <FhirServerUrlField
          value={fhirServerUrl}
          onChange={(value) => dispatch(setFhirServerUrl(value))}
          selfValidate
        />

        <TextField
          label={t('execution.patientId')}
          value={patientId}
          onChange={(e) => dispatch(setPatientId(e.target.value))}
          size="small"
          fullWidth
          placeholder={t('execution.patientIdPlaceholder')}
        />

        <Stack direction="row" spacing={2} sx={{
          alignItems: "center"
        }}>
          <GradientButton
            startIcon={isExecuting ? <CircularProgress size={20} color="inherit" /> : <PlayIcon />}
            onClick={handleExecute}
            disabled={isExecuting || !hasCql}
            fullWidth
            sx={{
              py: 1.2,
              fontSize: '0.95rem',
              '&.Mui-disabled': {
                background: 'rgba(0,0,0,0.12)',
              },
            }}
          >
            {isExecuting ? t('execution.executing') : t('execution.executeCql')}
          </GradientButton>
        </Stack>

        <FormControlLabel
          control={
            <Switch
              size="small"
              checked={debugMode}
              onChange={(e) => setDebugMode(e.target.checked)}
              sx={{
                '& .MuiSwitch-switchBase.Mui-checked': {
                  color: 'secondary.main',
                },
                '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': {
                  backgroundColor: 'secondary.main',
                },
              }}
            />
          }
          label={
            <Stack direction="row" spacing={0.5} sx={{
              alignItems: "center"
            }}>
              <DebugIcon sx={{ fontSize: 16, color: debugMode ? 'secondary.main' : 'text.secondary' }} />
              <Typography variant="body2" color={debugMode ? 'secondary.main' : 'text.secondary'}>
                {t('execution.debugMode')}
              </Typography>
            </Stack>
          }
        />

        {executionTimeMs !== null && (
          <Chip
            icon={<TimerIcon sx={{ fontSize: 16 }} />}
            label={`${executionTimeMs}ms`}
            size="small"
            sx={(theme) => ({
              alignSelf: 'flex-start',
              bgcolor: alpha(theme.palette.primary.main, 0.08),
              color: 'primary.dark',
              fontWeight: 600,
            })}
          />
        )}

        <Divider />

        {errors.length > 0 && (
          <Alert severity="error">
            {errors?.map((error, i) => (
              <div key={i}>{error}</div>
            ))}
          </Alert>
        )}

        {/* PAT-144: backend may report success=false with no specific errors;
            useCql now dispatches an empty errors array (instead of the previous
            hardcoded English 'Execution failed'), so the i18n fallback lives
            here at the render boundary. */}
        {errors.length === 0
          && executeMutation.data?.success === false && (
          <Alert severity="error">
            {t('execution.failedGeneric', 'Execution failed')}
          </Alert>
        )}

        {warnings.length > 0 && (
          <Alert severity="warning">
            <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
              {t('execution.warnings')}
            </Typography>
            {warnings.map((warning, i) => (
              <div key={i}>{warning}</div>
            ))}
          </Alert>
        )}

        {Object.keys(results).length > 0 && (
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              {t('execution.results')}
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell scope="col" width={40}></TableCell>
                    <TableCell scope="col">{t('execution.expression')}</TableCell>
                    <TableCell scope="col">{t('execution.type')}</TableCell>
                    <TableCell scope="col">{t('execution.value')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {Object.entries(results).map(([name, result]) => (
                    <React.Fragment key={name}>
                      <TableRow hover>
                        <TableCell>
                          {typeof result.value === 'object' && result.value !== null && (
                            <IconButton
                              size="small"
                              onClick={() => toggleExpanded(name)}
                              sx={{ color: 'primary.main' }}
                              aria-label={t('execution.toggleDetails')}
                            >
                              {expandedResults.has(name) ? (
                                <ExpandLessIcon />
                              ) : (
                                <ExpandMoreIcon />
                              )}
                            </IconButton>
                          )}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                            {name}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={result.valueType}
                            size="small"
                            variant="outlined"
                            sx={(theme) => ({
                              borderColor: alpha(theme.palette.secondary.main, 0.3),
                              color: 'secondary.main',
                              fontWeight: 500,
                            })}
                          />
                        </TableCell>
                        <TableCell>{renderValue(result.value)}</TableCell>
                      </TableRow>
                      {typeof result.value === 'object' && result.value !== null && (
                        <TableRow>
                          <TableCell colSpan={4} sx={{ py: 0 }}>
                            <Collapse in={expandedResults.has(name)}>
                              <Box
                                component="pre"
                                sx={{
                                  p: 2,
                                  bgcolor: 'action.hover',
                                  borderRadius: '8px',
                                  border: '1px solid',
                                  borderColor: 'divider',
                                  fontSize: '0.75rem',
                                  overflow: 'auto',
                                  maxHeight: 200,
                                  fontFamily: '"Consolas", monospace',
                                  color: 'text.primary',
                                }}
                              >
                                {JSON.stringify(result.value, null, 2)}
                              </Box>
                            </Collapse>
                          </TableCell>
                        </TableRow>
                      )}
                    </React.Fragment>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}

        {debugTrace && <DebugPanel trace={debugTrace} />}
      </Stack>
    </Paper>
  );
}
