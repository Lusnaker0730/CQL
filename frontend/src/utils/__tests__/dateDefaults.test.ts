import { describe, it, expect, vi, afterEach } from 'vitest'
import {
  getDefaultMeasurePeriod,
  getDefaultComparisonPeriods,
  computeTrendSlots,
  isDateRangeReversed,
} from '../dateDefaults'

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

describe('computeTrendSlots', () => {
  it('splits monthly range into N contiguous calendar-month slots oldest-first', () => {
    const slots = computeTrendSlots('2026-04-30', 4, 'monthly')
    expect(slots).toEqual([
      { periodStart: '2026-01-01', periodEnd: '2026-01-31' },
      { periodStart: '2026-02-01', periodEnd: '2026-02-28' },
      { periodStart: '2026-03-01', periodEnd: '2026-03-31' },
      { periodStart: '2026-04-01', periodEnd: '2026-04-30' },
    ])
  })

  it('quarterly slots span three calendar months each', () => {
    const slots = computeTrendSlots('2026-12-31', 4, 'quarterly')
    expect(slots).toEqual([
      { periodStart: '2026-01-01', periodEnd: '2026-03-31' },
      { periodStart: '2026-04-01', periodEnd: '2026-06-30' },
      { periodStart: '2026-07-01', periodEnd: '2026-09-30' },
      { periodStart: '2026-10-01', periodEnd: '2026-12-31' },
    ])
  })

  it('yearly slots wrap the calendar year containing the end date', () => {
    const slots = computeTrendSlots('2026-12-31', 3, 'yearly')
    expect(slots).toEqual([
      { periodStart: '2024-01-01', periodEnd: '2024-12-31' },
      { periodStart: '2025-01-01', periodEnd: '2025-12-31' },
      { periodStart: '2026-01-01', periodEnd: '2026-12-31' },
    ])
  })

  it('handles leap-year February correctly', () => {
    const slots = computeTrendSlots('2024-03-31', 2, 'monthly')
    expect(slots).toEqual([
      { periodStart: '2024-02-01', periodEnd: '2024-02-29' },
      { periodStart: '2024-03-01', periodEnd: '2024-03-31' },
    ])
  })
})

describe('isDateRangeReversed (PAT-135)', () => {
  it('returns false when either side is empty', () => {
    expect(isDateRangeReversed('', '')).toBe(false)
    expect(isDateRangeReversed('2024-01-01', '')).toBe(false)
    expect(isDateRangeReversed('', '2024-01-01')).toBe(false)
  })

  it('returns false when from <= to', () => {
    expect(isDateRangeReversed('2024-01-01', '2024-01-01')).toBe(false)
    expect(isDateRangeReversed('2024-01-01', '2024-12-31')).toBe(false)
  })

  it('returns true when from > to', () => {
    expect(isDateRangeReversed('2024-12-31', '2024-01-01')).toBe(true)
    expect(isDateRangeReversed('2025-01-01', '2024-12-31')).toBe(true)
  })
})
