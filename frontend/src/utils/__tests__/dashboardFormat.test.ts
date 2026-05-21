import { describe, it, expect } from 'vitest'
import { formatScoreValue, formatThresholdValue } from '../dashboardFormat'

describe('formatScoreValue — PAT-151', () => {
  it('null / undefined / NaN return the configured naLabel (default "-")', () => {
    expect(formatScoreValue(null)).toBe('-')
    expect(formatScoreValue(undefined)).toBe('-')
    expect(formatScoreValue(Number.NaN)).toBe('-')
    expect(formatScoreValue(null, { naLabel: 'N/A' })).toBe('N/A')
  })

  it('proportion: 1 decimal + percent', () => {
    expect(formatScoreValue(85.4321, { scoringType: 'proportion' })).toBe('85.4%')
    expect(formatScoreValue(0, { scoringType: 'proportion' })).toBe('0.0%')
    expect(formatScoreValue(100, { scoringType: 'proportion' })).toBe('100.0%')
  })

  it('proportion is the default when scoringType is omitted', () => {
    expect(formatScoreValue(50)).toBe('50.0%')
  })

  it('ratio / composite share the proportion family (0-100% scale)', () => {
    expect(formatScoreValue(50, { scoringType: 'ratio' })).toBe('50.0%')
    expect(formatScoreValue(50, { scoringType: 'composite' })).toBe('50.0%')
  })

  it('PAT-151 regression: continuous-variable does NOT append %', () => {
    // HbA1c 5.6 mmol/L must render as "5.6 mmol/L", never "5.6%"
    expect(formatScoreValue(5.6, { scoringType: 'continuous-variable', unit: 'mmol/L' }))
      .toBe('5.60 mmol/L')
    // Larger values pick fewer decimals
    expect(formatScoreValue(150.7, { scoringType: 'continuous-variable', unit: 'kg' }))
      .toBe('151 kg')
    expect(formatScoreValue(45.123, { scoringType: 'continuous-variable', unit: 'kg' }))
      .toBe('45.1 kg')
  })

  it('continuous-variable without a unit still skips the percent sign', () => {
    expect(formatScoreValue(5.6, { scoringType: 'continuous-variable' })).toBe('5.60')
  })

  it('PAT-151 regression: cohort renders as integer count, never %', () => {
    expect(formatScoreValue(42.7, { scoringType: 'cohort' })).toBe('43')
    expect(formatScoreValue(1234, { scoringType: 'cohort', unit: 'patients' }))
      .toBe('1234 patients')
  })
})

describe('formatThresholdValue', () => {
  it('mirrors formatScoreValue (separate name for documentation only)', () => {
    expect(formatThresholdValue(80, { scoringType: 'proportion' })).toBe('80.0%')
    expect(formatThresholdValue(7.0, { scoringType: 'continuous-variable', unit: 'mmol/L' }))
      .toBe('7.00 mmol/L')
    expect(formatThresholdValue(100, { scoringType: 'cohort' })).toBe('100')
  })

  it('uses "-" for missing thresholds (table cells)', () => {
    expect(formatThresholdValue(undefined)).toBe('-')
  })
})
