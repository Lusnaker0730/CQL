import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert, Box, Button, IconButton, Paper, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { StratifierElement, StratifierKind } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { createEmptyConjunctionGroup } from '../../constants/ecqmConstants'
import EcqmPopulationTreeEditor from './EcqmPopulationTreeEditor'
import ValueSourceEditor from './ValueSourceEditor'

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
              <ValueSourceEditor
                value={strat.value}
                idSuffix={strat.stratifierId}
                disabled={disabled}
                onChange={(value) => updateStratifier(idx, { ...strat, value })}
              />
            )}
          </Paper>
        )
      })}
    </Box>
  );
}
