// CDS Authoring Tool types

export interface ArtifactSummary {
  id: number
  name: string
  version: string
  description: string
  status: string
  fhirVersion: string
  ownerUsername: string
  createdAt: string
  updatedAt: string
}

export interface Artifact {
  id: number
  name: string
  version: string
  description: string
  status: string
  fhirVersion: string

  // CPG Metadata
  url?: string
  publisher?: string
  purpose?: string
  usageInfo?: string
  copyright?: string
  experimental?: boolean
  approvalDate?: string
  lastReviewDate?: string
  effectivePeriodStart?: string
  effectivePeriodEnd?: string
  strengthOfRecommendation?: Record<string, unknown>
  qualityOfEvidence?: Record<string, unknown>
  context?: Record<string, unknown>[]
  topic?: Record<string, unknown>[]
  author?: Record<string, unknown>[]
  reviewer?: Record<string, unknown>[]
  endorser?: Record<string, unknown>[]
  relatedArtifact?: Record<string, unknown>[]

  // Expression Trees
  expTreeInclude: ConjunctionGroup
  expTreeExclude: ConjunctionGroup

  // Linked data
  recommendations: Recommendation[]
  subpopulations: Subpopulation[]
  baseElements: BaseElement[]
  parameters: Parameter[]
  errorStatement?: ErrorStatement

