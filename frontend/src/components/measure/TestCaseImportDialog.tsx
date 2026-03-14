import { useState, useCallback, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Stack,
  Typography, TextField, Alert, CircularProgress, Chip, Switch, FormControlLabel,
  LinearProgress, Paper, IconButton, Tooltip, Divider,
} from '@mui/material'
import {
  CloudUpload as UploadIcon,
  Delete as DeleteIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
} from '@mui/icons-material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import GradientButton from '../common/GradientButton'
import type { TestCase, BatchTestCaseImportResult } from '../../types'
import { extractApiError } from '../../utils/errorUtils'

interface TestCaseImportDialogProps {
  open: boolean
  onClose: () => void
  measureId: number
}

interface ParsedTestCase {
  title: string
  description: string
  series?: string
  expectedPopulations: Record<string, boolean>
  patientBundleJson: string
}

export default function TestCaseImportDialog({ open, onClose, measureId }: TestCaseImportDialogProps) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [parsedCases, setParsedCases] = useState<ParsedTestCase[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string | null>(null)
  const [dateShiftDays, setDateShiftDays] = useState(0)
  const [dateShiftEnabled, setDateShiftEnabled] = useState(false)
  const [result, setResult] = useState<BatchTestCaseImportResult | null>(null)

  const importMutation = useMutation({
    mutationFn: async () => {
      const testCases: TestCase[] = parsedCases.map((pc) => ({
        title: pc.title,
        description: pc.description,
        series: pc.series,
        expectedPopulations: pc.expectedPopulations,
        patientBundleJson: pc.patientBundleJson,
      }))
      return measureApi.batchImportTestCases(
        measureId,
        testCases,
        dateShiftEnabled ? dateShiftDays : 0
      )
    },
    onSuccess: (data) => {
      setResult(data)
      queryClient.invalidateQueries({ queryKey: ['test-cases', measureId] })
    },
  })

  const parseFile = useCallback(async (file: File) => {
    setParseError(null)
    setParsedCases([])
    setResult(null)
    setFileName(file.name)

    try {
      const text = await file.text()
      const parsed = JSON.parse(text)
      const rawItems: unknown[] = Array.isArray(parsed) ? parsed : [parsed]
      const cases: ParsedTestCase[] = []

      for (const raw of rawItems) {
        const item = raw as Record<string, unknown>

        // Support multiple formats
        const bundleJson = (item.patientBundleJson as string | undefined)
          || (item.json as string | undefined)
          || (item.resourceType === 'Bundle' ? JSON.stringify(item) : undefined)

        if (!bundleJson) {
          // Skip items without patient data
          continue
        }

        // MADiE format: groupPopulationValues
        const groupPopValues = item.groupPopulationValues as Array<{ name: string; expected: boolean }> | undefined
        const expectedPops = (item.expectedPopulations as Record<string, boolean>)
          || groupPopValues?.reduce(
            (acc: Record<string, boolean>, p: { name: string; expected: boolean }) => {
              acc[p.name] = p.expected
              return acc
            }, {}
          ) || {}

        cases.push({
          title: (item.title as string) || (item.name as string) || file.name.replace(/\.json$/, ''),
          description: (item.description as string) || '',
          series: (item.series as string) || (item.groupName as string) || undefined,
          expectedPopulations: expectedPops,
          patientBundleJson: typeof bundleJson === 'string' ? bundleJson : JSON.stringify(bundleJson),
        })
      }

      if (cases.length === 0) {
        setParseError(t('importDialog.noValidCases'))
      } else {
        setParsedCases(cases)
      }
    } catch (e) {
      setParseError(t('importDialog.parseError', { error: extractApiError(e) }))
    }
  }, [t])

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) parseFile(file)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const file = e.dataTransfer.files[0]
    if (file && (file.name.endsWith('.json') || file.name.endsWith('.ndjson'))) {
      parseFile(file)
    }
  }, [parseFile])

  const handleRemoveCase = (index: number) => {
    setParsedCases((prev) => prev.filter((_, i) => i !== index))
  }

  const handleClose = () => {
    setParsedCases([])
    setParseError(null)
    setFileName(null)
    setResult(null)
    setDateShiftDays(0)
    setDateShiftEnabled(false)
    importMutation.reset()
    onClose()
  }


  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('importDialog.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {/* Step 1: File Selection */}
          {!result && (
            <Box
              onDrop={handleDrop}
              onDragOver={(e) => e.preventDefault()}
              sx={{
                border: '2px dashed',
                borderColor: fileName ? 'primary.main' : 'divider',
                borderRadius: 2,
                p: 3,
                textAlign: 'center',
                cursor: 'pointer',
                transition: 'border-color 0.2s',
                '&:hover': { borderColor: 'primary.main' },
              }}
              onClick={() => fileInputRef.current?.click()}
            >
              <UploadIcon sx={{ fontSize: 40, mb: 1, opacity: 0.5, color: 'primary.main' }} />
              <Typography variant="body2" color="text.secondary">
                {t('importDialog.dropHint')}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                {t('importDialog.formatHint')}
              </Typography>
              <input
                ref={fileInputRef}
                type="file"
                accept=".json,.ndjson"
                style={{ display: 'none' }}
                onChange={handleFileSelect}
              />
            </Box>
          )}

          {parseError && (
            <Alert severity="error">{parseError}</Alert>
          )}

          {/* Step 2: Preview */}
          {parsedCases.length > 0 && !result && (
            <>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle2">
                  {t('importDialog.preview', { count: parsedCases.length, file: fileName })}
                </Typography>
              </Stack>

              <Paper variant="outlined" sx={{ maxHeight: 250, overflow: 'auto' }}>
                {parsedCases.map((tc, index) => (
                  <Stack
                    key={index}
                    direction="row"
                    alignItems="center"
                    sx={{ px: 2, py: 0.75, borderBottom: '1px solid', borderColor: 'divider', '&:last-child': { borderBottom: 0 } }}
                  >
                    <Typography variant="body2" sx={{ flex: 1, fontWeight: 500 }} noWrap>
                      {tc.title}
                    </Typography>
                    {tc.series && <Chip label={tc.series} size="small" sx={{ height: 18, fontSize: '0.65rem', mr: 1 }} />}
                    {Object.entries(tc.expectedPopulations).filter(([, v]) => v).length > 0 && (
                      <Chip
                        label={`${Object.entries(tc.expectedPopulations).filter(([, v]) => v).length} pops`}
                        size="small"
                        variant="outlined"
                        sx={{ height: 18, fontSize: '0.65rem', mr: 1 }}
                      />
                    )}
                    <Tooltip title={t('importDialog.removeFromImport')}>
                      <IconButton size="small" onClick={() => handleRemoveCase(index)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ))}
              </Paper>

              <Divider />

              {/* Date Shifting */}
              <Stack spacing={1}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={dateShiftEnabled}
                      onChange={(_, checked) => setDateShiftEnabled(checked)}
                    />
                  }
                  label={
                    <Typography variant="body2">{t('importDialog.enableDateShift')}</Typography>
                  }
                />
                {dateShiftEnabled && (
                  <Stack direction="row" spacing={2} alignItems="center" sx={{ pl: 4 }}>
                    <TextField
                      type="number"
                      size="small"
                      label={t('importDialog.shiftDays')}
                      value={dateShiftDays}
                      onChange={(e) => setDateShiftDays(parseInt(e.target.value) || 0)}
                      sx={{ width: 150 }}
                      helperText={t('importDialog.shiftDaysHelp')}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 300 }}>
                      {t('importDialog.shiftExplanation')}
                    </Typography>
                  </Stack>
                )}
              </Stack>
            </>
          )}

          {/* Progress */}
          {importMutation.isPending && (
            <Box>
              <LinearProgress variant="indeterminate" />
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {t('importDialog.importing', { count: parsedCases.length })}
              </Typography>
            </Box>
          )}

          {/* Error */}
          {importMutation.isError && (
            <Alert severity="error">
              {t('importDialog.importError', { error: extractApiError(importMutation.error) })}
            </Alert>
          )}

          {/* Result */}
          {result && (
            <Stack spacing={1.5}>
              <Alert
                severity={result.failureCount === 0 ? 'success' : 'warning'}
                icon={result.failureCount === 0 ? <SuccessIcon /> : <ErrorIcon />}
              >
                <Typography variant="subtitle2">
                  {t('importDialog.resultSummary', {
                    success: result.successCount,
                    total: result.totalReceived,
                  })}
                </Typography>
              </Alert>
              {result.errors.length > 0 && (
                <Paper variant="outlined" sx={{ p: 1.5, maxHeight: 150, overflow: 'auto' }}>
                  {result.errors.map((err, i) => (
                    <Typography key={i} variant="caption" color="error" display="block" sx={{ fontFamily: 'monospace' }}>
                      {err}
                    </Typography>
                  ))}
                </Paper>
              )}
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>
          {result ? t('importDialog.done') : t('importDialog.cancel')}
        </Button>
        {!result && parsedCases.length > 0 && (
          <GradientButton
            onClick={() => importMutation.mutate()}
            disabled={importMutation.isPending || parsedCases.length === 0}
            startIcon={importMutation.isPending ? <CircularProgress size={16} /> : <UploadIcon />}
          >
            {t('importDialog.importCount', { count: parsedCases.length })}
          </GradientButton>
        )}
      </DialogActions>
    </Dialog>
  )
}
