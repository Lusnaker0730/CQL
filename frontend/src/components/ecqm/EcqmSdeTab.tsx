import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Checkbox, FormControlLabel, IconButton, Paper,
  Stack, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { StratifierKind, SupplementalDataElement, SupplementalDataUsage } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { STANDARD_SDE, createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'
import ValueSourceEditor from './ValueSourceEditor'

/** QM IG 3.19: a risk adjustment factor's define SHOULD be named "RAF …". */
const RAF_PREFIX = 'RAF '

interface Props {
  supplementalData: SupplementalDataElement[]
  supplementalDataGuidance?: string
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (sde: SupplementalDataElement[]) => void
  onGuidanceChange: (guidance: string) => void
}

// PAT-115: legacy SDE rows (pre-this-patch) don't carry `custom` flag.
// Treat them as custom iff their name does NOT exactly match a standard SDE.
// Once the user touches them, the flag is persisted and the ambiguity ends.
function isCustomSde(sde: SupplementalDataElement): boolean {
  if (sde.custom === true) return true
  if (sde.custom === false) return false
  return !STANDARD_SDE.some((s) => s.name === sde.name)
}

function newCustomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return 'sde-' + crypto.randomUUID()
  }
  return 'sde-' + Date.now() + '-' + Math.random().toString(36).slice(2)
}

