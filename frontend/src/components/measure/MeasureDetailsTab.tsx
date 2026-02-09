import { useState, useEffect } from 'react'
import {
  Box,
  Typography,
  Stack,
  TextField,
  MenuItem,
  Alert,
  Divider,
} from '@mui/material'
import { Save as SaveIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import SectionHeader from '../common/SectionHeader'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { helpContent } from '../../constants/helpContent'
import type { MeasureDefinition } from '../../types'

interface MeasureDetailsTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureDetailsTab({ measure, onMeasureUpdate }: MeasureDetailsTabProps) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<MeasureDefinition>({ ...measure })
  const [isDirty, setIsDirty] = useState(false)

  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    setForm({ ...measure })
    setIsDirty(false)
  }, [measure.id])

  const updateField = (field: keyof MeasureDefinition, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    setIsDirty(true)
  }

  const updateMutation = useMutation({
    mutationFn: () => measureApi.updateMeasure(measure.id!, form),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      onMeasureUpdate(updated)
      setIsDirty(false)
    },
  })

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <SectionHeader
        title="Measure Details"
        helpText={helpContent.measures.details}
        actions={
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!isDirty || updateMutation.isPending}
            onClick={() => updateMutation.mutate()}
          >
            {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
          </GradientButton>
        }
      />

      {updateMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(updateMutation.error as Error).message}
        </Alert>
      )}

      {updateMutation.isSuccess && !isDirty && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Measure updated successfully.
        </Alert>
      )}

      <Stack spacing={2.5}>
        <Typography variant="subtitle2" color="text.secondary">
          Basic Information
        </Typography>

        <TextField
          label="Name"
          required
          size="small"
          fullWidth
          value={form.name}
          onChange={(e) => updateField('name', e.target.value)}
        />

        <Stack direction="row" spacing={2}>
          <TextField
            label="Version"
            size="small"
            fullWidth
            value={form.version}
            onChange={(e) => updateField('version', e.target.value)}
          />
          <TextField
            label="Status"
            select
            size="small"
            fullWidth
            value={form.status}
            onChange={(e) => updateField('status', e.target.value)}
          >
            <MenuItem value="draft">Draft</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="retired">Retired</MenuItem>
          </TextField>
        </Stack>

        <TextField
          label="Title"
          size="small"
          fullWidth
          value={form.title || ''}
          onChange={(e) => updateField('title', e.target.value)}
        />

        <TextField
          label="Description"
          size="small"
          fullWidth
          multiline
          rows={3}
          value={form.description || ''}
          onChange={(e) => updateField('description', e.target.value)}
        />

        <Divider />

        <Typography variant="subtitle2" color="text.secondary">
          Measure Configuration
        </Typography>

        <TextField
          label="Scoring Type"
          select
          size="small"
          fullWidth
          value={form.scoringType}
          onChange={(e) => updateField('scoringType', e.target.value)}
        >
          <MenuItem value="proportion">Proportion</MenuItem>
          <MenuItem value="ratio">Ratio</MenuItem>
          <MenuItem value="continuous-variable">Continuous Variable</MenuItem>
          <MenuItem value="cohort">Cohort</MenuItem>
          <MenuItem value="composite">Composite</MenuItem>
        </TextField>

        {form.compositeScoring && (
          <TextField
            label="Composite Scoring"
            select
            size="small"
            fullWidth
            value={form.compositeScoring || ''}
            onChange={(e) => updateField('compositeScoring', e.target.value)}
          >
            <MenuItem value="opportunity">Opportunity</MenuItem>
            <MenuItem value="linear">Linear</MenuItem>
          </TextField>
        )}
      </Stack>
    </Box>
  )
}
