import type { AgeBand } from '../types/ecqm'
import { AGE_BAND_LABEL_RE, MAX_AGE, MAX_AGE_BANDS } from '../constants/ecqmConstants'

/** A problem with an age-band list, as an i18n key under `ecqm:stratifiers.bandErrors`. */
export type AgeBandProblem =
  | 'empty'
  | 'tooMany'
  | 'label'
  | 'bounds'
  | 'noBound'
  | 'order'
  | 'duplicate'

function isWholeYear(n: number | undefined): boolean {
  return n === undefined || (Number.isInteger(n) && n >= 0 && n <= MAX_AGE)
}

/**
 * PAT-233 — the same checks the backend applies before it turns bands into CQL, so the author
 * hears about a bad band while editing rather than as a skipped define at publish time.
 * Returns the distinct problems found, in the order they were found; empty means valid.
 */
export function validateAgeBands(bands: AgeBand[] | undefined): AgeBandProblem[] {
  const problems: AgeBandProblem[] = []
  const add = (p: AgeBandProblem) => { if (!problems.includes(p)) problems.push(p) }
  if (!bands || bands.length === 0) return ['empty']
  if (bands.length > MAX_AGE_BANDS) add('tooMany')
  const labels = new Set<string>()
  for (const band of bands) {
    const label = (band.label ?? '').trim()
    if (!AGE_BAND_LABEL_RE.test(label)) add('label')
    if (labels.has(label)) add('duplicate')
    labels.add(label)
    if (!isWholeYear(band.min) || !isWholeYear(band.max)) add('bounds')
    if (band.min === undefined && band.max === undefined) add('noBound')
    if (band.min !== undefined && band.max !== undefined && band.min > band.max) add('order')
  }
  return problems
}

/** `'18-49'`, `'65+'`, `'0-17'`: the label an author most likely wants for the bounds. */
export function defaultBandLabel(min: number | undefined, max: number | undefined): string {
  if (min !== undefined && max !== undefined) return `${min}-${max}`
  if (min !== undefined) return `${min}+`
  if (max !== undefined) return `0-${max}`
  return ''
}
