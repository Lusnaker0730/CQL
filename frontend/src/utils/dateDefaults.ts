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
