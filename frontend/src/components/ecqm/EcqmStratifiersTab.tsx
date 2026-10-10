import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert, Box, Button, IconButton, Paper, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import type { StratifierComponentElement, StratifierElement, StratifierKind } from '../../types/ecqm'
import type { ConjunctionGroup as ConjunctionGroupType, FormTemplateCategory, ModifierDefinition } from '../../types/authoring'
import { createEmptyConjunctionGroup, DEFAULT_AGE_BANDS } from '../../constants/ecqmConstants'
import { validateStratifierComponents } from '../../utils/stratifierComponents'
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

/** How the tab shows a stratifier: the two single-expression kinds, or PAT-235's component mode. */
type Mode = StratifierKind | 'components'

function newStratId() {
  return 'strat-' + Date.now().toString(36)
}

function modeOf(strat: StratifierElement): Mode {
  if (strat.components && strat.components.length > 0) return 'components'
  return strat.kind === 'value' ? 'value' : 'criteria'
}

/** The pair most authors want first: sex × age band. */
function defaultComponents(): StratifierComponentElement[] {
  return [
    { code: 'sex', kind: 'value', value: { source: 'gender' } },
    { code: 'age', kind: 'value', value: { source: 'ageBands', bands: DEFAULT_AGE_BANDS.map((b) => ({ ...b })) } },
  ]
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
  // PAT-235: component mode keeps the single-expression fields too, so nothing is lost by
  // switching around; the backend ignores whatever the mode does not use.
  const setMode = (idx: number, strat: StratifierElement, mode: Mode) => {
    if (mode === 'components') {
      updateStratifier(idx, { ...strat, components: strat.components?.length ? strat.components : defaultComponents() })
    } else if (mode === 'value') {
      updateStratifier(idx, { ...strat, kind: 'value', value: strat.value ?? { source: 'gender' }, components: undefined })
    } else {
      updateStratifier(idx, { ...strat, kind: 'criteria', components: undefined })
    }
  }

  const setComponents = (idx: number, strat: StratifierElement, components: StratifierComponentElement[]) => {
    updateStratifier(idx, { ...strat, components })
  }

  const renderExpressionEditor = (
    element: { kind?: StratifierKind; criteria?: ConjunctionGroupType; value?: StratifierElement['value'] },
    idSuffix: string,
    onUpdate: (patch: Partial<StratifierComponentElement>) => void,
  ) => (
    element.kind === 'value' ? (
      <ValueSourceEditor
        value={element.value}
        idSuffix={idSuffix}
        disabled={disabled}
        onChange={(value) => onUpdate({ value })}
      />
    ) : (
      <Box sx={{ pointerEvents: disabled ? 'none' : 'auto' }}>
        <EcqmPopulationTreeEditor
          label={t('stratifiers.criteria')}
          tree={element.criteria ?? (createEmptyConjunctionGroup() as ConjunctionGroupType)}
          templates={templates}
          modifiers={modifiers}
          onUpdateTree={(tree) => onUpdate({ criteria: tree })}
        />
      </Box>
    )
  )

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
        const mode = modeOf(strat)
        const components = strat.components ?? []
        const componentProblems = mode === 'components' ? validateStratifierComponents(components) : []
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
                value={mode}
                disabled={disabled}
                aria-label={t('stratifiers.kind.label', { number: idx + 1 })}
                onChange={(_, next: Mode | null) => { if (next) setMode(idx, strat, next) }}
              >
                <ToggleButton value="criteria">{t('stratifiers.kind.criteria')}</ToggleButton>
                <ToggleButton value="value">{t('stratifiers.kind.value')}</ToggleButton>
                <ToggleButton value="components">{t('stratifiers.kind.components')}</ToggleButton>
              </ToggleButtonGroup>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t(mode === 'components' ? 'stratifiers.kind.componentsHint'
                  : mode === 'value' ? 'stratifiers.kind.valueHint' : 'stratifiers.kind.criteriaHint')}
              </Typography>
            </Stack>

            {mode === 'components' ? (
              <Stack spacing={2}>
                {components.map((component, ci) => (
                  <Paper key={ci} variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }} data-testid="stratifier-component">
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
                      <TextField
                        size="small" label={t('stratifiers.component.code')} value={component.code ?? ''}
                        disabled={disabled} sx={{ width: 180 }}
                        slotProps={{ htmlInput: { 'aria-label': t('stratifiers.component.codeAria', { number: ci + 1 }) } }}
                        onChange={(e) => {
                          const copy = components.map((c) => ({ ...c }))
                          copy[ci].code = e.target.value
                          setComponents(idx, strat, copy)
                        }}
                      />
                      <ToggleButtonGroup
                        exclusive size="small"
                        value={component.kind === 'value' ? 'value' : 'criteria'}
                        disabled={disabled}
                        aria-label={t('stratifiers.component.kindAria', { number: ci + 1 })}
                        onChange={(_, next: StratifierKind | null) => {
                          if (!next) return
                          const copy = components.map((c) => ({ ...c }))
                          copy[ci] = next === 'value'
                            ? { ...copy[ci], kind: 'value', value: copy[ci].value ?? { source: 'gender' } }
                            : { ...copy[ci], kind: 'criteria' }
                          setComponents(idx, strat, copy)
                        }}
                      >
                        <ToggleButton value="criteria">{t('stratifiers.kind.criteria')}</ToggleButton>
                        <ToggleButton value="value">{t('stratifiers.kind.value')}</ToggleButton>
                      </ToggleButtonGroup>
                      <Box sx={{ flexGrow: 1 }} />
                      <IconButton
                        size="small" color="error" disabled={disabled}
                        aria-label={t('stratifiers.component.remove', { number: ci + 1 })}
                        onClick={() => setComponents(idx, strat, components.filter((_, i) => i !== ci))}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                    {renderExpressionEditor(component, `${strat.stratifierId}-${ci}`, (patch) => {
                      const copy = components.map((c) => ({ ...c }))
                      copy[ci] = { ...copy[ci], ...patch }
                      setComponents(idx, strat, copy)
                    })}
                  </Paper>
                ))}
                <Box>
                  <Button
                    size="small" startIcon={<AddIcon />} disabled={disabled}
                    onClick={() => setComponents(idx, strat, [...components, { code: '', kind: 'criteria', criteria: createEmptyConjunctionGroup() as ConjunctionGroupType }])}
                  >
                    {t('stratifiers.component.add')}
                  </Button>
                  {componentProblems.length > 0 && (
                    <Alert severity="warning" sx={{ mt: 1 }}>
                      {componentProblems.map((p) => t(`stratifiers.componentErrors.${p}`)).join(' ')}
                    </Alert>
                  )}
                </Box>
              </Stack>
            ) : mode === 'criteria' ? (
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
