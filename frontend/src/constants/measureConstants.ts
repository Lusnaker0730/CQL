/**
 * Centralized constants for eQMS measure statuses, scoring types, and settings.
 * Replaces scattered magic strings across measure components.
 */

// --- Measure Statuses ---

export const MEASURE_STATUS = {
  DRAFT: 'draft',
  IN_REVIEW: 'in-review',
  ACTIVE: 'active',
  RETIRED: 'retired',
} as const

export type MeasureStatusValue = (typeof MEASURE_STATUS)[keyof typeof MEASURE_STATUS]

export const MEASURE_STATUS_OPTIONS: { value: MeasureStatusValue; label: string }[] = [
  { value: 'draft', label: 'Draft' },
  { value: 'active', label: 'Active' },
  { value: 'retired', label: 'Retired' },
]

// --- Scoring Types ---

export const SCORING_TYPE = {
  PROPORTION: 'proportion',
  RATIO: 'ratio',
  CONTINUOUS_VARIABLE: 'continuous-variable',
  COHORT: 'cohort',
  COMPOSITE: 'composite',
} as const

export type ScoringTypeValue = (typeof SCORING_TYPE)[keyof typeof SCORING_TYPE]

export const SCORING_TYPE_OPTIONS: { value: ScoringTypeValue; label: string }[] = [
  { value: 'proportion', label: 'Proportion' },
  { value: 'ratio', label: 'Ratio' },
  { value: 'continuous-variable', label: 'Continuous Variable' },
  { value: 'cohort', label: 'Cohort' },
  { value: 'composite', label: 'Composite' },
]

// --- Care Settings ---

export const MEASURE_SETTING_OPTIONS: { value: string; label: string }[] = [
  { value: 'inpatient', label: 'Inpatient' },
  { value: 'outpatient', label: 'Outpatient' },
  { value: 'emergency', label: 'Emergency' },
  { value: 'community', label: 'Community' },
  { value: 'long-term-care', label: 'Long-term Care' },
  { value: 'home-health', label: 'Home Health' },
]

// --- Composite Scoring ---

export const COMPOSITE_SCORING_OPTIONS: { value: string; label: string }[] = [
  { value: 'opportunity', label: 'Opportunity' },
  { value: 'linear', label: 'Linear' },
]

// --- Defaults ---

export const DEFAULT_MEASURE_STATUS = MEASURE_STATUS.DRAFT
export const DEFAULT_SCORING_TYPE = SCORING_TYPE.PROPORTION
export const DEFAULT_REFERENCE_TYPE = 'citation'
