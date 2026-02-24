import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  Stack,
  TextField,
  Button,
  Switch,
  FormControlLabel,
  Alert,
  Paper,
  Divider,
  Autocomplete,
  Tabs,
  Tab,
  useTheme,
} from '@mui/material'
import {
  Save as SaveIcon,
  Close as CloseIcon,
  ViewModule as BuilderIcon,
  Code as JsonIcon,
  CloudDownload as EhrImportIcon,
} from '@mui/icons-material'
import Editor from '@monaco-editor/react'
import GradientButton from '../common/GradientButton'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { useTestCaseDraft, clearTestCaseDraft } from '../../hooks/useTestCaseDraft'
import {
  BundleBuilderProvider,
  useBundleBuilder,
  serializeToBundle,
  parseFromBundle,
} from '../../contexts/BundleBuilderContext'
import VisualBundleBuilder from '../testcase-builder/VisualBundleBuilder'
import type { TestCase, MeasureDefinition } from '../../types'
import EhrImportForTestCase from '../ehr/EhrImportForTestCase'

interface TestCaseEditorProps {
  measure: MeasureDefinition
  testCase: TestCase | null
  onClose: () => void
  onSaved: () => void
  readOnly?: boolean
}

const POPULATION_KEYS = [
  'initial-population',
  'denominator',
  'denominator-exclusion',
  'denominator-exception',
  'numerator',
  'numerator-exclusion',
] as const

const DEFAULT_BUNDLE = `{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "id": "test-patient-1",
        "gender": "female",
        "birthDate": "1960-01-01"
      }
    }
  ]
}`

