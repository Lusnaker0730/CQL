import React, { useState, useMemo } from 'react'
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
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import GradientButton from '../common/GradientButton'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import type { MeasureDefinition, TestCase, TestCaseRunResult, CoverageResult } from '../../types'
import TestCaseEditor from './TestCaseEditor'
import TestCaseResultComponent from './TestCaseResult'
import DateCalculatorDialog from './DateCalculatorDialog'
import TestCaseCoverage from './TestCaseCoverage'

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
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<TestCase | null | 'new'>(null)
  const [runResults, setRunResults] = useState<TestCaseRunResult[]>([])
  const [dateCalcOpen, setDateCalcOpen] = useState(false)
  const [coverageData, setCoverageData] = useState<Record<number, { data: CoverageResult | null; loading: boolean }>>({})

  const { data: testCases = [], isLoading } = useQuery({
    queryKey: ['test-cases', measure.id],
    queryFn: () => measureApi.getTestCases(measure.id!),
    enabled: !!measure.id,
  })

  const deleteMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.deleteTestCase(measure.id!, testCaseId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
    },
  })

  const runOneMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.runTestCase(measure.id!, testCaseId),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      setRunResults((prev) => {
        const filtered = prev.filter((r) => r.testCaseId !== result.testCaseId)
        return [...filtered, result]
      })
    },
  })

  const runAllMutation = useMutation({
    mutationFn: () => measureApi.runAllTestCases(measure.id!),
    onSuccess: (results) => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      setRunResults(results)
    },
  })

  const coverageMutation = useMutation({
    mutationFn: (testCaseId: number) => measureApi.runWithCoverage(measure.id!, testCaseId),
    onMutate: (testCaseId) => {
      setCoverageData(prev => ({ ...prev, [testCaseId]: { data: null, loading: true } }))
    },
    onSuccess: (data, testCaseId) => {
      setCoverageData(prev => ({ ...prev, [testCaseId]: { data, loading: false } }))
    },
    onError: (_, testCaseId) => {
      setCoverageData(prev => ({ ...prev, [testCaseId]: { data: null, loading: false } }))
    },
  })

  const importRef = React.useRef<HTMLInputElement>(null)

  const exportSingleTestCase = (tc: TestCase) => {
    const exportData = {
      title: tc.title,
      description: tc.description,
      series: tc.series,
      expectedPopulations: tc.expectedPopulations,
      patientBundleJson: tc.patientBundleJson,
    }
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${tc.title.replace(/[^a-z0-9]/gi, '_')}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const exportAllTestCases = () => {
    if (testCases.length === 0) return
    const exportData = testCases.map((tc) => ({
      title: tc.title,
      description: tc.description,
      series: tc.series,
      expectedPopulations: tc.expectedPopulations,
      patientBundleJson: tc.patientBundleJson,
    }))
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${measure.name || 'measure'}-test-cases.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const importMutation = useMutation({
    mutationFn: async (file: File) => {
      const text = await file.text()
      const parsed = JSON.parse(text)
      const rawItems: unknown[] = Array.isArray(parsed) ? parsed : [parsed]
      const results: TestCase[] = []
      for (const raw of rawItems) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const item = raw as any
        // Support MADiE format: detect by checking for 'json' or 'patientBundleJson' field
        const bundleJson = item.patientBundleJson || item.json
          || (item.resourceType === 'Bundle' ? JSON.stringify(item) : undefined)
        const tc: TestCase = {
          title: item.title || item.name || file.name.replace(/\.json$/, ''),
          description: item.description || '',
          series: item.series || item.groupName || undefined,
          expectedPopulations: item.expectedPopulations || item.groupPopulationValues?.reduce(
            (acc: Record<string, boolean>, p: { name: string; expected: boolean }) => {
              acc[p.name] = p.expected
              return acc
            }, {}
          ) || {},
          patientBundleJson: typeof bundleJson === 'string' ? bundleJson : JSON.stringify(bundleJson),
        }
        results.push(await measureApi.createTestCase(measure.id!, tc))
      }
      return results
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      if (importRef.current) importRef.current.value = ''
    },
  })

  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) importMutation.mutate(file)
  }

  const passCount = testCases.filter((tc) => tc.status === 'pass').length
  const failCount = testCases.filter((tc) => tc.status === 'fail').length
  const totalCount = testCases.length

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

  const renderTestCaseRow = (tc: TestCase) => {
    const result = runResults.find((r) => r.testCaseId === tc.id)
    const coverage = coverageData[tc.id!]
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
                  <Chip key={key} label={key.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()).substring(0, 3)} size="small" sx={{ height: 18, fontSize: '0.6rem', bgcolor: 'rgba(13,115,119,0.08)' }} />
                ))}
              </Stack>
            )}
            <Tooltip title="Run with coverage">
              <IconButton size="small" aria-label="Run with coverage" onClick={() => coverageMutation.mutate(tc.id!)} disabled={coverageMutation.isPending}>
                <RunIcon fontSize="small" color="secondary" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Run this test case">
              <IconButton size="small" aria-label="Run test case" onClick={() => runOneMutation.mutate(tc.id!)} disabled={runOneMutation.isPending}>
                {runOneMutation.isPending && runOneMutation.variables === tc.id ? <CircularProgress size={16} /> : <RunIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Export JSON">
              <IconButton size="small" aria-label="Export test case JSON" onClick={() => exportSingleTestCase(tc)}><ExportIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title="Edit">
              <IconButton size="small" aria-label="Edit test case" onClick={() => setEditing(tc)}><EditIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Tooltip title="Delete">
              <IconButton size="small" aria-label="Delete test case" color="error" onClick={() => deleteMutation.mutate(tc.id!)}><DeleteIcon fontSize="small" /></IconButton>
            </Tooltip>
          </Stack>
        </Stack>
        {result && (
          <>
            <Divider />
            <Box sx={{ px: 1, py: 0.5 }}><TestCaseResultComponent result={result} /></Box>
          </>
        )}
        {coverage && (
          <>
            <Divider />
            <Box sx={{ px: 2, py: 1 }}><TestCaseCoverage coverage={coverage.data} isLoading={coverage.loading} /></Box>
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
          <Typography variant="h6">Test Cases</Typography>
          <HelpTooltip text={helpContent.measures.testCases} />
          {totalCount > 0 && (
            <Stack direction="row" spacing={0.5}>
              <Chip
                label={`${passCount}/${totalCount} pass`}
                size="small"
                color={passCount === totalCount && totalCount > 0 ? 'success' : 'default'}
                sx={{ height: 22, fontSize: '0.75rem' }}
              />
              {failCount > 0 && (
                <Chip
                  label={`${failCount} fail`}
                  size="small"
                  color="error"
                  sx={{ height: 22, fontSize: '0.75rem' }}
                />
              )}
            </Stack>
          )}
        </Stack>
        <Stack direction="row" spacing={1}>
          <Button
            size="small"
            startIcon={<CalcIcon />}
            onClick={() => setDateCalcOpen(true)}
            variant="outlined"
            sx={{ borderColor: 'rgba(27,58,92,0.3)', color: 'secondary.main' }}
          >
            Date Calculator
          </Button>
          <Button
            size="small"
            startIcon={<RunAllIcon />}
            onClick={() => runAllMutation.mutate()}
            disabled={testCases.length === 0 || runAllMutation.isPending}
            sx={{
              borderColor: 'rgba(13,115,119,0.4)',
              color: 'primary.dark',
            }}
            variant="outlined"
          >
            {runAllMutation.isPending ? 'Running...' : 'Run All'}
          </Button>
          <Button
            size="small"
            startIcon={<ExportIcon />}
            onClick={exportAllTestCases}
            disabled={testCases.length === 0}
            variant="outlined"
            sx={{ borderColor: 'rgba(27,58,92,0.3)', color: 'secondary.main' }}
          >
            Export All
          </Button>
          <Button
            size="small"
            startIcon={<ImportIcon />}
            onClick={() => importRef.current?.click()}
            variant="outlined"
            disabled={importMutation.isPending}
            sx={{ borderColor: 'rgba(13,115,119,0.4)', color: 'primary.dark' }}
          >
            {importMutation.isPending ? 'Importing...' : 'Import'}
          </Button>
          <input ref={importRef} type="file" accept=".json" hidden onChange={handleFileImport} />
          <GradientButton
            startIcon={<AddIcon />}
            onClick={() => setEditing('new')}
          >
            Add Test Case
          </GradientButton>
        </Stack>
      </Stack>

      {!measure.cqlContent && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Save CQL content in the CQL tab first before running test cases.
        </Alert>
      )}

      {runAllMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(runAllMutation.error as Error).message}
        </Alert>
      )}

      {importMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Import failed: {(importMutation.error as Error).message}
        </Alert>
      )}

      {importMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Test cases imported successfully.
        </Alert>
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : testCases.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary" gutterBottom>
            No test cases yet
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Create test cases with expected population outcomes to validate your measure logic.
          </Typography>
          <Button
            startIcon={<AddIcon />}
            onClick={() => setEditing('new')}
            variant="outlined"
          >
            Create First Test Case
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
    </Box>
  )
}
