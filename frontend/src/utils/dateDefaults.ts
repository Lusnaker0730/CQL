/**
 * Dynamic date defaults for measure evaluation periods.
 * Replaces hardcoded 2024 dates with current-year calculations.
 */

/** Returns { periodStart: 'YYYY-01-01', periodEnd: 'YYYY-12-31' } for the current year */
export function getDefaultMeasurePeriod() {
  const year = new Date().getFullYear()
  return {
    periodStart: `${year}-01-01`,
    periodEnd: `${year}-12-31`,
  }
}

/** Returns default comparison periods: H1 vs H2 of the current year */
export function getDefaultComparisonPeriods() {
  const year = new Date().getFullYear()
  return {
    period1Start: `${year}-01-01`,
    period1End: `${year}-06-30`,
    period2Start: `${year}-07-01`,
    period2End: `${year}-12-31`,
  }
}

export type TrendInterval = 'monthly' | 'quarterly' | 'yearly'

/**
 * Split a date range into N contiguous slots ending at `endDate`, ordered
 * chronologically (oldest first). Dates are returned as `YYYY-MM-DD` strings
 * to stay compatible with the date input fields.
 *
 * Monthly: each slot is one calendar month — slot[i].end is the last day of
 * that month, slot[i].start is the first day. For quarterly / yearly we
 * widen the window by 3 / 12 months respectively.
 */
export function computeTrendSlots(
  endDate: string,
  count: number,
  interval: TrendInterval,
): { periodStart: string; periodEnd: string }[] {
  const monthsPerSlot = interval === 'monthly' ? 1 : interval === 'quarterly' ? 3 : 12
  const end = new Date(endDate + 'T00:00:00')
  const slots: { periodStart: string; periodEnd: string }[] = []

  let cursorEnd = new Date(end.getFullYear(), end.getMonth(), end.getDate())
  for (let i = 0; i < count; i++) {
    const start = new Date(cursorEnd.getFullYear(), cursorEnd.getMonth() - monthsPerSlot + 1, 1)
    const slotEnd = new Date(start.getFullYear(), start.getMonth() + monthsPerSlot, 0)
    slots.push({ periodStart: toIsoDate(start), periodEnd: toIsoDate(slotEnd) })
    cursorEnd = new Date(start.getFullYear(), start.getMonth() - 1, 1)
    cursorEnd = new Date(cursorEnd.getFullYear(), cursorEnd.getMonth() + 1, 0)
  }
  return slots.reverse()
}

function toIsoDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
