import { describe, it, expect } from 'vitest'
import {
  actualValuesFromLastRun,
  alignExpectedValues,
  defaultExpectedValues,
  effectiveGroupId,
  measureNeedsStructuredExpectations,
  parseObservationInput,
  toLegacyPopulationMap,
} from '../testCaseExpectedValues'
import type { GroupDefinition, MeasureDefinition } from '../../types'

const pops = (...types: string[]) =>
  types.map((populationType) => ({ populationType, criteriaExpression: populationType }))

const proportionGroup = (groupId: string): GroupDefinition => ({
  groupId,
  populations: pops('initial-population', 'denominator', 'denominator-exclusion', 'numerator'),
})

const measure = (scoringType: string, ...groups: GroupDefinition[]): MeasureDefinition => ({
  id: 1,
  name: 'm',
  version: '1.0.0',
  scoringType,
  cqlContent: '',
  groupDefinitions: groups,
})

// PAT-228 — pure helpers behind the per-group expectation editor.
describe('testCaseExpectedValues', () => {
  it('numbers a group without an id the way the backend does', () => {
    expect(effectiveGroupId({ groupId: '' }, 0)).toBe('group-1')
    expect(effectiveGroupId({ groupId: '  ' }, 2)).toBe('group-3')
    expect(effectiveGroupId({ groupId: 'rate-a' }, 1)).toBe('rate-a')
  })

  describe('measureNeedsStructuredExpectations', () => {
    it('is false for a plain single-group proportion measure', () => {
      expect(measureNeedsStructuredExpectations(measure('proportion', proportionGroup('group-1')))).toBe(false)
    })

    it('is true for several groups — same-named populations collide in the flat map', () => {
      expect(
        measureNeedsStructuredExpectations(measure('proportion', proportionGroup('g1'), proportionGroup('g2'))),
      ).toBe(true)
    })

    it('is true for observation-bearing scoring types and for stratifiers', () => {
      expect(measureNeedsStructuredExpectations(
        measure('continuous-variable', { groupId: 'g', populations: pops('initial-population', 'measure-population') }),
      )).toBe(true)
      expect(measureNeedsStructuredExpectations(measure('proportion', {
        ...proportionGroup('g'),
        stratifiers: [{ stratifierId: 's1', criteriaExpression: 'Stratifier 1' }],
      }))).toBe(true)
    })
  })

  it('defaults a new test case to "in the base populations, nothing else"', () => {
    const values = defaultExpectedValues(measure('proportion', proportionGroup('group-1')))
    expect(values.groups).toEqual([{
      groupId: 'group-1',
      populations: { 'initial-population': 1, denominator: 1, 'denominator-exclusion': 0, numerator: 0 },
    }])
  })

  describe('alignExpectedValues', () => {
    const m = measure('proportion',
      { ...proportionGroup('g1'), stratifiers: [{ stratifierId: 's1', criteriaExpression: 'S1' }] },
      proportionGroup('g2'))

    it('keeps saved values, fills in a group the expectation does not have yet', () => {
      const aligned = alignExpectedValues(m, {
        groups: [{ groupId: 'g1', populations: { numerator: 1 }, observations: [30], stratifiers: { s1: 'true' } }],
      })
      expect(aligned.groups[0]).toEqual({
        groupId: 'g1',
        populations: { 'initial-population': 1, denominator: 1, 'denominator-exclusion': 0, numerator: 1 },
        observations: [30],
        stratifiers: { s1: 'true' },
      })
      expect(aligned.groups[1].groupId).toBe('g2')
      expect(aligned.groups[1].observations).toBeUndefined()
    })

    it('drops keys the measure no longer has — the backend would reject them', () => {
      const aligned = alignExpectedValues(m, {
        groups: [
          { groupId: 'g1', populations: { 'measure-population': 1 }, stratifiers: { gone: 'true' } },
          { groupId: 'removed-group', populations: { numerator: 1 } },
        ],
      })
      expect(aligned.groups.map((g) => g.groupId)).toEqual(['g1', 'g2'])
      expect(aligned.groups[0].populations).not.toHaveProperty('measure-population')
      expect(aligned.groups[0].stratifiers).toBeUndefined()
    })

    it('falls back to defaults when nothing is stored', () => {
      expect(alignExpectedValues(m, null)).toEqual(defaultExpectedValues(m))
    })
  })

  it('derives the flat boolean map from the first group only', () => {
    expect(toLegacyPopulationMap({
      groups: [
        { groupId: 'g1', populations: { 'initial-population': 1, numerator: 0 } },
        { groupId: 'g2', populations: { numerator: 1 } },
      ],
    })).toEqual({ 'initial-population': true, numerator: false })
  })

  describe('parseObservationInput', () => {
    it('accepts commas, whitespace and full-width separators', () => {
      expect(parseObservationInput('30, 45.5  60，7、8').values).toEqual([30, 45.5, 60, 7, 8])
    })

    it('reports tokens that are not numbers instead of silently dropping them', () => {
      const parsed = parseObservationInput('30, abc, 4x')
      expect(parsed.values).toEqual([30])
      expect(parsed.invalid).toEqual(['abc', '4x'])
    })

    it('treats empty input as "no values expected"', () => {
      expect(parseObservationInput('  ')).toEqual({ values: [], invalid: [] })
    })
  })

  describe('actualValuesFromLastRun', () => {
    it('reads the structured actual values out of the stored run result', () => {
      const json = JSON.stringify({ status: 'fail', actualValues: { groups: [{ groupId: 'g1', populations: { numerator: 0 } }] } })
      expect(actualValuesFromLastRun(json)?.groups[0].groupId).toBe('g1')
    })

    it('returns null for runs made before structured values existed, and for broken JSON', () => {
      expect(actualValuesFromLastRun(JSON.stringify({ status: 'pass' }))).toBeNull()
      expect(actualValuesFromLastRun('{not json')).toBeNull()
      expect(actualValuesFromLastRun(undefined)).toBeNull()
    })
  })
})
