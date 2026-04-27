import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Checkbox, FormControlLabel, IconButton, Paper,
  Stack, TextField, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { SupplementalDataElement } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { STANDARD_SDE, createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'

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

      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>{t('sde.standardSdes')}</Typography>
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

      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="subtitle1" fontWeight={600}>{t('sde.customSdes')}</Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={addCustom}>{t('sde.addCustom')}</Button>
      </Stack>

      {customRows.map(({ sde, idx }) => (
        // PAT-115 Bug #1 fix: use a stable id for the React key, NOT the name.
        // Previously `key={sde.name}` caused the <Paper> (and its child TextField)
        // to remount on every keystroke in the name field → focus loss → users
        // could only type one character at a time.
        <Paper key={sde.id ?? `sde-idx-${idx}`} variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
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
          {/* PAT-115 Bug #3 fix: render the criteria editor unconditionally.
              Legacy rows may arrive without criteria; we lazy-materialise an
              empty conjunction group so the author can define conditions. */}
          <EcqmPopulationTreeEditor
            label={t('sde.sdeCriteria')}
            tree={sde.criteria ?? (createEmptyConjunctionGroup() as ConjunctionGroupType)}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => updateCustom(idx, { ...sde, criteria: tree })}
          />
        </Paper>
      ))}
    </Box>
  )
}
