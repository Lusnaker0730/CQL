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

export default function EcqmSdeTab({
  supplementalData, supplementalDataGuidance, templates, modifiers, onChange, onGuidanceChange,
}: Props) {
  const { t } = useTranslation('ecqm')
  const sdeNames = useMemo(() => new Set(supplementalData.map((s) => s.name)), [supplementalData])

  const toggleStandard = (name: string, checked: boolean) => {
    if (checked) {
      onChange([...supplementalData, { name }])
    } else {
      onChange(supplementalData.filter((s) => s.name !== name))
    }
  }

  const addCustom = () => {
    onChange([...supplementalData, {
      name: t('sde.defaultName', { number: supplementalData.length + 1 }),
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
    }])
  }

  const updateCustom = (idx: number, updated: SupplementalDataElement) => {
    const copy = [...supplementalData]
    copy[idx] = updated
    onChange(copy)
  }

  const removeCustom = (idx: number) => {
    const copy = [...supplementalData]
    copy.splice(idx, 1)
    onChange(copy)
  }

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
                  checked={sdeNames.has(sde.name)}
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

      {supplementalData
        .map((sde, idx) => ({ sde, idx }))
        .filter(({ sde }) => !STANDARD_SDE.some((s) => s.name === sde.name))
        .map(({ sde, idx }) => (
          <Paper key={sde.name} variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <TextField
                label={t('sde.sdeName')} size="small" sx={{ flex: 1, mr: 1 }}
                value={sde.name}
                onChange={(e) => updateCustom(idx, { ...sde, name: e.target.value })}
              />
              <IconButton size="small" color="error" onClick={() => removeCustom(idx)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
            {sde.criteria && (
              <EcqmPopulationTreeEditor
                label={t('sde.sdeCriteria')}
                tree={sde.criteria}
                templates={templates}
                modifiers={modifiers}
                onUpdateTree={(tree) => updateCustom(idx, { ...sde, criteria: tree })}
              />
            )}
          </Paper>
        ))}
    </Box>
  )
}
