// eCQM Authoring types

import type { ConjunctionGroup, BaseElement, Parameter } from './authoring'

export interface EcqmArtifactSummary {
  id: number
  name: string
  version: string
  description: string
  status: string
  scoringType: string
  populationBasis: string
  publishedMeasureId: number | null
  ownerUsername: string
  createdAt: string
  updatedAt: string
}

export interface EcqmArtifact {
  id: number
  name: string
  version: string
  description: string
  status: string
  fhirVersion: string
  scoringType: string
  populationBasis: string
  improvementNotation: string
  measureSet?: string
  cmsMeasureId?: string
  nqfNumber?: string
  url?: string
  publisher?: string
  purpose?: string
  copyright?: string
  rationale?: string
  clinicalGuidance?: string
  steward?: string
  disclaimer?: string
  supplementalDataGuidance?: string

  populationGroups: PopulationGroup[]
  supplementalData: SupplementalDataElement[]
  stratifiers: StratifierElement[]
  baseElements: BaseElement[]
  parameters: Parameter[]

  publishedMeasureId: number | null
  ownerUsername: string
  createdAt: string
  updatedAt: string
}

export interface EcqmArtifactRequest {
  name: string
  version?: string
  description?: string
  status?: string
  fhirVersion?: string
  scoringType?: string
  populationBasis?: string
  improvementNotation?: string
  measureSet?: string
  cmsMeasureId?: string
  nqfNumber?: string
  url?: string
  publisher?: string
  purpose?: string
  copyright?: string
  rationale?: string
  clinicalGuidance?: string
  steward?: string
  disclaimer?: string
  supplementalDataGuidance?: string
  populationGroups?: PopulationGroup[]
  supplementalData?: SupplementalDataElement[]
  stratifiers?: StratifierElement[]
  baseElements?: BaseElement[]
  parameters?: Parameter[]
}

export interface PopulationGroup {
  groupId: string
  description?: string
  populations: Record<string, ConjunctionGroup | null>
  initialPopulationDenom?: ConjunctionGroup | null
  initialPopulationNumer?: ConjunctionGroup | null
  observations?: ObservationEntry[]
  stratifiers?: StratifierElement[]
}

export interface ObservationEntry {
  observationId: string
  criteria: ConjunctionGroup
  aggregateMethod: string
  populationRef?: string
}

export interface StratifierElement {
  stratifierId: string
  description?: string
  criteria: ConjunctionGroup
}

export interface SupplementalDataElement {
  name: string
  criteria?: ConjunctionGroup
}

export interface PublishResult {
  measureDefinitionId: number
  measureName: string
  cql: string
  message: string
}

export interface ScoringTypeConfig {
  id: string
  label: string
  required: string[]
  optional: string[]
}

export interface ScoringTypesResponse {
  scoringTypes: ScoringTypeConfig[]
  populationBasisOptions: string[]
  aggregateMethods: string[]
}

// Population key constants
export const POPULATION_KEYS = [
  'initial-population',
  'denominator',
  'denominator-exclusion',
  'denominator-exception',
  'numerator',
  'numerator-exclusion',
  'measure-population',
  'measure-population-exclusion',
] as const

export type PopulationKey = typeof POPULATION_KEYS[number]

export const POPULATION_LABELS: Record<PopulationKey, string> = {
  'initial-population': 'Initial Population',
  'denominator': 'Denominator',
  'denominator-exclusion': 'Denominator Exclusion',
  'denominator-exception': 'Denominator Exception',
  'numerator': 'Numerator',
  'numerator-exclusion': 'Numerator Exclusion',
  'measure-population': 'Measure Population',
  'measure-population-exclusion': 'Measure Population Exclusion',
}
