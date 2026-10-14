import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Autocomplete, Stack, TextField, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { measureApi } from '../../api'
import StatusChip from '../common/StatusChip'
import { STALE_1M } from '../../constants/queryConstants'
import { MEASURE_STATUS, SCORING_TYPE } from '../../constants/measureConstants'
import type { MeasureDefinition } from '../../types'

interface Props {
  /** The composite itself — never offered as its own component. */
  selfId?: number
  componentMeasureIds: number[]
  componentWeights?: Record<string, number>
  method: string
  disabled?: boolean
  onChange: (ids: number[], weights: Record<string, number> | undefined) => void
}

const optionLabel = (m: MeasureDefinition) => `${m.title || m.name}${m.version ? ` v${m.version}` : ''}`

/**
 * PAT-259 — the component measures of a composite: a multi-select over this tenant's non-composite
 * measures (order kept), plus a weight per component when the method is `weighted`. Components and
 * weights are measure logic, so the caller disables the editor outside draft.
 */
export default function CompositeComponentsEditor({ selfId, componentMeasureIds, componentWeights, method, disabled, onChange }: Props) {
  const { t } = useTranslation('measures')
  const { data: measures = [], isLoading } = useQuery({
    queryKey: ['measures', 'composite-candidates'],
    queryFn: () => measureApi.getMeasures(),
    staleTime: STALE_1M,
  })
  const candidates = useMemo(
    () => measures.filter((m) => m.id != null && m.id !== selfId && m.scoringType !== SCORING_TYPE.COMPOSITE),
    [measures, selfId],
  )
  // a component the list does not know (e.g. deleted) still shows by id, so it can be removed
  const selected = useMemo(
    () => componentMeasureIds.map((id) => candidates.find((m) => m.id === id)
      ?? ({ id, name: `#${id}`, version: '', status: '', scoringType: '' } as MeasureDefinition)),
    [componentMeasureIds, candidates],
  )
  const weighted = method === 'weighted'

  const setWeight = (id: number, raw: string) => {
    const next: Record<string, number> = { ...(componentWeights ?? {}) }
    if (raw === '') delete next[String(id)]
    else {
      const value = Number(raw)
      if (Number.isFinite(value)) next[String(id)] = value
    }
    onChange(componentMeasureIds, Object.keys(next).length > 0 ? next : undefined)
  }

  return (
    <Stack spacing={1.5}>
      <Autocomplete
        multiple
        disabled={disabled}
        loading={isLoading}
        options={candidates}
        value={selected}
        isOptionEqualToValue={(a, b) => a.id === b.id}
        getOptionLabel={optionLabel}
        onChange={(_, next) => {
          const ids = next.map((m) => m.id!).filter((id): id is number => id != null)
          const kept = componentWeights
            ? Object.fromEntries(Object.entries(componentWeights).filter(([key]) => ids.includes(Number(key))))
            : undefined
          onChange(ids, kept && Object.keys(kept).length > 0 ? kept : undefined)
        }}
        renderOption={(props, m) => {
          const { key, ...rest } = props as typeof props & { key: string }
          return (
            <li key={key} {...rest}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <span>{optionLabel(m)}</span>
                {m.status && <StatusChip status={m.status} />}
              </Stack>
            </li>
          )
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            size="small"
            label={t('details.composite.components')}
            error={componentMeasureIds.length > 0 && componentMeasureIds.length < 2}
            helperText={componentMeasureIds.length < 2 ? t('details.composite.needTwo') : t('details.composite.componentsHelper')}
          />
        )}
      />
      {weighted && selected.length > 0 && (
        <Stack spacing={1}>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t('details.composite.weightHelper')}</Typography>
          {selected.map((m) => (
            <Stack key={m.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flex: 1 }}>{optionLabel(m)}</Typography>
              <TextField
                size="small"
                type="number"
                sx={{ width: 140 }}
                disabled={disabled}
                label={t('details.composite.weight')}
                placeholder="1"
                value={componentWeights?.[String(m.id)] ?? ''}
                onChange={(e) => setWeight(m.id!, e.target.value)}
                slotProps={{ htmlInput: { min: 0, step: 0.1, 'data-testid': `component-weight-${m.id}` } }}
              />
            </Stack>
          ))}
        </Stack>
      )}
      {selected.some((m) => m.status && m.status !== MEASURE_STATUS.ACTIVE) && (
        <Typography variant="caption" sx={{ color: 'warning.main' }}>{t('details.composite.inactiveWarning')}</Typography>
      )}
    </Stack>
  )
}
