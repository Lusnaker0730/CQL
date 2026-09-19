import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Autocomplete,
  Box,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import type {
  MeasureDefinition,
  TestCaseExpectedValues,
  TestCaseGroupValues,
} from '../../types'
import {
  effectiveGroupId,
  formatObservationInput,
  groupHasObservations,
  parseObservationInput,
} from '../../utils/testCaseExpectedValues'

interface TestCaseExpectedValuesEditorProps {
  measure: MeasureDefinition
  value: TestCaseExpectedValues
  onChange: (next: TestCaseExpectedValues) => void
  /** Reports whether every observation field currently parses — the editor blocks saving otherwise. */
  onValidityChange?: (valid: boolean) => void
  readOnly?: boolean
}

const STRATUM_OPTIONS = ['true', 'false']

/**
 * Per-group expectations of a test case (PAT-228): effective population membership, measure
 * observation values and the expected stratum per stratifier. `value` is expected to be aligned
 * with the measure already (`alignExpectedValues`), one entry per group in measure order.
 */
export default function TestCaseExpectedValuesEditor({
  measure,
  value,
  onChange,
  onValidityChange,
  readOnly,
}: TestCaseExpectedValuesEditorProps) {
  const { t } = useTranslation('measures')
  const groups = useMemo(() => measure.groupDefinitions ?? [], [measure.groupDefinitions])

  // Raw text per group so a half-typed number ("4.") is not reformatted under the cursor.
  const [observationText, setObservationText] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {}
    for (const g of value.groups) initial[g.groupId] = formatObservationInput(g.observations)
    return initial
  })
  const [invalidTokens, setInvalidTokens] = useState<Record<string, string[]>>({})

  const hasInvalid = Object.values(invalidTokens).some((tokens) => tokens.length > 0)
  useEffect(() => {
    onValidityChange?.(!hasInvalid)
  }, [hasInvalid, onValidityChange])

  const updateGroup = useCallback(
    (groupId: string, patch: (group: TestCaseGroupValues) => TestCaseGroupValues) => {
      onChange({ groups: value.groups.map((g) => (g.groupId === groupId ? patch(g) : g)) })
    },
    [onChange, value.groups],
  )

  const setPopulation = (groupId: string, type: string, inPopulation: boolean) => {
    updateGroup(groupId, (g) => ({
      ...g,
      populations: { ...(g.populations ?? {}), [type]: inPopulation ? 1 : 0 },
    }))
  }

  const setAssertObservations = (groupId: string, assert: boolean) => {
    setInvalidTokens((prev) => ({ ...prev, [groupId]: [] }))
    if (assert) {
      const parsed = parseObservationInput(observationText[groupId] ?? '')
      setInvalidTokens((prev) => ({ ...prev, [groupId]: parsed.invalid }))
      updateGroup(groupId, (g) => ({ ...g, observations: parsed.values }))
    } else {
      updateGroup(groupId, (g) => {
        const next = { ...g }
        delete next.observations
        return next
      })
    }
  }

  const setObservations = (groupId: string, text: string) => {
    setObservationText((prev) => ({ ...prev, [groupId]: text }))
    const parsed = parseObservationInput(text)
    setInvalidTokens((prev) => ({ ...prev, [groupId]: parsed.invalid }))
    updateGroup(groupId, (g) => ({ ...g, observations: parsed.values }))
  }

  const setStratum = (groupId: string, stratifierId: string, stratum: string) => {
    updateGroup(groupId, (g) => {
      const stratifiers = { ...(g.stratifiers ?? {}) }
      if (stratum.trim()) stratifiers[stratifierId] = stratum.trim()
      else delete stratifiers[stratifierId]
      const next: TestCaseGroupValues = { ...g, stratifiers }
      if (Object.keys(stratifiers).length === 0) delete next.stratifiers
      return next
    })
  }

  if (groups.length === 0) {
    return <Alert severity="info">{t('testCaseEditor.structured.noGroups')}</Alert>
  }

  return (
    <Stack spacing={1.5}>
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
        {t('testCaseEditor.structured.populationsHint')} {t('testCaseEditor.structured.patientBasedNote')}
      </Typography>
      {groups.map((group, index) => {
        const groupId = effectiveGroupId(group, index)
        const expected = value.groups.find((g) => g.groupId === groupId)
        if (!expected) return null
        const assertObservations = expected.observations != null
        const invalid = invalidTokens[groupId] ?? []

        return (
          <Paper key={groupId} variant="outlined" sx={{ p: 1.5 }} data-testid={`expected-group-${groupId}`}>
            <Stack spacing={1}>
              <Box>
                <Typography variant="subtitle2">
                  {t('testCaseEditor.structured.group', { id: groupId })}
                </Typography>
                {group.description && (
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    {group.description}
                  </Typography>
                )}
              </Box>

              <Stack spacing={0.25}>
                {(group.populations ?? []).map((pop) => (
                  <FormControlLabel
                    key={pop.populationType}
                    control={
                      <Switch
                        size="small"
                        disabled={readOnly}
                        checked={(expected.populations?.[pop.populationType] ?? 0) > 0}
                        onChange={(e) => setPopulation(groupId, pop.populationType, e.target.checked)}
                        slotProps={{ input: { 'aria-label': `${groupId} ${pop.populationType}` } }}
                      />
                    }
                    label={
                      <Typography variant="body2">
                        {t(`testCaseEditor.populationTypes.${pop.populationType}`, pop.populationType)}
                      </Typography>
                    }
                  />
                ))}
              </Stack>

              {groupHasObservations(measure, group) && (
                <Box>
                  <FormControlLabel
                    control={
                      <Switch
                        size="small"
                        disabled={readOnly}
                        checked={assertObservations}
                        onChange={(e) => setAssertObservations(groupId, e.target.checked)}
                        slotProps={{ input: { 'aria-label': `${groupId} assert observations` } }}
                      />
                    }
                    label={
                      <Typography variant="body2">{t('testCaseEditor.structured.assertObservations')}</Typography>
                    }
                  />
                  {assertObservations && (
                    <TextField
                      size="small"
                      fullWidth
                      disabled={readOnly}
                      label={t('testCaseEditor.structured.observations')}
                      placeholder={t('testCaseEditor.structured.observationsPlaceholder')}
                      value={observationText[groupId] ?? ''}
                      onChange={(e) => setObservations(groupId, e.target.value)}
                      error={invalid.length > 0}
                      helperText={
                        invalid.length > 0
                          ? t('testCaseEditor.structured.observationsInvalid', { tokens: invalid.join(', ') })
                          : t('testCaseEditor.structured.observationsHint')
                      }
                      slotProps={{
                        inputLabel: { shrink: true },
                        htmlInput: { 'aria-label': `${groupId} observation values` },
                      }}
                      sx={{ mt: 0.5 }}
                    />
                  )}
                </Box>
              )}

              {(group.stratifiers?.length ?? 0) > 0 && (
                <Stack spacing={1}>
                  <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                    {t('testCaseEditor.structured.stratifiers')}
                  </Typography>
                  {group.stratifiers!.map((stratifier) => (
                    <Autocomplete
                      key={stratifier.stratifierId}
                      freeSolo
                      size="small"
                      disabled={readOnly}
                      options={STRATUM_OPTIONS}
                      value={expected.stratifiers?.[stratifier.stratifierId] ?? ''}
                      onInputChange={(_, stratum) => setStratum(groupId, stratifier.stratifierId, stratum)}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          label={stratifier.description || stratifier.stratifierId}
                          helperText={t('testCaseEditor.structured.stratifierHint')}
                          slotProps={{
                            ...params.slotProps,
                            inputLabel: { shrink: true },
                            htmlInput: {
                              ...params.slotProps.htmlInput,
                              'aria-label': `${groupId} stratifier ${stratifier.stratifierId}`,
                            },
                          }}
                        />
                      )}
                    />
                  ))}
                </Stack>
              )}
            </Stack>
          </Paper>
        )
      })}
    </Stack>
  )
}
