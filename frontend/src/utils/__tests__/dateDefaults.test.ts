import { describe, it, expect, vi, afterEach } from 'vitest'
import { getDefaultMeasurePeriod, getDefaultComparisonPeriods } from '../dateDefaults'

describe('getDefaultMeasurePeriod', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('should return current year boundaries', () => {
    const result = getDefaultMeasurePeriod()
    const year = new Date().getFullYear()
    expect(result.periodStart).toBe(`${year}-01-01`)
    expect(result.periodEnd).toBe(`${year}-12-31`)
  })

  it('should use correct year when mocked', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2030-06-15'))

    const result = getDefaultMeasurePeriod()
    expect(result.periodStart).toBe('2030-01-01')
    expect(result.periodEnd).toBe('2030-12-31')
  })
})

describe('getDefaultComparisonPeriods', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('should return H1 and H2 for current year', () => {
    const result = getDefaultComparisonPeriods()
    const year = new Date().getFullYear()

    expect(result.period1Start).toBe(`${year}-01-01`)
    expect(result.period1End).toBe(`${year}-06-30`)
    expect(result.period2Start).toBe(`${year}-07-01`)
    expect(result.period2End).toBe(`${year}-12-31`)
  })

  it('should use correct year when mocked', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-03-01'))

    const result = getDefaultComparisonPeriods()
    expect(result.period1Start).toBe('2025-01-01')
    expect(result.period1End).toBe('2025-06-30')
    expect(result.period2Start).toBe('2025-07-01')
    expect(result.period2End).toBe('2025-12-31')
  })
})
