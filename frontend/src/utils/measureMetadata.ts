import type { MeasureStandardMetadata } from '../types'

export const METADATA_DATE_KEYS = ['effectiveStart', 'effectiveEnd', 'approvalDate', 'lastReviewDate'] as const

// PAT-236 — rules for the standard FHIR Measure metadata, shared by the measure details page
// and the eCQM summary tab (kept out of the component file so fast refresh stays intact).

/**
 * The eCQM artifact API is a partial update: an omitted key keeps the old value and `''` clears
 * a date (a JSON null is indistinguishable from "not sent" there). Turns the fields' explicit
 * `null` into `''` for the date keys that are present in the update.
 */
export function clearedDatesAsEmpty<T extends Partial<MeasureStandardMetadata>>(updates: T): T {
  const next = { ...updates }
  for (const key of METADATA_DATE_KEYS) {
    if (key in updates && updates[key] == null) next[key] = ''
  }
  return next
}

/** True when the effective period is inverted; both ends are ISO dates, so string order is date order. */
export function effectivePeriodInverted(start?: string | null, end?: string | null): boolean {
  return !!start && !!end && end < start
}

/** True when any standard metadata field carries a value (drives the "filled" tick on the accordion). */
export function standardMetadataFilled(value: MeasureStandardMetadata): boolean {
  return (
    (value.measureTypes?.length ?? 0) > 0 ||
    (value.definitionTerms?.length ?? 0) > 0 ||
    !!value.clinicalRecommendationStatement?.trim() ||
    !!value.effectiveStart ||
    !!value.effectiveEnd ||
    !!value.approvalDate ||
    !!value.lastReviewDate ||
    value.experimental === true
  )
}
