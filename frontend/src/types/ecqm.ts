// eCQM Authoring types

import type { ConjunctionGroup, BaseElement, Parameter } from './authoring'
import type { MeasureStandardMetadata } from './index'

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

export interface EcqmArtifact extends MeasureStandardMetadata {
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
  /** PAT-238: when the artifact was last published (null = never / before provenance was recorded) */
  publishedAt?: string | null
  ownerUsername: string
  createdAt: string
  updatedAt: string
}

export interface EcqmArtifactRequest extends MeasureStandardMetadata {
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
  /** Rate multiplier for ratio measures with observations (e.g., 1000 for "per 1,000 person-years") */
  rateMultiplier?: number
  /** Scoring unit label (e.g., "per 1,000 person-years") */
  scoringUnit?: string
}

export type ObservationType = 'criteria' | 'duration' | 'quantity'

export interface ObservationEntry {
  observationId: string
  criteria: ConjunctionGroup
  aggregateMethod: string
  populationRef?: string
  observationType?: ObservationType
  /** Time unit for duration type (e.g. 'days', 'hours') */
  observationUnit?: string
  /** FHIR property path for duration/quantity (e.g. 'period', 'value') */
  observationProperty?: string
  /**
   * PAT-129: percentile rank (0–100) when {@link aggregateMethod} === 'Percentile'.
   * Required for that method to produce valid CQL; absent / out-of-range values
   * are flagged in the editor.
   */
  percentileValue?: number
}

/** PAT-233 — how a stratifier's define is read. */
export type StratifierKind = 'criteria' | 'value'

/** Where a value stratifier gets its stratum from. Structured on purpose: no free CQL from the client. */
export type ValueStratifierSource = 'gender' | 'ageBands'

/** One age band; bounds are whole years, inclusive, at least one of them set. */
export interface AgeBand {
  /** Plain ASCII text (letters, digits, space, `_+-./:()`) — it becomes a CQL string literal. */
  label: string
  min?: number
  max?: number
}

export interface ValueStratifier {
  source: ValueStratifierSource
  /** For `ageBands`. */
  bands?: AgeBand[]
}

/** PAT-235 — one component of a multi-component stratifier: its own criteria or value expression. */
export interface StratifierComponentElement {
  /** Plain text (letters, digits, space, `_.-`; unique within the stratifier) — part of the define name. */
  code: string
  description?: string
  kind?: StratifierKind
  criteria?: ConjunctionGroup
  value?: ValueStratifier
}

export interface StratifierElement {
  stratifierId: string
  description?: string
  /** Absent means `criteria` (a boolean condition → `true` / `false` strata). */
  kind?: StratifierKind
  /** The boolean condition tree; kept (but ignored) while `kind === 'value'`. */
  criteria: ConjunctionGroup
  /** The stratum-valued expression when `kind === 'value'`: every distinct value is a stratum. */
  value?: ValueStratifier
  /**
   * PAT-235: when non-empty, the stratifier is multi-component (FHIR `stratifier.component[]`):
   * a patient's stratum is the combination of the components' values, and `kind` / `criteria` /
   * `value` above are ignored.
   */
  components?: StratifierComponentElement[]
}

/** PAT-234 — FHIR measure-data-usage: plain supplemental data, or a risk adjustment factor. */
export type SupplementalDataUsage = 'supplemental-data' | 'risk-adjustment-factor'

export interface SupplementalDataElement {
  name: string
  criteria?: ConjunctionGroup
  /** Absent means `supplemental-data`. Risk adjustment factors should be named `RAF …` (QM IG 3.19). */
  usage?: SupplementalDataUsage
  /** PAT-234: like a stratifier, a custom element is a boolean condition (`criteria`, default) or a value expression. */
  kind?: StratifierKind
  value?: ValueStratifier
  /** Stable client-side id for React keys on custom SDEs. Absent on legacy
   *  rows; EcqmSdeTab falls back to array index for those. */
  id?: string
  /** Marks the row as a custom SDE (not one of the standard SDE
   *  Ethnicity/Race/Sex/Payer templates). Lets name collisions with
   *  standard SDE names stay in the custom list instead of disappearing. */
  custom?: boolean
}

/** PAT-238 — the eCQM builder artifact a measure was published from, and drift since that publish. */
export interface BuilderSource {
  artifactId: number
  artifactName: string
  artifactVersion?: string
  ownerUsername?: string
  publishedAt?: string | null
  artifactUpdatedAt?: string | null
  /** null = unknown (published before publish provenance was recorded) */
  measureEditedSincePublish?: boolean | null
  builderChangedSincePublish?: boolean | null
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
