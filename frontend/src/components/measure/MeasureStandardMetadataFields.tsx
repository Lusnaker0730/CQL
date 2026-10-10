import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Chip,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { DefinitionTerm, MeasureStandardMetadata, MeasureTypeCode } from '../../types'
import { MEASURE_TYPE_OPTIONS } from '../../constants/measureConstants'
import { MEASURE } from '../../constants/fieldConstraints'
import { effectivePeriodInverted } from '../../utils/measureMetadata'

interface Props {
  value: MeasureStandardMetadata
  onChange: (updates: Partial<MeasureStandardMetadata>) => void
  readOnly?: boolean
  size?: 'small' | 'medium'
}

/**
 * PAT-236 — the FHIR Measure metadata that was missing from the platform: measure type,
 * definition terms, clinical recommendation statement, effective / approval / review dates and
 * the experimental flag. Used by the measure details page and the eCQM summary tab, so both
 * edit the same fields with the same rules; the artifact publishes them onto the measure.
 */
export default function MeasureStandardMetadataFields({ value, onChange, readOnly, size = 'small' }: Props) {
  const { t } = useTranslation('measures')
  const terms = value.definitionTerms ?? []
  const periodInverted = effectivePeriodInverted(value.effectiveStart, value.effectiveEnd)

  const updateTerm = (index: number, field: keyof DefinitionTerm, text: string) => {
    const next = terms.map((term, i) => (i === index ? { ...term, [field]: text } : term))
    onChange({ definitionTerms: next })
  }

  const dateField = (field: 'effectiveStart' | 'effectiveEnd' | 'approvalDate' | 'lastReviewDate', error?: boolean) => (
    <TextField
      label={t(`standardMetadata.${field}`)}
      type="date"
      size={size}
      fullWidth
      disabled={readOnly}
      value={value[field] || ''}
      error={error}
      onChange={(e) => onChange({ [field]: e.target.value || null })}
      slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'data-testid': `standard-metadata-${field}` } }}
    />
  )

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
        <TextField
          label={t('standardMetadata.measureTypes')}
          select
          size={size}
          fullWidth
          disabled={readOnly}
          value={value.measureTypes ?? []}
          onChange={(e) => {
            // MUI hands a string[] for a multiple select; the string form only appears in the autofill path.
            const raw = e.target.value as unknown as MeasureTypeCode[] | string
            const next = (typeof raw === 'string' ? raw.split(',') : raw) as MeasureTypeCode[]
            onChange({ measureTypes: next.slice(0, MEASURE.measureTypes.maxItems) })
          }}
          helperText={t('standardMetadata.measureTypesHelper')}
          slotProps={{
            select: {
              multiple: true,
              renderValue: (selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {(selected as MeasureTypeCode[]).map((code) => (
                    <Chip key={code} size="small" label={t(`standardMetadata.types.${code}`)} />
                  ))}
                </Box>
              ),
            },
          }}
        >
          {MEASURE_TYPE_OPTIONS.map((code) => (
            <MenuItem key={code} value={code}>{t(`standardMetadata.types.${code}`)}</MenuItem>
          ))}
        </TextField>
        <FormControlLabel
          sx={{ minWidth: 200, mt: 0.5 }}
          control={
            <Switch
              checked={value.experimental === true}
              disabled={readOnly}
              onChange={(e) => onChange({ experimental: e.target.checked })}
              slotProps={{ input: { 'aria-label': t('standardMetadata.experimental') } }}
            />
          }
          label={
            <Box>
              <Typography variant="body2">{t('standardMetadata.experimental')}</Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t('standardMetadata.experimentalHelper')}</Typography>
            </Box>
          }
        />
      </Stack>

      <Stack direction="row" spacing={2}>
        {dateField('effectiveStart', periodInverted)}
        {dateField('effectiveEnd', periodInverted)}
      </Stack>
      {periodInverted && (
        <Typography variant="caption" color="error" role="alert">{t('standardMetadata.effectivePeriodInverted')}</Typography>
      )}
      <Stack direction="row" spacing={2}>
        {dateField('approvalDate')}
        {dateField('lastReviewDate')}
      </Stack>

      <TextField
        label={t('standardMetadata.clinicalRecommendationStatement')}
        size={size}
        fullWidth
        multiline
        rows={3}
        disabled={readOnly}
        value={value.clinicalRecommendationStatement || ''}
        onChange={(e) => onChange({ clinicalRecommendationStatement: e.target.value })}
        placeholder={t('standardMetadata.clinicalRecommendationStatementPlaceholder')}
        helperText={`${(value.clinicalRecommendationStatement || '').length} / ${MEASURE.clinicalRecommendationStatement.maxLength}`}
        slotProps={{ htmlInput: { maxLength: MEASURE.clinicalRecommendationStatement.maxLength } }}
      />

      <Box>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t('standardMetadata.definitionTerms')}</Typography>
        <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 1 }}>
          {t('standardMetadata.definitionTermsHelper')}
        </Typography>
        <Stack spacing={1.5}>
          {terms.map((term, i) => (
            <Stack key={i} direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
              <TextField
                label={t('standardMetadata.term')}
                size={size}
                sx={{ minWidth: 200 }}
                disabled={readOnly}
                value={term.term || ''}
                onChange={(e) => updateTerm(i, 'term', e.target.value)}
                slotProps={{ htmlInput: { maxLength: MEASURE.definitionTerm.maxLength } }}
              />
              <TextField
                label={t('standardMetadata.definition')}
                size={size}
                fullWidth
                multiline
                disabled={readOnly}
                value={term.definition || ''}
                onChange={(e) => updateTerm(i, 'definition', e.target.value)}
                slotProps={{ htmlInput: { maxLength: MEASURE.definitionText.maxLength } }}
              />
              {!readOnly && (
                <IconButton
                  size="small"
                  color="error"
                  aria-label={t('standardMetadata.removeTerm')}
                  onClick={() => onChange({ definitionTerms: terms.filter((_, j) => j !== i) })}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
          {!readOnly && (
            <Button
              size="small"
              startIcon={<AddIcon />}
              disabled={terms.length >= MEASURE.definitionTerms.maxItems}
              onClick={() => onChange({ definitionTerms: [...terms, { term: '', definition: '' }] })}
              sx={{ alignSelf: 'flex-start' }}
            >
              {t('standardMetadata.addTerm')}
            </Button>
          )}
        </Stack>
      </Box>
    </Stack>
  )
}
