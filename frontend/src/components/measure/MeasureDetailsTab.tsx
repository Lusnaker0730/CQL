import { useState, useEffect } from 'react'
import {
  Box,
  Typography,
  Stack,
  TextField,
  MenuItem,
  Alert,
  Divider,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  IconButton,
  Chip,
  Button,
} from '@mui/material'
import {
  Save as SaveIcon,
  ExpandMore as ExpandMoreIcon,
  CheckCircle as CheckIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import SectionHeader from '../common/SectionHeader'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import { helpContent } from '../../constants/helpContent'
import { REFERENCE_TYPES } from '../../constants/populationConfig'
import type { MeasureDefinition, MeasureReference } from '../../types'

interface MeasureDetailsTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

function sectionFilled(fields: (string | undefined | null | unknown[])[]): boolean {
  return fields.some((f) => {
    if (Array.isArray(f)) return f.length > 0
    return f && String(f).trim().length > 0
  })
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

  const updateField = (field: keyof MeasureDefinition, value: unknown) => {
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

  // Reference management
  const addReference = () => {
    const refs = [...(form.references || [])]
    refs.push({ type: 'citation', reference: '' })
    updateField('references', refs)
  }

  const updateReference = (index: number, field: keyof MeasureReference, value: string) => {
    const refs = [...(form.references || [])]
    refs[index] = { ...refs[index], [field]: value }
    updateField('references', refs)
  }

  const removeReference = (index: number) => {
    const refs = [...(form.references || [])]
    refs.splice(index, 1)
    updateField('references', refs)
  }

  // Developer management
  const addDeveloper = () => {
    const devs = [...(form.developers || []), '']
    updateField('developers', devs)
  }

  const updateDeveloper = (index: number, value: string) => {
    const devs = [...(form.developers || [])]
    devs[index] = value
    updateField('developers', devs)
  }

  const removeDeveloper = (index: number) => {
    const devs = [...(form.developers || [])]
    devs.splice(index, 1)
    updateField('developers', devs)
  }

  const generalFilled = sectionFilled([form.name, form.version, form.scoringType, form.measureSet])
  const overviewFilled = sectionFilled([form.title, form.description, form.rationale, form.clinicalGuidance])
  const stewardFilled = sectionFilled([form.steward, form.developers])
  const refsFilled = sectionFilled([form.references])
  const legalFilled = sectionFilled([form.copyright, form.disclaimer])

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

      <Stack spacing={1}>
        {/* General Information */}
        <Accordion defaultExpanded>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {generalFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">General Information</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
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
              <Stack direction="row" spacing={2}>
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
                <TextField
                  label="Measure Set"
                  size="small"
                  fullWidth
                  value={form.measureSet || ''}
                  onChange={(e) => updateField('measureSet', e.target.value)}
                  placeholder="e.g. CMS Quality Reporting"
                />
              </Stack>
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
          </AccordionDetails>
        </Accordion>

        {/* Measure Overview */}
        <Accordion defaultExpanded>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {overviewFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">Measure Overview</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
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
              <TextField
                label="Rationale"
                size="small"
                fullWidth
                multiline
                rows={3}
                value={form.rationale || ''}
                onChange={(e) => updateField('rationale', e.target.value)}
                placeholder="Why is this measure important?"
              />
              <TextField
                label="Clinical Guidance"
                size="small"
                fullWidth
                multiline
                rows={3}
                value={form.clinicalGuidance || ''}
                onChange={(e) => updateField('clinicalGuidance', e.target.value)}
                placeholder="Implementation guidance for clinical users"
              />
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* Steward & Developers */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {stewardFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">Steward & Developers</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label="Steward"
                size="small"
                fullWidth
                value={form.steward || ''}
                onChange={(e) => updateField('steward', e.target.value)}
                placeholder="Organization responsible for the measure"
              />
              <Divider />
              <Typography variant="caption" color="text.secondary">Developers</Typography>
              {(form.developers || []).map((dev, i) => (
                <Stack key={i} direction="row" spacing={1} alignItems="center">
                  <TextField
                    size="small"
                    fullWidth
                    value={dev}
                    onChange={(e) => updateDeveloper(i, e.target.value)}
                    placeholder="Developer name or organization"
                  />
                  <IconButton size="small" aria-label="Remove developer" color="error" onClick={() => removeDeveloper(i)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={addDeveloper} sx={{ alignSelf: 'flex-start' }}>
                Add Developer
              </Button>
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* References */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {refsFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">References</Typography>
              {(form.references || []).length > 0 && (
                <Chip label={`${(form.references || []).length}`} size="small" sx={{ height: 20, fontSize: '0.7rem' }} />
              )}
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              {(form.references || []).map((ref, i) => (
                <Stack key={i} direction="row" spacing={1} alignItems="flex-start">
                  <TextField
                    select
                    label="Type"
                    size="small"
                    sx={{ minWidth: 160 }}
                    value={ref.type}
                    onChange={(e) => updateReference(i, 'type', e.target.value)}
                  >
                    {REFERENCE_TYPES.map((t) => (
                      <MenuItem key={t} value={t}>{t}</MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label="Reference"
                    size="small"
                    fullWidth
                    value={ref.reference}
                    onChange={(e) => updateReference(i, 'reference', e.target.value)}
                    placeholder="URL or citation text"
                  />
                  <IconButton size="small" aria-label="Remove reference" color="error" onClick={() => removeReference(i)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={addReference} sx={{ alignSelf: 'flex-start' }}>
                Add Reference
              </Button>
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* Legal */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {legalFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">Legal</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label="Copyright"
                size="small"
                fullWidth
                multiline
                rows={2}
                value={form.copyright || ''}
                onChange={(e) => updateField('copyright', e.target.value)}
              />
              <TextField
                label="Disclaimer"
                size="small"
                fullWidth
                multiline
                rows={2}
                value={form.disclaimer || ''}
                onChange={(e) => updateField('disclaimer', e.target.value)}
              />
            </Stack>
          </AccordionDetails>
        </Accordion>
      </Stack>
    </Box>
  )
}
