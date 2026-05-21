export type ScoringFamily = 'proportion' | 'continuousVariable' | 'cohort'

/**
 * PAT-124: classify FHIR scoring type into one of three families that share
 * a Y-axis convention. Proportion / ratio / composite all live on a 0–100%
 * scale and are safe to overlay; continuous-variable scores are raw clinical
 * values (HbA1c = 5.6 mmol/L) and must each get an auto-scaled axis with the
 * right unit; cohort is a patient count, not a score, so we render it
 * separately too.
 *
 * Lives in its own file so the chart component stays a pure-component module
 * (Vite's react-refresh plugin warns when a *.tsx file exports both a
 * component and non-component bindings).
 */
export function classifyScoring(scoringType: string | undefined): ScoringFamily {
  if (!scoringType) return 'proportion'
  if (scoringType === 'continuous-variable') return 'continuousVariable'
  if (scoringType === 'cohort') return 'cohort'
  return 'proportion'
}