export default function EcqmSdeTab({
  supplementalData, supplementalDataGuidance, templates, modifiers, onChange, onGuidanceChange,
}: Props) {
  const { t } = useTranslation('ecqm')

  // Only count standard SDE slots as "checked" — a custom row that happens to
  // share a name with a standard SDE should NOT tick the standard checkbox.
  const standardCheckedNames = useMemo(
    () => new Set(supplementalData.filter((s) => !isCustomSde(s)).map((s) => s.name)),
    [supplementalData]
  )

  const toggleStandard = (name: string, checked: boolean) => {
    if (checked) {
      onChange([...supplementalData, { name, custom: false }])
    } else {
      // Only remove the standard slot, keep any custom row that shares the name.
      onChange(supplementalData.filter((s) => !(s.name === name && !isCustomSde(s))))
    }
  }

  const addCustom = () => {
    onChange([...supplementalData, {
      id: newCustomId(),
      custom: true,
      name: t('sde.defaultName', { number: supplementalData.length + 1 }),
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
    }])
  }

  const updateCustom = (idx: number, updated: SupplementalDataElement) => {
    const copy = [...supplementalData]
    // Preserve/assign a stable id on first edit of a legacy row so the React
    // key below never depends on the mutable name field.
    if (!updated.id) updated = { ...updated, id: newCustomId() }
    if (updated.custom !== true) updated = { ...updated, custom: true }
    copy[idx] = updated
    onChange(copy)
  }

  // PAT-234: switching a row to a risk adjustment factor renames it "RAF …" while it still has
  // the default name; a name the author wrote is left alone (the backend warns at publish).
  const setUsage = (idx: number, sde: SupplementalDataElement, usage: SupplementalDataUsage) => {
    let name = sde.name
    const isDefaultName = /^Custom SDE \d+$/.test(name) || name === t('sde.defaultName', { number: idx + 1 })
    if (usage === 'risk-adjustment-factor' && !name.startsWith(RAF_PREFIX) && (isDefaultName || name.trim() === '')) {
      name = RAF_PREFIX + (name.trim() || 'Factor')
    }
    updateCustom(idx, { ...sde, usage, name })
  }

  const setKind = (idx: number, sde: SupplementalDataElement, kind: StratifierKind) => {
    if (kind === 'value') updateCustom(idx, { ...sde, kind, value: sde.value ?? { source: 'gender' } })
    else updateCustom(idx, { ...sde, kind: 'criteria' })
  }

  const removeCustom = (idx: number) => {
    const copy = [...supplementalData]
    copy.splice(idx, 1)
    onChange(copy)
  }

  const customRows = useMemo(
    () => supplementalData
      .map((sde, idx) => ({ sde, idx }))
      .filter(({ sde }) => isCustomSde(sde)),
    [supplementalData]
  )

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      <Typography variant="h6" gutterBottom>{t('sde.title')}</Typography>
      <TextField
        label={t('sde.guidance')} fullWidth multiline rows={2} sx={{ mb: 3 }}
        value={supplementalDataGuidance || ''}
        onChange={(e) => onGuidanceChange(e.target.value)}
      />
      <Typography
        variant="subtitle1"
        sx={{
          fontWeight: 600,
          mb: 1
        }}>{t('sde.standardSdes')}</Typography>
      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack spacing={0.5}>
          {STANDARD_SDE.map((sde) => (
            <FormControlLabel
              key={sde.name}
              control={
                <Checkbox
                  checked={standardCheckedNames.has(sde.name)}
                  onChange={(e) => toggleStandard(sde.name, e.target.checked)}
                />
              }
              label={`${sde.name} (${sde.oid})`}
            />
          ))}
        </Stack>
      </Paper>
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 1
        }}>
        <Typography variant="subtitle1" sx={{
          fontWeight: 600
        }}>{t('sde.customSdes')}</Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={addCustom}>{t('sde.addCustom')}</Button>
      </Stack>
      {customRows.map(({ sde, idx }) => (
        // PAT-115 Bug #1 fix: use a stable id for the React key, NOT the name.
        // Previously `key={sde.name}` caused the <Paper> (and its child TextField)
        // to remount on every keystroke in the name field → focus loss → users
        // could only type one character at a time.
        (<Paper key={sde.id ?? `sde-idx-${idx}`} variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Stack
            direction="row"
            sx={{
              justifyContent: "space-between",
              alignItems: "center",
              mb: 1
            }}>
            <TextField
              label={t('sde.sdeName')} size="small" sx={{ flex: 1, mr: 1 }}
              value={sde.name}
              onChange={(e) => updateCustom(idx, { ...sde, name: e.target.value })}
            />
            <IconButton
              size="small"
              color="error"
              onClick={() => removeCustom(idx)}
              aria-label={t('sde.removeCustom', { name: sde.name })}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2, flexWrap: 'wrap', rowGap: 1 }}>
            <ToggleButtonGroup
              exclusive size="small"
              value={sde.usage ?? 'supplemental-data'}
              aria-label={t('sde.usage.label', { name: sde.name })}
              onChange={(_, next: SupplementalDataUsage | null) => { if (next) setUsage(idx, sde, next) }}
            >
              <ToggleButton value="supplemental-data">{t('sde.usage.supplementalData')}</ToggleButton>
              <ToggleButton value="risk-adjustment-factor">{t('sde.usage.riskAdjustment')}</ToggleButton>
            </ToggleButtonGroup>
            <ToggleButtonGroup
              exclusive size="small"
              value={sde.kind === 'value' ? 'value' : 'criteria'}
              aria-label={t('sde.kind.label', { name: sde.name })}
              onChange={(_, next: StratifierKind | null) => { if (next) setKind(idx, sde, next) }}
            >
              <ToggleButton value="criteria">{t('stratifiers.kind.criteria')}</ToggleButton>
              <ToggleButton value="value">{t('stratifiers.kind.value')}</ToggleButton>
            </ToggleButtonGroup>
          </Stack>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
            {t(sde.usage === 'risk-adjustment-factor' ? 'sde.usage.riskAdjustmentHint' : 'sde.usage.supplementalDataHint')}
            {sde.usage === 'risk-adjustment-factor' && !sde.name.startsWith(RAF_PREFIX) && ` ${t('sde.usage.rafNameHint')}`}
          </Typography>
          {sde.kind === 'value' ? (
            <ValueSourceEditor
              value={sde.value}
              idSuffix={sde.id ?? `sde-idx-${idx}`}
              onChange={(value) => updateCustom(idx, { ...sde, value })}
            />
          ) : (
            /* PAT-115 Bug #3 fix: render the criteria editor unconditionally.
               Legacy rows may arrive without criteria; we lazy-materialise an
               empty conjunction group so the author can define conditions. */
            <EcqmPopulationTreeEditor
              label={t('sde.sdeCriteria')}
              tree={sde.criteria ?? (createEmptyConjunctionGroup() as ConjunctionGroupType)}
              templates={templates}
              modifiers={modifiers}
              onUpdateTree={(tree) => updateCustom(idx, { ...sde, criteria: tree })}
            />
          )}
        </Paper>)
      ))}
    </Box>
  );
}
