import React, { useState, useEffect, useMemo } from 'react'
import { alpha } from '@mui/material/styles'
import { useTranslation } from 'react-i18next'
import { downloadBlob } from '../../utils/download'
import {
  Box,
  Typography,
  Stack,
  Button,
  IconButton,
  Alert,
  CircularProgress,
  Chip,
  Paper,
  Divider,
  Tooltip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  PlayArrow as RunIcon,
  PlaylistPlay as RunAllIcon,
  Edit as EditIcon,
  CheckCircle as PassIcon,
  Cancel as FailIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  ExpandMore as ExpandMoreIcon,
  Calculate as CalcIcon,
  FileDownload as ExportIcon,
  FileUpload as ImportIcon,
} from '@mui/icons-material'
import DebugModeSwitch from '../common/DebugModeSwitch'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import type { MeasureDefinition, TestCase, TestCaseRunResult } from '../../types'
import TestCaseEditor from './TestCaseEditor'
import TestCaseResultComponent from './TestCaseResult'
import DateCalculatorDialog from './DateCalculatorDialog'
import TestCaseCoverage from './TestCaseCoverage'
import TestCaseImportDialog from './TestCaseImportDialog'
import PopulationTracePanel from './PopulationTracePanel'
import DebugPanel from '../execution/DebugPanel'
import { saveEditingState, loadEditingState, clearEditingState } from '../../hooks/useTestCaseDraft'

interface TestCasesTabProps {
  measure: MeasureDefinition
  readOnly?: boolean
}

const STATUS_ICON: Record<string, React.ReactNode> = {
  pass: <PassIcon sx={{ fontSize: 16, color: 'success.main' }} />,
  fail: <FailIcon sx={{ fontSize: 16, color: 'error.main' }} />,
  error: <ErrorIcon sx={{ fontSize: 16, color: 'warning.main' }} />,
  pending: <PendingIcon sx={{ fontSize: 16, color: 'text.disabled' }} />,
}

