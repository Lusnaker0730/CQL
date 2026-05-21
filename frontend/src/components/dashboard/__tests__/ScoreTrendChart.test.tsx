import { describe, it, expect } from 'vitest'
import { classifyScoring } from '../scoringFamily'

describe('classifyScoring', () => {
  it('treats proportion / ratio / composite as the proportion family (0–100% scale)', () => {
    expect(classifyScoring('proportion')).toBe('proportion')
    expect(classifyScoring('ratio')).toBe('proportion')
    expect(classifyScoring('composite')).toBe('proportion')
  })

  it('routes continuous-variable to its own family (auto Y-axis + unit)', () => {
    expect(classifyScoring('continuous-variable')).toBe('continuousVariable')
  })

  it('routes cohort to its own family (patient counts, not scores)', () => {
    expect(classifyScoring('cohort')).toBe('cohort')
  })

  it('treats unknown / missing scoringType as proportion (safe default)', () => {
    expect(classifyScoring(undefined)).toBe('proportion')
    expect(classifyScoring('')).toBe('proportion')
    expect(classifyScoring('something-new')).toBe('proportion')
  })
})
