import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, IconButton, Paper, Stack, TextField, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { StratifierElement } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'

interface Props {
  stratifiers: StratifierElement[]
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  onChange: (stratifiers: StratifierElement[]) => void
}

function newStratId() {
  return 'strat-' + Date.now().toString(36)
}

export default function EcqmStratifiersTab({ stratifiers, templates, modifiers, onChange }: Props) {
  const { t } = useTranslation('ecqm')
  const addStratifier = useCallback(() => {
    onChange([...stratifiers, {
      stratifierId: newStratId(),
      description: '',
      criteria: createEmptyConjunctionGroup() as ConjunctionGroupType,
    }])
  }, [stratifiers, onChange])

  const updateStratifier = useCallback((idx: number, updated: StratifierElement) => {
    const copy = [...stratifiers]
    copy[idx] = updated
    onChange(copy)
  }, [stratifiers, onChange])

  const removeStratifier = useCallback((idx: number) => {
    const copy = [...stratifiers]
    copy.splice(idx, 1)
    onChange(copy)
  }, [stratifiers, onChange])

  return (
    <Box sx={{ p: 3, maxWidth: 900 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">{t('stratifiers.title')}</Typography>
        <Button startIcon={<AddIcon />} onClick={addStratifier}>{t('stratifiers.addStratifier')}</Button>
      </Stack>

      {stratifiers.length === 0 && (
        <Typography color="text.secondary">{t('stratifiers.emptyState')}</Typography>
      )}

      {stratifiers.map((strat, idx) => (
        <Paper key={strat.stratifierId} variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2" fontWeight={600}>
              {t('stratifiers.label', { number: idx + 1 })}
            </Typography>
            <IconButton size="small" color="error" onClick={() => removeStratifier(idx)}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
          <TextField
            label={t('stratifiers.description')} fullWidth size="small" sx={{ mb: 2 }}
            value={strat.description || ''}
            onChange={(e) => updateStratifier(idx, { ...strat, description: e.target.value })}
          />
          <EcqmPopulationTreeEditor
            label={t('stratifiers.criteria')}
            tree={strat.criteria}
            templates={templates}
            modifiers={modifiers}
            onUpdateTree={(tree) => updateStratifier(idx, { ...strat, criteria: tree })}
          />
        </Paper>
      ))}
    </Box>
  )
}