function TestCaseEditorInner({ measure, testCase, onClose, onSaved, readOnly }: TestCaseEditorProps) {
  const { t } = useTranslation('measures')
  const theme = useTheme()
  const queryClient = useQueryClient()
  const isNew = !testCase?.id
  const { state, dispatch } = useBundleBuilder()

  const [title, setTitle] = useState(testCase?.title || '')
  const [description, setDescription] = useState(testCase?.description || '')
  const [bundleJson, setBundleJson] = useState(testCase?.patientBundleJson || DEFAULT_BUNDLE)
  const [expectedPops, setExpectedPops] = useState<Record<string, boolean>>(
    testCase?.expectedPopulations || {
      'initial-population': true,
      'denominator': true,
      'numerator': false,
    }
  )
  const [bundleError, setBundleError] = useState<string | null>(null)
  const [series, setSeries] = useState(testCase?.series || '')
  const existingSeries: string[] = []
  const [isDirty, setIsDirty] = useState(false)
  const [bundleTab, setBundleTab] = useState(0) // 0 = Visual, 1 = JSON
  const [showDraftAlert, setShowDraftAlert] = useState(false)
  const [ehrImportOpen, setEhrImportOpen] = useState(false)
  useUnsavedChangesGuard(isDirty)

  // Draft auto-save
  const { restoredDraft, dismissDraft } = useTestCaseDraft({
    measureId: measure.id!,
    testCaseId: testCase?.id ?? null,
    title,
    description,
    bundleJson,
    expectedPops,
    series,
  })

  // Restore draft on mount (only once)
  const draftAppliedRef = useRef(false)
  useEffect(() => {
    if (restoredDraft && !draftAppliedRef.current) {
      draftAppliedRef.current = true
      setTitle(restoredDraft.title)
      setDescription(restoredDraft.description)
      setBundleJson(restoredDraft.bundleJson)
      setExpectedPops(restoredDraft.expectedPops)
      setSeries(restoredDraft.series)
      setShowDraftAlert(true)
      try {
        const entries = parseFromBundle(restoredDraft.bundleJson)
        if (entries.length > 0) {
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // Invalid JSON in draft — user can fix in JSON tab
      }
    }
  }, [restoredDraft, dispatch])

  // Track whether sync is in progress to prevent loops
  const syncingRef = useRef(false)
  const initializedRef = useRef(false)

  // Track bundleJson in a ref so the initialization effect can read the
  // current value without re-running when bundleJson changes
  const bundleJsonRef = useRef(bundleJson)
  bundleJsonRef.current = bundleJson

  // Initialize builder from existing JSON on mount
  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true
      try {
        const entries = parseFromBundle(bundleJsonRef.current)
        if (entries.length > 0) {
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // Invalid JSON — user will see error in JSON tab
      }
    }
  }, [dispatch])

  // Only reset editor state when switching to a DIFFERENT test case, not on
  // same-test-case reference changes (e.g. React Query refetch).
  const prevTestCaseIdRef = useRef(testCase?.id)
  useEffect(() => {
    if (testCase && testCase.id !== prevTestCaseIdRef.current) {
      prevTestCaseIdRef.current = testCase.id
      setTitle(testCase.title)
      setDescription(testCase.description || '')
      const json = testCase.patientBundleJson || DEFAULT_BUNDLE
      setBundleJson(json)
      setExpectedPops(testCase.expectedPopulations || {})
      setSeries(testCase.series || '')
      setIsDirty(false)
      try {
        const entries = parseFromBundle(json)
        dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
      } catch {
        // ignore
      }
    }
  }, [testCase, dispatch])

  // Sync: Visual Builder → JSON (when entries change)
  useEffect(() => {
    if (syncingRef.current) return
    if (state.entries.length > 0) {
      syncingRef.current = true
      const json = serializeToBundle(state.entries)
      setBundleJson(json)
      setBundleError(null)
      setIsDirty(true)
      syncingRef.current = false
    }
  }, [state.entries])

  const validateBundle = (json: string): boolean => {
    try {
      const parsed = JSON.parse(json)
      if (parsed.resourceType !== 'Bundle') {
        setBundleError(t('testCaseEditor.validation.invalidBundle'))
        return false
      }
      setBundleError(null)
      return true
    } catch {
      setBundleError(t('testCaseEditor.validation.invalidJson'))
      return false
    }
  }

  // Debounced sync: JSON → Visual Builder
  const jsonSyncTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const handleBundleChange = useCallback(
    (value: string) => {
      setBundleJson(value)
      setIsDirty(true)
      if (value.trim()) validateBundle(value)
      else setBundleError(null)

      // Debounced sync to visual builder
      if (jsonSyncTimerRef.current) clearTimeout(jsonSyncTimerRef.current)
      jsonSyncTimerRef.current = setTimeout(() => {
        if (syncingRef.current) return
        try {
          const entries = parseFromBundle(value)
          if (entries.length > 0) {
            syncingRef.current = true
            dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
            syncingRef.current = false
          }
        } catch {
          // Invalid JSON — don't sync
        }
      }, 500)
    },
    [dispatch]
  )

  const togglePopulation = (key: string) => {
    setExpectedPops((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
    setIsDirty(true)
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const data: TestCase = {
        title,
        description,
        patientBundleJson: bundleJson,
        expectedPopulations: expectedPops,
        series: series || undefined,
      }
      if (isNew) {
        return measureApi.createTestCase(measure.id!, data)
      }
      return measureApi.updateTestCase(measure.id!, testCase!.id!, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', measure.id] })
      clearTestCaseDraft(measure.id!, testCase?.id ?? null)
      onSaved()
    },
  })

  const handleSave = () => {
    if (!title.trim()) return
    if (bundleJson.trim() && !validateBundle(bundleJson)) return
    saveMutation.mutate()
  }

  return (
    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">
          {isNew ? t('testCaseEditor.newTitle') : t('testCaseEditor.editTitle', { name: testCase?.title })}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button
            size="small"
            startIcon={<EhrImportIcon />}
            onClick={() => setEhrImportOpen(true)}
            disabled={readOnly}
          >
            {t('ehr.importFromEhr', { ns: 'fhir' })}
          </Button>
          <Button size="small" startIcon={<CloseIcon />} onClick={onClose}>
            {t('actions.cancel', { ns: 'common' })}
          </Button>
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!title.trim() || saveMutation.isPending || readOnly}
            onClick={handleSave}
          >
            {saveMutation.isPending ? t('testCaseEditor.saving') : t('testCaseEditor.save')}
          </GradientButton>
        </Stack>
      </Stack>

      {saveMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(saveMutation.error as Error).message}
        </Alert>
      )}

      {showDraftAlert && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => {
                dismissDraft()
                setShowDraftAlert(false)
                // Reset to server values
                setTitle(testCase?.title || '')
                setDescription(testCase?.description || '')
                const json = testCase?.patientBundleJson || DEFAULT_BUNDLE
                setBundleJson(json)
                setExpectedPops(testCase?.expectedPopulations || {
                  'initial-population': true, 'denominator': true, 'numerator': false,
                })
                setSeries(testCase?.series || '')
                try {
                  const entries = parseFromBundle(json)
                  dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
                } catch { /* ignore */ }
              }}
            >
              {t('testCaseEditor.discardDraft')}
            </Button>
          }
        >
          {t('testCaseEditor.draftRestored')}
        </Alert>
      )}

      <Stack spacing={2}>
        <TextField
          label={t('testCaseEditor.fields.title')}
          required
          size="small"
          fullWidth
          value={title}
          onChange={(e) => { setTitle(e.target.value); setIsDirty(true) }}
          placeholder={t('testCaseEditor.fields.titlePlaceholder')}
        />

        <TextField
          label={t('testCaseEditor.fields.description')}
          size="small"
          fullWidth
          multiline
          rows={2}
          value={description}
          onChange={(e) => { setDescription(e.target.value); setIsDirty(true) }}
        />

        <Autocomplete
          freeSolo
          options={existingSeries}
          value={series}
          onInputChange={(_, v) => { setSeries(v); setIsDirty(true) }}
          renderInput={(params) => (
            <TextField
              {...params}
              label={t('testCaseEditor.fields.series')}
              size="small"
              fullWidth
              placeholder={t('testCaseEditor.fields.seriesPlaceholder')}
              inputProps={{ ...params.inputProps, maxLength: 250 }}
            />
          )}
        />

        <Divider />

        <Typography variant="subtitle2" color="text.secondary">
          {t('testCaseEditor.expectedPopulations')}
        </Typography>

        <Paper variant="outlined" sx={{ p: 1.5 }}>
          <Stack spacing={0.5}>
            {POPULATION_KEYS.map((key) => (
              <FormControlLabel
                key={key}
                control={
                  <Switch
                    size="small"
                    checked={!!expectedPops[key]}
                    onChange={() => togglePopulation(key)}
                  />
                }
                label={
                  <Typography variant="body2">{t(`testCaseEditor.populationTypes.${key}`)}</Typography>
                }
              />
            ))}
          </Stack>
        </Paper>

        <Divider />

        <Box>
          <Tabs
            value={bundleTab}
            onChange={(_, v) => setBundleTab(v)}
            sx={{ minHeight: 36, mb: 1 }}
          >
            <Tab
              icon={<BuilderIcon sx={{ fontSize: 18 }} />}
              iconPosition="start"
              label={t('testCaseEditor.tabs.visualBuilder')}
              sx={{ minHeight: 36, textTransform: 'none', fontSize: '0.85rem' }}
            />
            <Tab
              icon={<JsonIcon sx={{ fontSize: 18 }} />}
              iconPosition="start"
              label={t('testCaseEditor.tabs.jsonAdvanced')}
              sx={{ minHeight: 36, textTransform: 'none', fontSize: '0.85rem' }}
            />
          </Tabs>

          {bundleTab === 0 && (
            <VisualBundleBuilder onDirty={() => setIsDirty(true)} />
          )}

          {bundleTab === 1 && (
            <>
              {bundleError && (
                <Alert severity="error" sx={{ py: 0, mb: 1 }}>
                  {bundleError}
                </Alert>
              )}
              <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
                <Editor
                  height="300px"
                  language="json"
                  theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
                  value={bundleJson}
                  onChange={(v) => handleBundleChange(v || '')}
                  options={{
                    minimap: { enabled: false },
                    fontSize: 13,
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                    automaticLayout: true,
                  }}
                />
              </Box>
            </>
          )}
        </Box>
      </Stack>

      {ehrImportOpen && (
        <EhrImportForTestCase
          open={ehrImportOpen}
          measureId={measure.id}
          onClose={() => setEhrImportOpen(false)}
          onImported={(json) => {
            setBundleJson(json)
            setIsDirty(true)
            try {
              const entries = parseFromBundle(json)
              dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
            } catch { /* ignore parse errors */ }
            setEhrImportOpen(false)
          }}
        />
      )}
    </Box>
  )
}

export default function TestCaseEditor(props: TestCaseEditorProps) {
  return (
    <BundleBuilderProvider>
      <TestCaseEditorInner {...props} />
    </BundleBuilderProvider>
  )
}
