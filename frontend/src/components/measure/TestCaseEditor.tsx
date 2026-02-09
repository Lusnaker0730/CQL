import { useState, useEffect, useRef, useCallback } from 'react'
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
} from '@mui/material'
import {
  Save as SaveIcon,
  Close as CloseIcon,
  ViewModule as BuilderIcon,
  Code as JsonIcon,
} from '@mui/icons-material'
import Editor from '@monaco-editor/react'
import GradientButton from '../common/GradientButton'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import {
  BundleBuilderProvider,
  useBundleBuilder,
  serializeToBundle,
  parseFromBundle,
} from '../../contexts/BundleBuilderContext'
import VisualBundleBuilder from '../testcase-builder/VisualBundleBuilder'
import type { TestCase, MeasureDefinition } from '../../types'

interface TestCaseEditorProps {
  measure: MeasureDefinition
  testCase: TestCase | null
  onClose: () => void
  onSaved: () => void
}

const POPULATION_TYPES = [
  { key: 'initial-population', label: 'Initial Population' },
  { key: 'denominator', label: 'Denominator' },
  { key: 'denominator-exclusion', label: 'Denominator Exclusion' },
  { key: 'denominator-exception', label: 'Denominator Exception' },
  { key: 'numerator', label: 'Numerator' },
  { key: 'numerator-exclusion', label: 'Numerator Exclusion' },
]

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

function TestCaseEditorInner({ measure, testCase, onClose, onSaved }: TestCaseEditorProps) {
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
  useUnsavedChangesGuard(isDirty)

  // Track whether sync is in progress to prevent loops
  const syncingRef = useRef(false)
  const initializedRef = useRef(false)

  // Initialize builder from existing JSON on mount
  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true
      try {
        const entries = parseFromBundle(bundleJson)
        if (entries.length > 0) {
          dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
        }
      } catch {
        // Invalid JSON — user will see error in JSON tab
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (testCase) {
      setTitle(testCase.title)
      setDescription(testCase.description || '')
      const json = testCase.patientBundleJson || DEFAULT_BUNDLE
      setBundleJson(json)
      setExpectedPops(testCase.expectedPopulations || {})
      setSeries(testCase.series || '')
      try {
        const entries = parseFromBundle(json)
        dispatch({ type: 'LOAD_FROM_JSON', payload: entries })
      } catch {
        // ignore
      }
    }
  }, [testCase]) // eslint-disable-line react-hooks/exhaustive-deps

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
        setBundleError('Root resource must be a FHIR Bundle')
        return false
      }
      setBundleError(null)
      return true
    } catch {
      setBundleError('Invalid JSON')
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
          {isNew ? 'New Test Case' : `Edit: ${testCase?.title}`}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button size="small" startIcon={<CloseIcon />} onClick={onClose}>
            Cancel
          </Button>
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!title.trim() || saveMutation.isPending}
            onClick={handleSave}
          >
            {saveMutation.isPending ? 'Saving...' : 'Save'}
          </GradientButton>
        </Stack>
      </Stack>

      {saveMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(saveMutation.error as Error).message}
        </Alert>
      )}

      <Stack spacing={2}>
        <TextField
          label="Test Case Title"
          required
          size="small"
          fullWidth
          value={title}
          onChange={(e) => { setTitle(e.target.value); setIsDirty(true) }}
          placeholder="e.g. 65yo Female with Diabetes, No HbA1c"
        />

        <TextField
          label="Description"
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
              label="Series"
              size="small"
              fullWidth
              placeholder="Group test cases by series name"
              inputProps={{ ...params.inputProps, maxLength: 250 }}
            />
          )}
        />

        <Divider />

        <Typography variant="subtitle2" color="text.secondary">
          Expected Population Membership
        </Typography>

        <Paper variant="outlined" sx={{ p: 1.5 }}>
          <Stack spacing={0.5}>
            {POPULATION_TYPES.map((pop) => (
              <FormControlLabel
                key={pop.key}
                control={
                  <Switch
                    size="small"
                    checked={!!expectedPops[pop.key]}
                    onChange={() => togglePopulation(pop.key)}
                  />
                }
                label={
                  <Typography variant="body2">{pop.label}</Typography>
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
              label="Visual Builder"
              sx={{ minHeight: 36, textTransform: 'none', fontSize: '0.85rem' }}
            />
            <Tab
              icon={<JsonIcon sx={{ fontSize: 18 }} />}
              iconPosition="start"
              label="JSON (Advanced)"
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
