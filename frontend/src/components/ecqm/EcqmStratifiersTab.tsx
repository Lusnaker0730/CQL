import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert, Box, Button, FormControl, IconButton, InputLabel, MenuItem, Paper, Select, Stack,
  TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { AgeBand, StratifierElement, StratifierKind, ValueStratifierSource } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { createEmptyConjunctionGroup, DEFAULT_AGE_BANDS, VALUE_STRATIFIER_SOURCES } from '../../constants/ecqmConstants'
import { defaultBandLabel, validateAgeBands } from '../../utils/ageBands'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'

interface Props {
  stratifiers: StratifierElement[]
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  /**
   * PAT-129: when set, the tab renders read-only with an explanatory Alert at
   * the top and Add / Delete / edit affordances disabled. Used to enforce the
   * CMS rule that Ratio measures with separate Initial Populations cannot
   * carry stratifiers. Existing data is intentionally preserved (not wiped) so
   * users can disable dual-IP and have their stratifiers come back.
   */
  disabledReason?: string
  onChange: (stratifiers: StratifierElement[]) => void
}

function newStratId() {
  return 'strat-' + Date.now().toString(36)
}

function parseYears(raw: string): number | undefined {
  const trimmed = raw.trim()
  if (trimmed === '') return undefined
  const n = Number(trimmed)
  return Number.isFinite(n) ? n : Number.NaN
}

export default function EcqmStratifiersTab({
  stratifiers, templates, modifiers, disabledReason, onChange,
}: Props) {
  const { t } = useTranslation('ecqm')
  const disabled = !!disabledReason

  const addStratifier = useCallback(() => {
    if (disabled) return
    onChange([...stratifiers, {
      stratifierId: newStratId(),
      description: '',
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
    }])
  }, [stratifiers, onChange, disabled])

  const updateStratifier = useCallback((idx: number, updated: StratifierElement) => {
    if (disabled) return
    const copy = [...stratifiers]
    copy[idx] = updated
    onChange(copy)
  }, [stratifiers, onChange, disabled])

  const removeStratifier = useCallback((idx: number) => {
    if (disabled) return
    const copy = [...stratifiers]
    copy.splice(idx, 1)
    onChange(copy)
  }, [stratifiers, onChange, disabled])

  // PAT-233: a value stratifier starts as "by gender"; switching back keeps the condition tree.
  const setKind = (idx: number, strat: StratifierElement, kind: StratifierKind) => {
    if (kind === 'value') {
      updateStratifier(idx, { ...strat, kind, value: strat.value ?? { source: 'gender' } })
    } else {
      updateStratifier(idx, { ...strat, kind: 'criteria' })
    }
  }

  const setSource = (idx: number, strat: StratifierElement, source: ValueStratifierSource) => {
    const bands = source === 'ageBands' ? (strat.value?.bands?.length ? strat.value.bands : DEFAULT_AGE_BANDS.map((b) => ({ ...b }))) : undefined
    updateStratifier(idx, { ...strat, value: { source, ...(bands ? { bands } : {}) } })
  }

  const setBands = (idx: number, strat: StratifierElement, bands: AgeBand[]) => {
    updateStratifier(idx, { ...strat, value: { source: 'ageBands', bands } })
  }

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2
        }}>
        <Typography variant="h6">{t('stratifiers.title')}</Typography>
        <Button
          startIcon={<AddIcon />}
          onClick={addStratifier}
          disabled={disabled}
          aria-label={t('stratifiers.addStratifier')}
        >
          {t('stratifiers.addStratifier')}
        </Button>
      </Stack>
      {disabled && (
        <Alert severity="warning" sx={{ mb: 2 }} role="alert">
          {disabledReason}
        </Alert>
      )}
      {stratifiers.length === 0 && !disabled && (
        <Typography sx={{
          color: "text.secondary"
        }}>{t('stratifiers.emptyState')}</Typography>
      )}
      {stratifiers.map((strat, idx) => {
        const kind: StratifierKind = strat.kind === 'value' ? 'value' : 'criteria'
        const source = strat.value?.source ?? 'gender'
        const bands = strat.value?.bands ?? []
        const problems = kind === 'value' && source === 'ageBands' ? validateAgeBands(bands) : []
        return (
          <Paper
            key={strat.stratifierId}
            variant="outlined"
            sx={{ p: 2, mb: 2, opacity: disabled ? 0.6 : 1 }}
          >
            <Stack
              direction="row"
              sx={{
                justifyContent: "space-between",
                alignItems: "center",
                mb: 1
              }}>
              <Typography variant="subtitle2" sx={{
                fontWeight: 600
              }}>
                {t('stratifiers.label', { number: idx + 1 })}
              </Typography>
              <IconButton
                size="small"
                color="error"
                onClick={() => removeStratifier(idx)}
                disabled={disabled}
                aria-label={t('stratifiers.removeStratifier', { number: idx + 1 })}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
            <TextField
              label={t('stratifiers.description')} fullWidth size="small" sx={{ mb: 2 }}
              value={strat.description || ''}
              disabled={disabled}
              onChange={(e) => updateStratifier(idx, { ...strat, description: e.target.value })}
            />

            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
              <ToggleButtonGroup
                exclusive
                size="small"
                value={kind}
                disabled={disabled}
                aria-label={t('stratifiers.kind.label', { number: idx + 1 })}
                onChange={(_, next: StratifierKind | null) => { if (next) setKind(idx, strat, next) }}
              >
                <ToggleButton value="criteria">{t('stratifiers.kind.criteria')}</ToggleButton>
                <ToggleButton value="value">{t('stratifiers.kind.value')}</ToggleButton>
              </ToggleButtonGroup>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t(kind === 'value' ? 'stratifiers.kind.valueHint' : 'stratifiers.kind.criteriaHint')}
              </Typography>
            </Stack>

            {kind === 'criteria' ? (
              <Box sx={{ pointerEvents: disabled ? 'none' : 'auto' }}>
                <EcqmPopulationTreeEditor
                  label={t('stratifiers.criteria')}
                  tree={strat.criteria}
                  templates={templates}
                  modifiers={modifiers}
                  onUpdateTree={(tree) => updateStratifier(idx, { ...strat, criteria: tree })}
                />
              </Box>
            ) : (
              <Stack spacing={2}>
                <FormControl size="small" sx={{ maxWidth: 320 }} disabled={disabled}>
                  <InputLabel id={`strat-source-${strat.stratifierId}`}>{t('stratifiers.value.source')}</InputLabel>
                  <Select
                    labelId={`strat-source-${strat.stratifierId}`}
                    label={t('stratifiers.value.source')}
                    value={source}
                    inputProps={{ 'aria-label': t('stratifiers.value.source') }}
                    onChange={(e) => setSource(idx, strat, e.target.value as ValueStratifierSource)}
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
                              setBands(idx, strat, copy)
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
                                setBands(idx, strat, copy)
                              }}
                            />
                          ))}
                          <IconButton
                            size="small" disabled={disabled}
                            aria-label={t('stratifiers.value.removeBand', { number: bi + 1 })}
                            onClick={() => setBands(idx, strat, bands.filter((_, i) => i !== bi))}
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
                        setBands(idx, strat, [...bands, { label: defaultBandLabel(min, undefined), ...(min !== undefined ? { min } : {}) }])
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
            )}
          </Paper>
        )
      })}
    </Box>
  );
}
