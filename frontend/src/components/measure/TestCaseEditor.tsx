import { useState, useEffect } from 'react'
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
} from '@mui/material'
import {
  Save as SaveIcon,
  Close as CloseIcon,
} from '@mui/icons-material'
import Editor from '@monaco-editor/react'
import GradientButton from '../common/GradientButton'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
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

export default function TestCaseEditor({ measure, testCase, onClose, onSaved }: TestCaseEditorProps) {
  const queryClient = useQueryClient()
  const isNew = !testCase?.id

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
  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    if (testCase) {
      setTitle(testCase.title)
      setDescription(testCase.description || '')
      setBundleJson(testCase.patientBundleJson || DEFAULT_BUNDLE)
      setExpectedPops(testCase.expectedPopulations || {})
      setSeries(testCase.series || '')
    }
  }, [testCase])

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

  const handleBundleChange = (value: string) => {
    setBundleJson(value)
    setIsDirty(true)
    if (value.trim()) validateBundle(value)
    else setBundleError(null)
  }

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

        <Typography variant="subtitle2" color="text.secondary">
          Patient Bundle (FHIR JSON)
        </Typography>

        {bundleError && (
          <Alert severity="error" sx={{ py: 0 }}>
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
      </Stack>
    </Box>
  )
}
