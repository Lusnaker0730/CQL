import { useTranslation } from 'react-i18next'
import {
  Alert, Box, Button, FormControl, IconButton, InputLabel, MenuItem, Select, Stack, TextField, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { AgeBand, ValueStratifier, ValueStratifierSource } from '../../types/ecqm'
import { DEFAULT_AGE_BANDS, VALUE_STRATIFIER_SOURCES } from '../../constants/ecqmConstants'
import { defaultBandLabel, validateAgeBands } from '../../utils/ageBands'

interface Props {
  /** Absent means "by gender". */
  value?: ValueStratifier
  /** Stable id for the labelled select. */
  idSuffix: string
  disabled?: boolean
  onChange: (value: ValueStratifier) => void
}

function parseYears(raw: string): number | undefined {
  const trimmed = raw.trim()
  if (trimmed === '') return undefined
  const n = Number(trimmed)
  return Number.isFinite(n) ? n : Number.NaN
}

/**
 * PAT-233 / PAT-234 — the "value" of a value stratifier or a value supplemental data element:
 * which structured source (gender, age bands) and, for age bands, the bands themselves.
 * Structured on purpose — the artifact is client JSON, so there is no free CQL field.
 */
export default function ValueSourceEditor({ value, idSuffix, disabled, onChange }: Props) {
  const { t } = useTranslation('ecqm')
  const source = value?.source ?? 'gender'
  const bands = value?.bands ?? []
  const problems = source === 'ageBands' ? validateAgeBands(bands) : []

  const setSource = (next: ValueStratifierSource) => {
    const nextBands = next === 'ageBands' ? (bands.length ? bands : DEFAULT_AGE_BANDS.map((b) => ({ ...b }))) : undefined
    onChange({ source: next, ...(nextBands ? { bands: nextBands } : {}) })
  }
  const setBands = (next: AgeBand[]) => onChange({ source: 'ageBands', bands: next })

  return (
    <Stack spacing={2}>
      <FormControl size="small" sx={{ maxWidth: 320 }} disabled={disabled}>
        <InputLabel id={`value-source-${idSuffix}`}>{t('stratifiers.value.source')}</InputLabel>
        <Select
          labelId={`value-source-${idSuffix}`}
          label={t('stratifiers.value.source')}
          value={source}
          inputProps={{ 'aria-label': t('stratifiers.value.source') }}
          onChange={(e) => setSource(e.target.value as ValueStratifierSource)}
        >
          {VALUE_STRATIFIER_SOURCES.map((s) => (
            <MenuItem key={s} value={s}>{t(`stratifiers.value.sources.${s}`)}</MenuItem>
          ))}
        </Select>
      </FormControl>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {t(`stratifiers.value.sourceHints.${source}`)}
      </Typography>

      {source === 'ageBands' && (
        <Box>
          <Stack spacing={1}>
            {bands.map((band, bi) => (
              <Stack key={bi} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <TextField
                  size="small" label={t('stratifiers.value.bandLabel')} value={band.label ?? ''}
                  disabled={disabled} sx={{ width: 160 }}
                  slotProps={{ htmlInput: { 'aria-label': t('stratifiers.value.bandAria', { number: bi + 1, field: 'label' }) } }}
                  onChange={(e) => {
                    const copy = bands.map((b) => ({ ...b }))
                    copy[bi].label = e.target.value
                    setBands(copy)
                  }}
                />
                {(['min', 'max'] as const).map((field) => (
                  <TextField
                    key={field}
                    size="small" type="number" label={t(`stratifiers.value.${field}`)}
                    value={band[field] ?? ''}
                    disabled={disabled} sx={{ width: 110 }}
                    slotProps={{ htmlInput: { min: 0, max: 150, step: 1, 'aria-label': t('stratifiers.value.bandAria', { number: bi + 1, field }) } }}
                    onChange={(e) => {
                      const copy = bands.map((b) => ({ ...b }))
                      const parsed = parseYears(e.target.value)
                      if (parsed === undefined) delete copy[bi][field]
                      else copy[bi][field] = parsed
                      // A blank label follows the bounds until the author writes one.
                      const prev = copy[bi].label
                      if (!prev || prev === defaultBandLabel(band.min, band.max)) {
                        copy[bi].label = defaultBandLabel(copy[bi].min, copy[bi].max)
                      }
                      setBands(copy)
                    }}
                  />
                ))}
                <IconButton
                  size="small" disabled={disabled}
                  aria-label={t('stratifiers.value.removeBand', { number: bi + 1 })}
                  onClick={() => setBands(bands.filter((_, i) => i !== bi))}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
          </Stack>
          <Button
            size="small" startIcon={<AddIcon />} disabled={disabled} sx={{ mt: 1 }}
            onClick={() => {
              const last = bands[bands.length - 1]
              const min = last?.max !== undefined ? last.max + 1 : undefined
              setBands([...bands, { label: defaultBandLabel(min, undefined), ...(min !== undefined ? { min } : {}) }])
            }}
          >
            {t('stratifiers.value.addBand')}
          </Button>
          {problems.length > 0 && (
            <Alert severity="warning" sx={{ mt: 1 }}>
              {problems.map((p) => t(`stratifiers.bandErrors.${p}`)).join(' ')}
            </Alert>
          )}
        </Box>
      )}
    </Stack>
  )
}
