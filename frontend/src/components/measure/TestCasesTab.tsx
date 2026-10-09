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
  FormControlLabel,
  Switch,
  Menu,
  MenuItem,
  ListItemText,
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
  EventRepeat as ShiftDatesIcon,
  Lock as LockIcon,
  LockOpen as LockOpenIcon,
} from '@mui/icons-material'
import DebugModeSwitch from '../common/DebugModeSwitch'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import type { MeasureDefinition, TestCase, TestCaseRunResult, MeasureClauseCoverage } from '../../types'
import { episodeBasisByGroup } from '../../utils/testCaseExpectedValues'
import TestCaseEditor from './TestCaseEditor'
import TestCaseResultComponent from './TestCaseResult'
import DateCalculatorDialog from './DateCalculatorDialog'
import TestCaseCoverage from './TestCaseCoverage'
import ClauseCoverageView from './ClauseCoverageView'
import TestCaseImportDialog from './TestCaseImportDialog'
import TestCaseValidationBadge from './TestCaseValidationBadge'
import TestCaseCopyDialog from './TestCaseCopyDialog'
import TestCaseShiftDatesDialog from './TestCaseShiftDatesDialog'
import PopulationTracePanel from './PopulationTracePanel'
import DebugPanel from '../execution/DebugPanel'
import { saveEditingState, loadEditingState, clearEditingState } from '../../hooks/useTestCaseDraft'
import { getStoredUsername } from '../../utils/validation'

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
  // PAT-246: copy test cases to another measure / version
  const [copyDialogOpen, setCopyDialogOpen] = useState(false)
  // PAT-247: "Export" offers the platform JSON and the MADiE-compatible zip of FHIR bundles
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null)
  // PAT-248: shift the dates of one test case or of all of them by whole years
  const [shiftTarget, setShiftTarget] = useState<TestCase | 'all' | null>(null)
  const [debugMode, setDebugMode] = useState(false)
  // PAT-245: "Run all" can leave out test cases whose FHIR validation found errors.
  const [skipInvalid, setSkipInvalid] = useState(false)
  // PAT-243: episode-based groups report episode counts; the result rows must not read them as Yes / No.
  const episodeGroups = useMemo(() => episodeBasisByGroup(measure), [measure])
  // PAT-242: test cases run in the measure's Measurement Period when it has one, else the current year.
  const measurePeriod = measure.measurementPeriodStart && measure.measurementPeriodEnd
    ? { start: measure.measurementPeriodStart, end: measure.measurementPeriodEnd }
    : null
  const [measureCoverage, setMeasureCoverage] = useState<MeasureClauseCoverage | null>(null)

  const { data: testCases = [], isLoading } = useQuery({
    queryKey: ['test-cases', measure.id],
    queryFn: () => measureApi.getTestCases(measure.id!),
    enabled: !!measure.id,
    // PAT-245: validation runs in the background after a save — poll while any case is pending.
    refetchInterval: (query) =>
      (query.state.data ?? []).some((tc) => tc.validationStatus === 'pending') ? 3000 : false,
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

  // PAT-253: edit lock — only the holder may edit / delete / shift a locked case until the lock is
  // released or expires; the server refuses everyone else with 409 Locked, the UI just mirrors it.
  const currentUser = useMemo(() => getStoredUsername(), [])
  const lockMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.lockTestCase(measure.id!, testCaseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] }),
    onError: (err) => showNotification(t('testCases.lock.lockFailed', { error: extractApiError(err) }), 'error'),
  })
  const unlockMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.unlockTestCase(measure.id!, testCaseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] }),
    onError: (err) => showNotification(t('testCases.lock.unlockFailed', { error: extractApiError(err) }), 'error'),
  })
  const lockedByOther = (tc: TestCase) => !!tc.lockedBy && tc.lockedBy !== currentUser

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

  // PAT-232: clause coverage over ALL test cases (a clause counts when any test case reached it).
  const coverageMutation = useMutation({
    mutationFn: () => measureApi.getMeasureClauseCoverage(measure.id!),
    onSuccess: (data) => {
      setMeasureCoverage(data)
      void queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
    },
  })

  // PAT-245: FHIR validation of the patient bundles
  const validateAllMutation = useMutation({
    mutationFn: () => measureApi.validateAllTestCases(measure.id!),
    onSuccess: (data) => {
      showNotification(t('testCases.validation.validateAllQueued', { count: data.scheduled }), 'info')
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
    },
    onError: (err) => showNotification(extractApiError(err), 'error'),
  })
  const revalidateMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.validateTestCase(measure.id!, testCaseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] }),
    onError: (err) => showNotification(extractApiError(err), 'error'),
  })
  const invalidCount = useMemo(() => testCases.filter((tc) => tc.validationStatus === 'invalid').length, [testCases])

  const runAllMutation = useMutation({
    mutationFn: () => measureApi.runAllTestCases(measure.id!, debugMode, skipInvalid),
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

  // PAT-247: the server builds the MADiE-compatible zip (bundle + test-case MeasureReport per case).
  const exportZipMutation = useMutation({
    mutationFn: () => measureApi.exportTestCasesZip(measure.id!),
    onSuccess: (blob) => downloadBlob(blob, `${measure.name || 'measure'}-test-cases.zip`),
    onError: (err) => showNotification(t('testCases.exportMenu.zipFailed', { error: extractApiError(err) }), 'error'),
  })

  // PAT-248: the suite as a workbook (expected next to actual, mismatches highlighted).
  const exportExcelMutation = useMutation({
    mutationFn: () => measureApi.exportTestCasesExcel(measure.id!),
    onSuccess: (blob) => downloadBlob(blob, `${measure.name || 'measure'}-test-cases.xlsx`),
    onError: (err) => showNotification(t('testCases.exportMenu.zipFailed', { error: extractApiError(err) }), 'error'),
  })

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
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            px: 2,
            py: 1
          }}>
          <Stack
            direction="row"
            spacing={1}
            sx={{
              alignItems: "center",
              flex: 1,
              minWidth: 0
            }}>
            {STATUS_ICON[tc.status || 'pending']}
            <Typography variant="body2" noWrap sx={{
              fontWeight: 500
            }}>{tc.title}</Typography>
            {tc.series && <Chip label={tc.series} size="small" sx={{ height: 18, fontSize: '0.6rem' }} />}
            <TestCaseValidationBadge testCase={tc} compact />
            {tc.description && (
              <Typography
                variant="caption"
                noWrap
                sx={{
                  color: "text.secondary",
                  flex: 1
                }}>{tc.description}</Typography>
            )}
          </Stack>
          <Stack direction="row" spacing={0.5} sx={{
            alignItems: "center"
          }}>
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
            {lockedByOther(tc) ? (
              <Tooltip title={t('testCases.lock.lockedUntil', { user: tc.lockedBy, until: tc.lockExpiresAt ? new Date(tc.lockExpiresAt).toLocaleString() : '' })}>
                <Chip icon={<LockIcon />} label={t('testCases.lock.lockedBy', { user: tc.lockedBy })} size="small" color="warning" variant="outlined" data-testid={`test-case-lock-${tc.id}`} />
              </Tooltip>
            ) : tc.lockedBy ? (
              <Tooltip title={t('testCases.lock.unlock')}>
                <IconButton size="small" aria-label={t('testCases.ariaLabels.unlock')} color="warning" onClick={() => unlockMutation.mutate(tc.id!)} disabled={unlockMutation.isPending}><LockOpenIcon fontSize="small" /></IconButton>
              </Tooltip>
            ) : (
              <Tooltip title={t('testCases.lock.lock')}>
                <IconButton size="small" aria-label={t('testCases.ariaLabels.lock')} onClick={() => lockMutation.mutate(tc.id!)} disabled={readOnly || lockMutation.isPending}><LockIcon fontSize="small" /></IconButton>
              </Tooltip>
            )}
            <Tooltip title={t('testCases.tooltips.shiftDates')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.shiftDates')} onClick={() => setShiftTarget(tc)} disabled={readOnly || lockedByOther(tc)}><ShiftDatesIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title={t('testCases.tooltips.edit')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.edit')} onClick={() => setEditing(tc)}><EditIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title={t('testCases.tooltips.delete')}>
              <IconButton size="small" aria-label={t('testCases.ariaLabels.delete')} color="error" onClick={() => deleteMutation.mutate(tc.id!)} disabled={lockedByOther(tc)}><DeleteIcon fontSize="small" /></IconButton>
            </Tooltip>
          </Stack>
        </Stack>
        {(tc.validationStatus === 'invalid' || tc.validationStatus === 'error') && (
          <Box sx={{ px: 2, pb: 1 }}>
            <TestCaseValidationBadge
              testCase={tc}
              onRevalidate={() => revalidateMutation.mutate(tc.id!)}
              revalidating={revalidateMutation.isPending && revalidateMutation.variables === tc.id}
            />
          </Box>
        )}
        {result && (
          <>
            <Divider />
            <Box sx={{ px: 2, py: 1 }}>
              {result.phaseError && (
                <Alert severity="error" sx={{ mb: 1 }}>
                  <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                  }}>
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
              <TestCaseResultComponent result={result} episodeBasisByGroup={episodeGroups} />

              {result.populationTrace && (
                <Accordion defaultExpanded sx={{ mt: 1 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" sx={{
                      fontWeight: 600
                    }}>
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
                    <Typography variant="body2" sx={{
                      fontWeight: 600
                    }}>
                      {t('testCases.coverageTitle')}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TestCaseCoverage coverage={result.coverage} isLoading={false} />
                  </AccordionDetails>
                </Accordion>
              )}

              {result.clauseCoverage && (
                <Accordion sx={{ mt: 0.5 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {t('testCases.clauseCoverage.title')} · {result.clauseCoverage.percent.toFixed(1)}%
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <ClauseCoverageView coverage={result.clauseCoverage} />
                  </AccordionDetails>
                </Accordion>
              )}

              {result.debugTrace && (
                <Accordion sx={{ mt: 0.5 }}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="body2" sx={{
                      fontWeight: 600
                    }}>
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
    );
  }

  if (editing !== null) {
    return (
      <TestCaseEditor
        measure={measure}
        testCase={editing === 'new' ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={() => setEditing(null)}
        readOnly={readOnly || (editing !== 'new' && lockedByOther(editing))}
        lockedByOther={editing !== 'new' && lockedByOther(editing) ? editing.lockedBy : undefined}
      />
    )
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2
        }}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <Typography variant="h6">{t('testCases.title')}</Typography>
          <HelpTooltip text={helpContent.measures.testCases} />
          <Typography variant="caption" data-testid="test-cases-measurement-period" sx={{ color: 'text.secondary' }}>
            {measurePeriod
              ? t('testCases.measurementPeriod.measure', measurePeriod)
              : t('testCases.measurementPeriod.currentYear', { year: new Date().getFullYear() })}
          </Typography>
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
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <DebugModeSwitch checked={debugMode} onChange={setDebugMode} label={t('testCases.debugMode')} />
          <Tooltip title={t('testCases.validation.skipInvalidHint')}>
            <FormControlLabel
              sx={{ mr: 0 }}
              control={<Switch size="small" checked={skipInvalid} onChange={(e) => setSkipInvalid(e.target.checked)} />}
              label={<Typography variant="body2" color="text.secondary">{t('testCases.validation.skipInvalid', { count: invalidCount })}</Typography>}
            />
          </Tooltip>
          <Button
            size="small"
            onClick={() => validateAllMutation.mutate()}
            disabled={testCases.length === 0 || validateAllMutation.isPending || readOnly}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.primary.main, 0.4), color: 'primary.dark' }}
          >
            {t('testCases.validation.validateAll')}
          </Button>
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
            startIcon={<ShiftDatesIcon />}
            onClick={() => setShiftTarget('all')}
            disabled={testCases.length === 0 || readOnly}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.secondary.main, 0.3), color: 'secondary.main' }}
          >
            {t('testCases.shiftDates.button')}
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
          <Tooltip title={t('testCases.clauseCoverage.tooltip')}>
            <span>
              <Button
                size="small"
                onClick={() => coverageMutation.mutate()}
                disabled={testCases.length === 0 || coverageMutation.isPending}
                sx={{ borderColor: (theme) => alpha(theme.palette.primary.main, 0.4), color: 'primary.dark' }}
                variant="outlined"
              >
                {coverageMutation.isPending ? t('testCases.running') : t('testCases.clauseCoverage.button')}
              </Button>
            </span>
          </Tooltip>
          <Button
            size="small"
            startIcon={<ExportIcon />}
            endIcon={<ExpandMoreIcon fontSize="small" />}
            onClick={(e) => setExportAnchor(e.currentTarget)}
            disabled={testCases.length === 0 || exportZipMutation.isPending || exportExcelMutation.isPending}
            variant="outlined"
            aria-haspopup="menu"
            sx={{ borderColor: (theme) => alpha(theme.palette.secondary.main, 0.3), color: 'secondary.main' }}
          >
            {t('testCases.exportAll')}
          </Button>
          <Menu anchorEl={exportAnchor} open={Boolean(exportAnchor)} onClose={() => setExportAnchor(null)}>
            <MenuItem onClick={() => { setExportAnchor(null); exportAllTestCases() }}>
              <ListItemText primary={t('testCases.exportMenu.json')} secondary={t('testCases.exportMenu.jsonHint')} />
            </MenuItem>
            <MenuItem onClick={() => { setExportAnchor(null); exportZipMutation.mutate() }}>
              <ListItemText primary={t('testCases.exportMenu.madie')} secondary={t('testCases.exportMenu.madieHint')} />
            </MenuItem>
            <MenuItem onClick={() => { setExportAnchor(null); exportExcelMutation.mutate() }}>
              <ListItemText primary={t('testCases.exportMenu.excel')} secondary={t('testCases.exportMenu.excelHint')} />
            </MenuItem>
          </Menu>
          <Button
            size="small"
            startIcon={<ImportIcon />}
            onClick={() => setImportDialogOpen(true)}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.primary.main, 0.4), color: 'primary.dark' }}
          >
            {t('testCases.import')}
          </Button>
          <Button
            size="small"
            onClick={() => setCopyDialogOpen(true)}
            disabled={testCases.length === 0}
            variant="outlined"
            sx={{ borderColor: (theme) => alpha(theme.palette.primary.main, 0.4), color: 'primary.dark' }}
          >
            {t('testCases.copyDialog.button')}
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
      {coverageMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {extractApiError(coverageMutation.error)}
        </Alert>
      )}
      {measureCoverage && (
        <Accordion defaultExpanded sx={{ mb: 2 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {t('testCases.clauseCoverage.measureTitle')}
              {measureCoverage.coverage ? ` · ${measureCoverage.coverage.percent.toFixed(1)}%` : ''}
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            {measureCoverage.coverage ? (
              <ClauseCoverageView
                coverage={measureCoverage.coverage}
                subtitle={t('testCases.clauseCoverage.measureSubtitle', {
                  executed: measureCoverage.executed,
                  total: measureCoverage.testCases,
                  passed: measureCoverage.passed,
                })}
              />
            ) : (
              <Alert severity="warning">
                {t('testCases.clauseCoverage.noneExecuted', { total: measureCoverage.testCases })}
              </Alert>
            )}
          </AccordionDetails>
        </Accordion>
      )}
      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : testCases.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography gutterBottom sx={{
            color: "text.secondary"
          }}>
            {t('testCases.emptyTitle')}
          </Typography>
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              mb: 2
            }}>
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
                <Stack direction="row" spacing={1} sx={{
                  alignItems: "center"
                }}>
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
      <TestCaseShiftDatesDialog
        open={shiftTarget !== null}
        onClose={() => setShiftTarget(null)}
        measure={measure}
        target={shiftTarget}
        count={testCases.length}
      />
      <TestCaseImportDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        measureId={measure.id!}
      />
      {copyDialogOpen && (
        <TestCaseCopyDialog open onClose={() => setCopyDialogOpen(false)} measure={measure} testCases={testCases} />
      )}
    </Box>
  );
}
