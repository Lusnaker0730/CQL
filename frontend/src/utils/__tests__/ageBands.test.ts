import { describe, it, expect } from 'vitest'
import { defaultBandLabel, validateAgeBands } from '../ageBands'

// PAT-233 — mirrors the backend's band checks so the author hears about a bad band while editing.
describe('validateAgeBands', () => {
  it('accepts the default-looking bands', () => {
    expect(validateAgeBands([
      { label: '0-17', min: 0, max: 17 },
      { label: '18-64', min: 18, max: 64 },
      { label: '65+', min: 65 },
      { label: 'infant', max: 1 },
    ])).toEqual([])
  })

  it('reports each distinct problem once', () => {
    expect(validateAgeBands(undefined)).toEqual(['empty'])
    expect(validateAgeBands([])).toEqual(['empty'])
    expect(validateAgeBands([{ label: 'a<b', min: 60, max: 40 }])).toEqual(['label', 'order'])
    expect(validateAgeBands([{ label: 'x', min: 18.5, max: 200 }])).toEqual(['bounds'])
    expect(validateAgeBands([{ label: 'x' }])).toEqual(['noBound'])
    expect(validateAgeBands([{ label: 'same', min: 0, max: 17 }, { label: 'same', min: 18 }])).toEqual(['duplicate'])
    expect(validateAgeBands([{ label: '老年', min: 65 }])).toEqual(['label']) // becomes a CQL literal; ASCII only
    expect(validateAgeBands(Array.from({ length: 21 }, (_, i) => ({ label: `b${i}`, min: i })))).toEqual(['tooMany'])
  })
})

describe('defaultBandLabel', () => {
  it('names a band after its bounds', () => {
    expect(defaultBandLabel(18, 49)).toBe('18-49')
    expect(defaultBandLabel(65, undefined)).toBe('65+')
    expect(defaultBandLabel(undefined, 17)).toBe('0-17')
    expect(defaultBandLabel(undefined, undefined)).toBe('')
  })
})
