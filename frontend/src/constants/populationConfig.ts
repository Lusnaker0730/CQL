import type { PopulationDefinition } from '../types'

export interface PopulationType {
  value: string
  label: string
  description: string
}

export const POPULATION_TYPES: PopulationType[] = [
  {
    value: 'initial-population',
    label: 'Initial Population',
    description: 'The initial set of patients to be evaluated by the measure.',
  },
  {
    value: 'denominator',
    label: 'Denominator',
    description: 'The lower portion of a fraction used to calculate a rate. Typically a subset of the Initial Population.',
  },
  {
    value: 'denominator-exclusion',
    label: 'Denominator Exclusion',
    description: 'Patients removed from the denominator before determining membership in the numerator.',
  },
  {
    value: 'denominator-exception',
    label: 'Denominator Exception',
    description: 'Patients who should have met the numerator criteria but did not due to valid exceptions.',
  },
  {
    value: 'numerator',
    label: 'Numerator',
    description: 'The upper portion of a fraction. Patients from the denominator meeting the desired criteria.',
  },
  {
    value: 'numerator-exclusion',
    label: 'Numerator Exclusion',
    description: 'Patients removed from the numerator. Used in ratio measures.',
  },
  {
    value: 'measure-population',
    label: 'Measure Population',
    description: 'The population basis for continuous variable measures.',
  },
  {
    value: 'measure-population-exclusion',
    label: 'Measure Population Exclusion',
    description: 'Patients excluded from the measure population in continuous variable measures.',
  },
]

export const POPULATION_LABELS: Record<string, string> = Object.fromEntries(
  POPULATION_TYPES.map((pt) => [pt.value, pt.label])
)

interface ScoringTemplate {
  required: string[]
  optional: string[]
}

export const SCORING_TEMPLATES: Record<string, ScoringTemplate> = {
  proportion: {
    required: ['initial-population', 'denominator', 'numerator'],
    optional: ['denominator-exclusion', 'denominator-exception', 'numerator-exclusion'],
  },
  ratio: {
    required: ['initial-population', 'denominator', 'numerator'],
    optional: ['denominator-exclusion', 'numerator-exclusion'],
  },
  cohort: {
    required: ['initial-population'],
    optional: [],
  },
  'continuous-variable': {
    required: ['initial-population', 'measure-population'],
    optional: ['measure-population-exclusion'],
  },
  composite: {
    required: ['initial-population'],
    optional: [],
  },
}

export function createPopulationsFromTemplate(scoringType: string): PopulationDefinition[] {
  const template = SCORING_TEMPLATES[scoringType]
  if (!template) {
    return [{ populationType: 'initial-population', criteriaExpression: '' }]
  }
  return template.required.map((type) => ({
    populationType: type,
    criteriaExpression: '',
  }))
}

export function getPopulationLabel(type: string): string {
  return POPULATION_LABELS[type] || type
}

export function getPopulationConfig(type: string): PopulationType | undefined {
  return POPULATION_TYPES.find((pt) => pt.value === type)
}

export const POPULATION_BASIS_OPTIONS = [
  'Boolean',
  'Encounter',
  'MedicationRequest',
  'Procedure',
  'Observation',
]

export const EXPRESSION_TO_POPULATION_MAP: Record<string, string> = {
  'Initial Population': 'initial-population',
  'InitialPopulation': 'initial-population',
  'Denominator': 'denominator',
  'Denominator Exclusion': 'denominator-exclusion',
  'DenominatorExclusion': 'denominator-exclusion',
  'Denominator Exception': 'denominator-exception',
  'DenominatorException': 'denominator-exception',
  'Numerator': 'numerator',
  'Numerator Exclusion': 'numerator-exclusion',
  'NumeratorExclusion': 'numerator-exclusion',
  'Measure Population': 'measure-population',
  'MeasurePopulation': 'measure-population',
  'Measure Population Exclusion': 'measure-population-exclusion',
  'MeasurePopulationExclusion': 'measure-population-exclusion',
}

export function autoMapExpressions(
  populations: PopulationDefinition[],
  expressionNames: string[]
): { mapped: PopulationDefinition[]; count: number } {
  let count = 0
  const mapped = populations.map((pop) => {
    if (pop.criteriaExpression) return pop
    const match = expressionNames.find(
      (name) => EXPRESSION_TO_POPULATION_MAP[name] === pop.populationType
    )
    if (match) {
      count++
      return { ...pop, criteriaExpression: match }
    }
    return pop
  })
  return { mapped, count }
}

export function getPopulationTypesForScoring(scoringType: string): string[] {
  const template = SCORING_TEMPLATES[scoringType]
  if (!template) return []
  return [...template.required, ...template.optional]
}

export const AGGREGATE_METHODS = [
  'Count',
  'Sum',
  'Average',
  'Median',
  'Minimum',
  'Maximum',
]

export const OBSERVATION_REQUIRED_SCORING = new Set([
  'continuous-variable',
  'ratio',
])

export const REFERENCE_TYPES = [
  'citation',
  'documentation',
  'justification',
  'derived-from',
]