  // Ownership
  ownerUsername: string
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface ArtifactRequest {
  name: string
  version?: string
  description?: string
  status?: string
  fhirVersion?: string
  url?: string
  publisher?: string
  purpose?: string
  usageInfo?: string
  copyright?: string
  experimental?: boolean
  approvalDate?: string
  lastReviewDate?: string
  effectivePeriodStart?: string
  effectivePeriodEnd?: string
  strengthOfRecommendation?: Record<string, unknown>
  qualityOfEvidence?: Record<string, unknown>
  context?: Record<string, unknown>[]
  topic?: Record<string, unknown>[]
  author?: Record<string, unknown>[]
  reviewer?: Record<string, unknown>[]
  endorser?: Record<string, unknown>[]
  relatedArtifact?: Record<string, unknown>[]
  expTreeInclude?: ConjunctionGroup
  expTreeExclude?: ConjunctionGroup
  recommendations?: Recommendation[]
  subpopulations?: Subpopulation[]
  baseElements?: BaseElement[]
  parameters?: Parameter[]
  errorStatement?: ErrorStatement
  lockVersion?: number
}

// Expression tree types
export interface ConjunctionGroup {
  id: string
  name: string
  conjunction?: boolean
  returnType: string
  childInstances: ElementInstance[]
  path?: string
}

export interface ElementInstance {
  uniqueId: string
  type: string
  name: string
  extends?: string
  returnType: string
  fields: ElementField[]
  modifiers?: Modifier[]
  suppressedModifiers?: string[]
  path?: string
  childInstances?: ElementInstance[]
  conjunction?: boolean
}

export interface ElementField {
  id: string
  type: string
  name: string
  value?: unknown
  static?: boolean
  select?: string
  valueSets?: ValueSetReference[]
  codes?: CodeReference[]
  exclusive?: boolean
}

export interface Modifier {
  id: string
  name: string
  inputTypes: string[]
  returnType: string
  cqlTemplate?: string
  cqlLibraryFunction?: string
  values?: Record<string, unknown>
}

export interface ValueSetReference {
  name: string
  oid: string
  steward?: string
}

export interface CodeReference {
  code: string
  codeSystem: { name: string; id: string }
  display?: string
}

// Recommendations
export interface Recommendation {
  uid: string
  grade?: string
  subpopulations?: SubpopulationRef[]
  text: string
  rationale?: string
  comment?: string
  links?: RecommendationLink[]
  suggestions?: Suggestion[]
  // CDS Card Tuple mode
  cdsCardMode?: boolean
  detail?: string
  indicator?: string
  sourceLabel?: string
  selectionBehavior?: string
}

export interface SubpopulationRef {
  uniqueId: string
  subpopulationName: string
}

export interface RecommendationLink {
  type: string
  label: string
  url: string
}

export interface Suggestion {
  uid: string
  label: string
  actions?: SuggestionAction[]
  isRecommended?: boolean
}

export interface SuggestionAction {
  type: string
  description: string
  resource?: Record<string, unknown>
}

// Subpopulations
export interface Subpopulation {
  uniqueId: string
  subpopulationName: string
  special?: boolean
  childInstances?: ElementInstance[]
  conjunction?: boolean
  returnType?: string
}

// Base Elements
export interface BaseElement {
  uniqueId: string
  name: string
  type: string
  returnType: string
  fields?: ElementField[]
  modifiers?: Modifier[]
  childInstances?: ElementInstance[]
  conjunction?: boolean
}

// Parameters
export interface Parameter {
  uniqueId: string
  name: string
  type: string
  value?: unknown
  comment?: string
}

// Error Statement
export interface ErrorStatement {
  ifThenClauses?: IfThenClause[]
  elseClause?: string
}

export interface IfThenClause {
  ifCondition: ConditionReference
  statements?: string[]
  thenClause: string
}

export interface ConditionReference {
  label: string
  value: string
}

// Testing & Deployment results
export interface ArtifactTestResult {
  artifactId: number
  cql: string
  patientResults: PatientTestResult[]
  totalPatients: number
  successCount: number
}

export interface PatientTestResult {
  patientId: string
  success: boolean
  error?: string
  expressions?: Record<string, { value: string; type: string }>
  meetsInclusion?: string
  meetsExclusion?: string
  inPopulation?: string
  recommendation?: string
}

export interface DeployResult {
  serviceId: string
  hook: string
  title: string
  message: string
}

export interface SaveLibraryResult {
  libraryId: string
  name: string
  version: string
  message: string
}

// CQL Import
export interface CqlImportResult {
  name: string
  version: string
  fhirVersion: string
  valueSets: Array<{ name: string; oid: string }>
  parameters: Array<{ uniqueId: string; name: string; type: string; value?: string }>
  definitions: Array<{ name: string; expression: string }>
  valid: boolean
  errors?: Array<{ message: string }>
  metadata?: {
    libraryId: string
    expressions: Array<{ name: string; resultType?: string }>
    includes: string[]
  }
  cqlContent: string
}

// Query Builder
export interface QueryBuilderResource {
  name: string
  properties: Array<{
    name: string
    type: string
    values?: string[]
  }>
}

export interface QueryBuilderOperator {
  id: string
  symbol: string
  label: string
  applicableTypes: string[]
}

// External CQL Library
export interface ExternalCqlLibrary {
  id: number
  artifactId: number
  name: string
  version?: string
  fhirVersion?: string
  cqlContent: string
  details?: {
    definitions?: Array<{ name: string; context?: string; resultType?: string }>
    parameters?: string[]
    valueSets?: string[]
    includes?: string[]
    errors?: Array<{ message: string }>
  }
  createdAt: string
}

// Template types (for Phase 7)
export interface FormTemplate {
  id: string
  name: string
  returnType: string
  suppress?: boolean
  twcoreOnly?: boolean
  fields?: ElementField[]
  type?: string
  extends?: string
  template?: string
  conjunction?: boolean
  cannotHaveModifiers?: boolean
  suppressedModifiers?: string[]
  validator?: Record<string, unknown>
}

export interface FormTemplateCategory {
  id: number
  name: string
  icon?: string
  suppress?: boolean
  entries: FormTemplate[]
}

export interface ModifierDefinition {
  id: string
  name: string
  type?: string
  inputTypes: string[]
  returnType: string
  cqlTemplate?: string
  cqlLibraryFunction?: string
  values?: Record<string, unknown>
  validator?: Record<string, unknown>
}

// TWCORE Catalog types
export interface TwcoreCode {
  code: string
  display: string
  displayZh: string
}

export interface TwcoreCodeCategory {
  name: string
  codes: TwcoreCode[]
}

export interface TwcoreCatalogEntry {
  resourceType: string
  name: string
  url: string
  system: string
  categories: TwcoreCodeCategory[]
}

export interface TwcoreCodeSystem {
  label: string
  url: string
}