export default function TestCasesTab({ measure, readOnly }: TestCasesTabProps) {
  const { t } = useTranslation('measures')
  const { t: tCommon } = useTranslation('common')
  const queryClient = useQueryClient()
  const { showNotification } = useNotification()
  const [editing, setEditingRaw] = useState<TestCase | null | 'new'>(null)
  const [runResults, setRunResults] = useState<TestCaseRunResult[]>([])
  const [dateCalcOpen, setDateCalcOpen] = useState(false)
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [debugMode, setDebugMode] = useState(false)

  const { data: testCases = [], isLoading } = useQuery({
    queryKey: ['test-cases', measure.id],
    queryFn: () => measureApi.getTestCases(measure.id!),
    enabled: !!measure.id,
  })

  // Wrap setEditing to persist to sessionStorage
  const setEditing = (val: TestCase | null | 'new') => {
    setEditingRaw(val)
    if (val === null) {
      clearEditingState(measure.id!)
    } else if (val === 'new') {
      saveEditingState(measure.id!, 'new')
    } else if (val.id) {
      saveEditingState(measure.id!, val.id)
    }
  }

  // Restore editing state from sessionStorage on remount (once only)
  const restoredRef = React.useRef(false)
  useEffect(() => {
    if (isLoading || restoredRef.current) return
    restoredRef.current = true
    const savedId = loadEditingState(measure.id!)
    if (savedId === null) return
    if (savedId === 'new') {
      setEditingRaw('new')
    } else {
      const found = testCases.find((tc) => tc.id === savedId)
      if (found) setEditingRaw(found)
      else clearEditingState(measure.id!) // stale reference
    }
  }, [measure.id, isLoading, testCases])

  const deleteMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.deleteTestCase(measure.id!, testCaseId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
    },
    onError: (err) => showNotification(tCommon('mutationErrors.deleteFailed', { error: extractApiError(err) }), 'error'),
  })

  const runOneMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.runTestCase(measure.id!, testCaseId, debugMode),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      setRunResults((prev) => {
        const filtered = prev.filter((r) => r.testCaseId !== result.testCaseId)
        return [...filtered, result]
      })
    },
    onError: (err) => showNotification(tCommon('mutationErrors.runFailed', { error: extractApiError(err) }), 'error'),
  })

  const runAllMutation = useMutation({
    mutationFn: () => measureApi.runAllTestCases(measure.id!, debugMode),
    onSuccess: (results) => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      setRunResults(results)
    },
    onError: (err) => showNotification(tCommon('mutationErrors.runFailed', { error: extractApiError(err) }), 'error'),
  })

  const toExportShape = (tc: TestCase) => ({
    title: tc.title,
    description: tc.description,
    series: tc.series,
    sortOrder: tc.sortOrder,
    expectedPopulations: tc.expectedPopulations,
    patientBundleJson: tc.patientBundleJson,
  })


  const exportSingleTestCase = (tc: TestCase) => {
    const blob = new Blob([JSON.stringify(toExportShape(tc), null, 2)], { type: 'application/json' })
    downloadBlob(blob, `${tc.title.replace(/[^a-z0-9]/gi, '_')}.json`)
  }

  const exportAllTestCases = () => {
    if (testCases.length === 0) return
    const blob = new Blob(
      [JSON.stringify(testCases.map(toExportShape), null, 2)],
      { type: 'application/json' }
    )
    downloadBlob(blob, `${measure.name || 'measure'}-test-cases.json`)
  }

  const { passCount, failCount, totalCount } = useMemo(() => {
    let pass = 0, fail = 0
    for (const tc of testCases) {
      if (tc.status === 'pass') pass++
      else if (tc.status === 'fail') fail++
    }
    return { passCount: pass, failCount: fail, totalCount: testCases.length }
  }, [testCases])

  const groupedTestCases = useMemo(() => {
    const groups = new Map<string, TestCase[]>()
    const ungrouped: TestCase[] = []
    for (const tc of testCases) {
      if (tc.series) {
        const list = groups.get(tc.series) || []
        list.push(tc)
        groups.set(tc.series, list)
      } else {
        ungrouped.push(tc)
      }
    }
    for (const [, list] of groups) {
      list.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    }
    return { groups, ungrouped }
  }, [testCases])

  const runResultMap = useMemo(() => {
    const map = new Map<number, TestCaseRunResult>()
    for (const r of runResults) map.set(r.testCaseId, r)
    return map
  }, [runResults])

  const renderTestCaseRow = (tc: TestCase) => {
    const result = runResultMap.get(tc.id!)
    return (
      <Paper key={tc.id} variant="outlined" sx={{ overflow: 'hidden' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ px: 2, py: 1 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flex: 1, minWidth: 0 }}>
            {STATUS_ICON[tc.status || 'pending']}
            <Typography variant="body2" fontWeight={500} noWrap>{tc.title}</Typography>
            {tc.series && <Chip label={tc.series} size="small" sx={{ height: 18, fontSize: '0.6rem' }} />}
            {tc.description && (
              <Typography variant="caption" color="text.secondary" noWrap sx={{ flex: 1 }}>{tc.description}</Typography>
            )}
          </Stack>
          <Stack direction="row" spacing={0.5} alignItems="center">
            {tc.expectedPopulations && (
              <Stack direction="row" spacing={0.25}>
                {Object.entries(tc.expectedPopulations).filter(([, v]) => v).map(([key]) => (
                  <Chip key={key} label={t(`testCaseEditor.populationTypesShort.${key}`, key.substring(0, 3))} size="small" sx={{ height: 18, fontSize: '0.6rem', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08) }} />
                ))}
              </Stack>
            )}
            <Tooltip title={debugMode ? t('testCases.tooltips.runTestCaseDebug') : t('testCases.tooltips.runTestCase')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.runTestCase')} onClick={() => runOneMutation.mutate(tc.id!)} disabled={runOneMutation.isPending}>
                {runOneMutation.isPending && runOneMutation.variables === tc.id ? <CircularProgress size={16} /> : <RunIcon fontSize="small" color={debugMode ? 'secondary' : 'inherit'} />}
              </IconButton>
            </Tooltip>
            <Tooltip title={t('testCases.tooltips.exportJson')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.exportJson')} onClick={() => exportSingleTestCase(tc)}><ExportIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title={t('testCases.tooltips.edit')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.edit')} onClick={() => setEditing(tc)}><EditIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title={t('testCases.tooltips.delete')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.delete')} color="error" onClick={() => deleteMutation.mutate(tc.id!)}><DeleteIcon fontSize="small" /></IconButton>
            </Tooltip>
          </Stack>
        </Stack>
        {result && (
          <>
            <Divider />
            <Box sx={{ px: 2, py: 1 }}>
              {result.phaseError && (
                <Alert severity="error" sx={{ mb: 1 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip
                      size="small"
                      color="error"
                      label={t(`populationTrace.phases.${result.phaseError.phase}`, { defaultValue: result.phaseError.phase })}
                    />
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {result.phaseError.message}
                    </Typography>
                  </Stack>
                </Alert>
              )}
              <TestCaseResultComponent result={result} />

              {result.populationTrace && (
                <Accordion defaultExpanded sx={{ mt: 1 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" fontWeight={600}>
                      {t('testCases.populationTraceTitle')}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <PopulationTracePanel trace={result.populationTrace} />
                  </AccordionDetails>
                </Accordion>
              )}

              {result.coverage && (
                <Accordion sx={{ mt: 0.5 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" fontWeight={600}>
                      {t('testCases.coverageTitle')}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TestCaseCoverage coverage={result.coverage} isLoading={false} />
                  </AccordionDetails>
                </Accordion>
              )}

              {result.debugTrace && (
                <Accordion sx={{ mt: 0.5 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" fontWeight={600}>
                      {t('testCases.debugPanelTitle')}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <DebugPanel trace={result.debugTrace} />
                  </AccordionDetails>
                </Accordion>
              )}
            </Box>
          </>
        )}
      </Paper>
    )
  }

  if (editing !== null) {
    return (
      <TestCaseEditor
        measure={measure}
        testCase={editing === 'new' ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={() => setEditing(null)}
        readOnly={readOnly}
      />
    )
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h6">{t('testCases.title')}</Typography>
          <HelpTooltip text={helpContent.measures.testCases} />
          {totalCount > 0 && (
            <Stack direction="row" spacing={0.5}>
              <Chip
                label={t('testCases.passCount', { pass: passCount, total: totalCount })}
                size="small"
                color={passCount === totalCount && totalCount > 0 ? 'success' : 'default'}
                sx={{ height: 22, fontSize: '0.75rem' }}
              />
              {failCount > 0 && (
                <Chip
                  label={t('testCases.failCount', { count: failCount })}
                  size="small"
                  color="error"
                  sx={{ height: 22, fontSize: '0.75rem' }}
                />
              )}
            </Stack>
          )}
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center">
          <DebugModeSwitch checked={debugMode} onChange={setDebugMode} label={t('testCases.debugMode')} />
          <Button
            size="small"
            startIcon={<CalcIcon />}
            onClick={() => setDateCalcOpen(true)}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.secondary.main, 0.3), color: 'secondary.main' }}
          >
            {t('testCases.dateCalculator')}
          </Button>
          <Button
            size="small"
            startIcon={<RunAllIcon />}
            onClick={() => runAllMutation.mutate()}
            disabled={testCases.length === 0 || runAllMutation.isPending}
            sx={{
              borderColor: (theme) => alpha(theme.palette.primary.main, 0.4),
              color: 'primary.dark',
            }}
            variant="outlined"
          >
            {runAllMutation.isPending ? t('testCases.running') : t('testCases.runAll')}
          </Button>
          <Button
            size="small"
            startIcon={<ExportIcon />}
            onClick={exportAllTestCases}
            disabled={testCases.length === 0}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.secondary.main, 0.3), color: 'secondary.main' }}
          >
            {t('testCases.exportAll')}
          </Button>
          <Button
            size="small"
            startIcon={<ImportIcon />}
            onClick={() => setImportDialogOpen(true)}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.primary.main, 0.4), color: 'primary.dark' }}
          >
            {t('testCases.import')}
          </Button>
          <GradientButton
            startIcon={<AddIcon />}
            onClick={() => setEditing('new')}
          >
            {t('testCases.addTestCase')}
          </GradientButton>
        </Stack>
      </Stack>

      {!measure.cqlContent && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('testCases.saveCqlFirst')}
        </Alert>
      )}

      {runAllMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {extractApiError(runAllMutation.error)}
        </Alert>
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : testCases.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary" gutterBottom>
            {t('testCases.emptyTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('testCases.emptyDescription')}
          </Typography>
          <Button
            startIcon={<AddIcon />}
            onClick={() => setEditing('new')}
            variant="outlined"
          >
            {t('testCases.createFirst')}
          </Button>
        </Paper>
      ) : (
        <Stack spacing={1.5}>
          {Array.from(groupedTestCases.groups.entries()).map(([series, tcs]) => (
            <Accordion key={series} defaultExpanded>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="subtitle2">{series}</Typography>
                  <Chip label={`${tcs.length}`} size="small" sx={{ height: 20 }} />
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 1 }}>
                <Stack spacing={1}>{tcs.map(renderTestCaseRow)}</Stack>
              </AccordionDetails>
            </Accordion>
          ))}
          {groupedTestCases.ungrouped.map(renderTestCaseRow)}
        </Stack>
      )}
      <DateCalculatorDialog open={dateCalcOpen} onClose={() => setDateCalcOpen(false)} />
      <TestCaseImportDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        measureId={measure.id!}
      />
    </Box>
  )
}
