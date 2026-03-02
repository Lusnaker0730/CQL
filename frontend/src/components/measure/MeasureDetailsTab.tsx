import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { extractApiError } from '../../utils/errorUtils'
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
import {
  MEASURE_STATUS_OPTIONS,
  SCORING_TYPE_OPTIONS,
  MEASURE_SETTING_OPTIONS,
  COMPOSITE_SCORING_OPTIONS,
  DEFAULT_REFERENCE_TYPE,
} from '../../constants/measureConstants'
import type { MeasureDefinition, MeasureReference } from '../../types'
import DepartmentSelector from '../common/DepartmentSelector'
import IndicatorMappingSection from './IndicatorMappingSection'

interface MeasureDetailsTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
  readOnly?: boolean
}

function sectionFilled(fields: (string | undefined | null | unknown[])[]): boolean {
  return fields.some((f) => {
    if (Array.isArray(f)) return f.length > 0
    return f && String(f).trim().length > 0
  })
}

export default function MeasureDetailsTab({ measure, onMeasureUpdate, readOnly }: MeasureDetailsTabProps) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const [form, setForm] = useState<MeasureDefinition>({ ...measure })
  const [isDirty, setIsDirty] = useState(false)

  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    setForm({ ...measure })
    setIsDirty(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset form only when measure.id changes
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
    refs.push({ type: DEFAULT_REFERENCE_TYPE, reference: '' })
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

  const generalFilled = sectionFilled([form.name, form.version, form.scoringType, form.measureSet, form.setting, form.nqfNumber, form.cmsMeasureId])
  const overviewFilled = sectionFilled([form.title, form.description, form.rationale, form.clinicalGuidance])
  const stewardFilled = sectionFilled([form.steward, form.developers])
  const refsFilled = sectionFilled([form.references])
  const legalFilled = sectionFilled([form.copyright, form.disclaimer])

  return (
    <Box sx={{ p: 2, overflow: 'auto', height: '100%' }}>
      <SectionHeader
        title={t('details.title')}
        helpText={helpContent.measures.details}
        actions={
          <GradientButton
            startIcon={<SaveIcon />}
            disabled={!isDirty || updateMutation.isPending || readOnly}
            onClick={() => updateMutation.mutate()}
          >
            {updateMutation.isPending ? t('details.saving') : t('details.saveChanges')}
          </GradientButton>
        }
      />

      {updateMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {extractApiError(updateMutation.error)}
        </Alert>
      )}

      {updateMutation.isSuccess && !isDirty && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('details.updateSuccess')}
        </Alert>
      )}

      <Stack spacing={1}>
        {/* General Information */}
        <Accordion defaultExpanded>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {generalFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">{t('details.generalInformation')}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label={t('details.fields.name')}
                required
                size="small"
                fullWidth
                value={form.name}
                onChange={(e) => updateField('name', e.target.value)}
              />
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('details.fields.version')}
                  size="small"
                  fullWidth
                  value={form.version}
                  onChange={(e) => updateField('version', e.target.value)}
                />
                <TextField
                  label={t('details.fields.status')}
                  select
                  size="small"
                  fullWidth
                  value={form.status}
                  onChange={(e) => updateField('status', e.target.value)}
                >
                  {MEASURE_STATUS_OPTIONS.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
                </TextField>
              </Stack>
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('details.fields.scoringType')}
                  select
                  size="small"
                  fullWidth
                  value={form.scoringType}
                  onChange={(e) => updateField('scoringType', e.target.value)}
                >
                  {SCORING_TYPE_OPTIONS.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
                </TextField>
                <TextField
                  label={t('details.fields.measureSet')}
                  size="small"
                  fullWidth
                  value={form.measureSet || ''}
                  onChange={(e) => updateField('measureSet', e.target.value)}
                  placeholder={t('details.fields.measureSetPlaceholder')}
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('details.fields.nqfNumber')}
                  size="small"
                  fullWidth
                  value={form.nqfNumber || ''}
                  onChange={(e) => updateField('nqfNumber', e.target.value)}
                  placeholder={t('details.fields.nqfNumberPlaceholder')}
                />
                <TextField
                  label={t('details.fields.cmsMeasureId')}
                  size="small"
                  fullWidth
                  value={form.cmsMeasureId || ''}
                  onChange={(e) => updateField('cmsMeasureId', e.target.value)}
                  placeholder={t('details.fields.cmsMeasureIdPlaceholder')}
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('details.fields.setting')}
                  select
                  size="small"
                  fullWidth
                  value={form.setting || ''}
                  onChange={(e) => updateField('setting', e.target.value)}
                  helperText={t('details.fields.settingHelper')}
                >
                  <MenuItem value="">
                    <em>{t('details.fields.none')}</em>
                  </MenuItem>
                  {MEASURE_SETTING_OPTIONS.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
                </TextField>
                <DepartmentSelector
                  value={form.department || ''}
                  onChange={(v) => updateField('department', v)}
                  label={t('details.fields.department')}
                  showAll={false}
                />
              </Stack>
              {form.compositeScoring && (
                <TextField
                  label={t('details.fields.compositeScoring')}
                  select
                  size="small"
                  fullWidth
                  value={form.compositeScoring || ''}
                  onChange={(e) => updateField('compositeScoring', e.target.value)}
                >
                  {COMPOSITE_SCORING_OPTIONS.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
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
              <Typography variant="subtitle2">{t('details.measureOverview')}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label={t('details.overviewFields.title')}
                size="small"
                fullWidth
                value={form.title || ''}
                onChange={(e) => updateField('title', e.target.value)}
              />
              <TextField
                label={t('details.overviewFields.description')}
                size="small"
                fullWidth
                multiline
                rows={3}
                value={form.description || ''}
                onChange={(e) => updateField('description', e.target.value)}
              />
              <TextField
                label={t('details.overviewFields.rationale')}
                size="small"
                fullWidth
                multiline
                rows={3}
                value={form.rationale || ''}
                onChange={(e) => updateField('rationale', e.target.value)}
                placeholder={t('details.overviewFields.rationalePlaceholder')}
              />
              <TextField
                label={t('details.overviewFields.clinicalGuidance')}
                size="small"
                fullWidth
                multiline
                rows={3}
                value={form.clinicalGuidance || ''}
                onChange={(e) => updateField('clinicalGuidance', e.target.value)}
                placeholder={t('details.overviewFields.clinicalGuidancePlaceholder')}
              />
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* Steward & Developers */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {stewardFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">{t('details.stewardDevelopers')}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label={t('details.stewardFields.steward')}
                size="small"
                fullWidth
                value={form.steward || ''}
                onChange={(e) => updateField('steward', e.target.value)}
                placeholder={t('details.stewardFields.stewardPlaceholder')}
              />
              <Divider />
              <Typography variant="caption" color="text.secondary">{t('details.stewardFields.developers')}</Typography>
              {(form.developers || []).map((dev, i) => (
                <Stack key={i} direction="row" spacing={1} alignItems="center">
                  <TextField
                    size="small"
                    fullWidth
                    value={dev}
                    onChange={(e) => updateDeveloper(i, e.target.value)}
                    placeholder={t('details.stewardFields.developerPlaceholder')}
                  />
                  <IconButton size="small" aria-label={t('details.stewardFields.removeDeveloper')} color="error" onClick={() => removeDeveloper(i)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={addDeveloper} sx={{ alignSelf: 'flex-start' }}>
                {t('details.stewardFields.addDeveloper')}
              </Button>
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* References */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {refsFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">{t('details.references')}</Typography>
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
                    label={t('details.referenceFields.type')}
                    size="small"
                    sx={{ minWidth: 160 }}
                    value={ref.type}
                    onChange={(e) => updateReference(i, 'type', e.target.value)}
                  >
                    {REFERENCE_TYPES.map((refType) => (
                      <MenuItem key={refType} value={refType}>{refType}</MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label={t('details.referenceFields.reference')}
                    size="small"
                    fullWidth
                    value={ref.reference}
                    onChange={(e) => updateReference(i, 'reference', e.target.value)}
                    placeholder={t('details.referenceFields.referencePlaceholder')}
                  />
                  <IconButton size="small" aria-label={t('details.referenceFields.removeReference')} color="error" onClick={() => removeReference(i)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
              <Button size="small" startIcon={<AddIcon />} onClick={addReference} sx={{ alignSelf: 'flex-start' }}>
                {t('details.referenceFields.addReference')}
              </Button>
            </Stack>
          </AccordionDetails>
        </Accordion>

        {/* Indicator Code Mapping */}
        <IndicatorMappingSection
          measure={form}
          onChange={(updates) => {
            setForm((prev) => ({ ...prev, ...updates }))
            setIsDirty(true)
          }}
          readOnly={readOnly}
        />

        {/* Legal */}
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              {legalFilled && <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />}
              <Typography variant="subtitle2">{t('details.legal')}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Stack spacing={2}>
              <TextField
                label={t('details.legalFields.copyright')}
                size="small"
                fullWidth
                multiline
                rows={2}
                value={form.copyright || ''}
                onChange={(e) => updateField('copyright', e.target.value)}
              />
              <TextField
                label={t('details.legalFields.disclaimer')}
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
