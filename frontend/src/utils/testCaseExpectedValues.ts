import type {
  GroupDefinition,
  MeasureDefinition,
  TestCaseExpectedValues,
  TestCaseGroupValues,
} from '../types'

/**
 * Pure helpers for the structured expected values of a measure test case (PAT-228).
 * Kept out of the component files so they can be unit-tested and so the component modules
 * only export components (react-refresh lint rule).
 */

const OBSERVATION_SCORING_TYPES = new Set(['continuous-variable', 'ratio'])

/** Same numbering the backend uses for a group without an id (`TestCaseService.effectiveGroupId`). */
export function effectiveGroupId(group: GroupDefinition, index: number): string {
  return group.groupId && group.groupId.trim() ? group.groupId : `group-${index + 1}`
}

export function groupHasObservations(measure: MeasureDefinition, group: GroupDefinition): boolean {
  return (group.observations?.length ?? 0) > 0 || OBSERVATION_SCORING_TYPES.has(measure.scoringType)
}

/**
 * True when the flat boolean map cannot describe this measure: several groups (same-named
 * populations collide), measure observations, or stratifiers.
 */
export function measureNeedsStructuredExpectations(measure: MeasureDefinition): boolean {
  const groups = measure.groupDefinitions ?? []
  if (groups.length > 1) return true
  return groups.some(
    (g) => groupHasObservations(measure, g) || (g.stratifiers?.length ?? 0) > 0,
  )
}

/** Populations a patient is in by default for a new test case; everything else starts at 0. */
const DEFAULT_IN = new Set(['initial-population', 'denominator', 'measure-population'])

export function defaultExpectedValues(measure: MeasureDefinition): TestCaseExpectedValues {
  const groups = measure.groupDefinitions ?? []
  return {
    groups: groups.map((group, index) => {
      const populations: Record<string, number> = {}
      for (const pop of group.populations ?? []) {
        if (!pop.populationType) continue
        populations[pop.populationType] = DEFAULT_IN.has(pop.populationType) ? 1 : 0
      }
      return { groupId: effectiveGroupId(group, index), populations }
    }),
  }
}

/**
 * Lines the stored expectation up with the measure as it is NOW: groups the measure no longer
 * has are dropped, new groups / populations get defaults, stale population and stratifier keys
 * are removed (the backend rejects keys the measure does not have).
 */
export function alignExpectedValues(
  measure: MeasureDefinition,
  stored: TestCaseExpectedValues | null | undefined,
): TestCaseExpectedValues {
  const defaults = defaultExpectedValues(measure)
  if (!stored?.groups?.length) return defaults
  const storedById = new Map(stored.groups.map((g) => [g.groupId, g]))
  const measureGroups = measure.groupDefinitions ?? []

  return {
    groups: defaults.groups.map((fallback, index) => {
      const saved = storedById.get(fallback.groupId)
      if (!saved) return fallback
      const group = measureGroups[index]

      const populations: Record<string, number> = {}
      for (const type of Object.keys(fallback.populations ?? {})) {
        populations[type] = saved.populations?.[type] ?? fallback.populations![type]
      }

      const stratifierIds = new Set((group.stratifiers ?? []).map((s) => s.stratifierId))
      const stratifiers: Record<string, string> = {}
      for (const [id, value] of Object.entries(saved.stratifiers ?? {})) {
        if (stratifierIds.has(id)) stratifiers[id] = value
      }

      const aligned: TestCaseGroupValues = { groupId: fallback.groupId, populations }
      if (saved.observations != null) aligned.observations = saved.observations
      if (Object.keys(stratifiers).length > 0) aligned.stratifiers = stratifiers
      return aligned
    }),
  }
}

/**
 * Flat boolean view of the first group. Still sent as `expectedPopulations` so list chips and
 * older API consumers keep showing something sensible; the run itself ignores it once
 * `expectedValues` is present.
 */
export function toLegacyPopulationMap(values: TestCaseExpectedValues): Record<string, boolean> {
  const first = values.groups[0]
  const out: Record<string, boolean> = {}
  for (const [type, count] of Object.entries(first?.populations ?? {})) {
    out[type] = count > 0
  }
  return out
}

export interface ObservationParseResult {
  values: number[]
  /** Tokens that are not finite numbers — the caller shows them and blocks saving. */
  invalid: string[]
}

/** "30, 45.5 60" → [30, 45.5, 60]. Accepts commas, whitespace and full-width commas. */
export function parseObservationInput(text: string): ObservationParseResult {
  const values: number[] = []
  const invalid: string[] = []
  for (const token of text.split(/[\s,，、]+/)) {
    if (!token) continue
    const n = Number(token)
    if (Number.isFinite(n)) values.push(n)
    else invalid.push(token)
  }
  return { values, invalid }
}

export function formatObservationInput(values: number[] | null | undefined): string {
  return (values ?? []).join(', ')
}

/** Structured actual values of the last run, if the stored run result has them. */
export function actualValuesFromLastRun(lastRunResultJson: string | undefined | null): TestCaseExpectedValues | null {
  if (!lastRunResultJson) return null
  try {
    const parsed = JSON.parse(lastRunResultJson) as { actualValues?: TestCaseExpectedValues }
    return parsed.actualValues?.groups?.length ? parsed.actualValues : null
  } catch {
    return null
  }
}
