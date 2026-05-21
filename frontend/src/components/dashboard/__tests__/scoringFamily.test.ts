import { describe, it, expect } from 'vitest'
import { classifyScoring } from '../scoringFamily'

describe('classifyScoring', () => {
  it('treats undefined / null / empty as proportion (safe default)', () => {
    expect(classifyScoring(undefined)).toBe('proportion')
    expect(classifyScoring('')).toBe('proportion')
  })

  it('maps continuous-variable to its own family', () => {
    expect(classifyScoring('continuous-variable')).toBe('continuousVariable')
  })

  it('maps cohort to its own family', () => {
    expect(classifyScoring('cohort')).toBe('cohort')
  })

  it('coalesces proportion / ratio / composite into proportion (shared 0-100% scale)', () => {
    expect(classifyScoring('proportion')).toBe('proportion')
    expect(classifyScoring('ratio')).toBe('proportion')
    expect(classifyScoring('composite')).toBe('proportion')
  })

  it('falls back to proportion for unknown FHIR scoring strings', () => {
    expect(classifyScoring('something-new')).toBe('proportion')
  })
})
