import type { PopulationKey } from '../types/ecqm'

export const SCORING_TYPES = [
  { id: 'proportion', label: 'Proportion' },
  { id: 'ratio', label: 'Ratio' },
  { id: 'continuous-variable', label: 'Continuous Variable' },
  { id: 'cohort', label: 'Cohort' },
] as const

export const POPULATION_BASIS_OPTIONS = [
  { id: 'boolean', label: 'Patient-based (Boolean)' },
  { id: 'Encounter', label: 'Encounter-based' },
  { id: 'Procedure', label: 'Procedure-based' },
  { id: 'MedicationRequest', label: 'MedicationRequest-based' },
  { id: 'Observation', label: 'Observation-based' },
] as const

export const IMPROVEMENT_NOTATIONS = [
  { id: 'increase', label: 'Increase (higher is better)' },
  { id: 'decrease', label: 'Decrease (lower is better)' },
] as const

export const AGGREGATE_METHODS = [
  'Count', 'Sum', 'Average', 'Median', 'Maximum', 'Minimum', 'Percentile',
] as const

/** Which populations are relevant for each scoring type */
export const SCORING_POPULATIONS: Record<string, PopulationKey[]> = {
  proportion: [
    'initial-population', 'denominator', 'denominator-exclusion',
    'denominator-exception', 'numerator', 'numerator-exclusion',
  ],
  ratio: [
    'initial-population', 'denominator', 'denominator-exclusion',
    'numerator', 'numerator-exclusion',
  ],
  'continuous-variable': [
    'initial-population', 'measure-population', 'measure-population-exclusion',
  ],
  cohort: ['initial-population'],
}

export const REQUIRED_POPULATIONS: Record<string, PopulationKey[]> = {
  proportion: ['initial-population', 'denominator', 'numerator'],
  ratio: ['initial-population', 'denominator', 'numerator'],
  'continuous-variable': ['initial-population', 'measure-population'],
  cohort: ['initial-population'],
}

/** Standard Supplemental Data Elements */
export const STANDARD_SDE = [
  { name: 'SDE Ethnicity', oid: 'urn:oid:2.16.840.1.114222.4.11.837' },
  { name: 'SDE Race', oid: 'urn:oid:2.16.840.1.114222.4.11.836' },
  { name: 'SDE Sex', oid: 'urn:oid:2.16.840.1.113762.1.4.1' },
  { name: 'SDE Payer', oid: 'urn:oid:2.16.840.1.114222.4.11.3591' },
] as const

/** Stable empty conjunction group — use as fallback for display only.
 *  Call createEmptyConjunctionGroup() when you need a mutable copy. */
export const DEFAULT_CONJUNCTION_GROUP = Object.freeze({
  id: 'And',
  name: 'And',
  conjunction: true,
  returnType: 'boolean',
  childInstances: [] as readonly never[],
})

/** Creates a fresh mutable copy for use as initial state */
export function createEmptyConjunctionGroup() {
  return { id: 'And', name: 'And', conjunction: true, returnType: 'boolean', childInstances: [] as unknown[] }
}
