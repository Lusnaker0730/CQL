import { useCallback } from 'react'
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
  const sdeNames = new Set(supplementalData.map((s) => s.name))

  const toggleStandard = useCallback((name: string, checked: boolean) => {
    if (checked) {
      onChange([...supplementalData, { name }])
    } else {
      onChange(supplementalData.filter((s) => s.name !== name))
    }
  }, [supplementalData, onChange])

  const addCustom = useCallback(() => {
    onChange([...supplementalData, {
      name: `Custom SDE ${supplementalData.length + 1}`,
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
    }])
  }, [supplementalData, onChange])

  const updateCustom = useCallback((idx: number, updated: SupplementalDataElement) => {
    const copy = [...supplementalData]
    copy[idx] = updated
    onChange(copy)
  }, [supplementalData, onChange])

  const removeCustom = useCallback((idx: number) => {
    const copy = [...supplementalData]
    copy.splice(idx, 1)
    onChange(copy)
  }, [supplementalData, onChange])

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      <Typography variant="h6" gutterBottom>Supplemental Data Elements</Typography>

      <TextField
        label="SDE Guidance" fullWidth multiline rows={2} sx={{ mb: 3 }}
        value={supplementalDataGuidance || ''}
        onChange={(e) => onGuidanceChange(e.target.value)}
      />

      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>Standard SDEs</Typography>
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
        <Typography variant="subtitle1" fontWeight={600}>Custom SDEs</Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={addCustom}>Add Custom</Button>
      </Stack>

      {supplementalData
        .map((sde, idx) => ({ sde, idx }))
        .filter(({ sde }) => !STANDARD_SDE.some((s) => s.name === sde.name))
        .map(({ sde, idx }) => (
          <Paper key={sde.name} variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <TextField
                label="SDE Name" size="small" sx={{ flex: 1, mr: 1 }}
                value={sde.name}
                onChange={(e) => updateCustom(idx, { ...sde, name: e.target.value })}
              />
              <IconButton size="small" color="error" onClick={() => removeCustom(idx)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Stack>
            {sde.criteria && (
              <EcqmPopulationTreeEditor
                label="SDE Criteria"
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
