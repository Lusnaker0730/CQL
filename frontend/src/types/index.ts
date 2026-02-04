export interface CqlTranslationRequest {
  cql: string
  enableAnnotations?: boolean
  enableLocators?: boolean
  enableResultTypes?: boolean
  validateUnits?: boolean
}

export interface CqlTranslationResponse {
  success: boolean
  elmJson?: string
  errors: CqlError[]
  warnings: CqlError[]
  metadata?: TranslationMetadata
}

export interface CqlError {
  severity: string
  message: string
  startLine?: number
  startColumn?: number
  endLine?: number
  endColumn?: number
  errorType?: string
}

export interface TranslationMetadata {
  libraryId?: string
  libraryVersion?: string
  usings: string[]
  includes: string[]
  parameters: string[]
  valueSets: string[]
  codes: string[]
  concepts: string[]
  expressions: ExpressionInfo[]
}

export interface ExpressionInfo {
  name: string
  context?: string
  accessLevel?: string
  resultType?: string
}

export interface CqlExecutionRequest {
  cql: string
  libraryId?: string
  patientId?: string
  contextType?: string
  parameters?: Record<string, unknown>
  fhirServerUrl?: string
  debugMode?: boolean
  expressionNames?: string[]
}

export interface CqlExecutionResponse {
  success: boolean
  patientId?: string
  results: Record<string, ExpressionResult>
  errors?: string[]
  metadata?: ExecutionMetadata
}

export interface ExpressionResult {
  name: string
  value: unknown
  valueType: string
  displayValue: string
}

export interface ExecutionMetadata {
  executionTimeMs: number
  libraryId?: string
  libraryVersion?: string
  fhirServerUrl?: string
  resourcesRetrieved?: number
}

export interface CqlLibrary {
  id: string
  name: string
  version: string
  cqlContent: string
  elmJson?: string
  description?: string
  status: string
  dependencies: string[]
  createdAt: string
  updatedAt: string
}

export interface CdsServiceDefinition {
  id: string
  hook: string
  title: string
  description: string
  prefetch?: Record<string, { query: string }>
}

export interface CdsRequest {
  hook: string
  hookInstance: string
  fhirServer?: string
  context: {
    userId?: string
    patientId?: string
    encounterId?: string
  }
  prefetch?: Record<string, unknown>
}

export interface CdsResponse {
  cards: CdsCard[]
  systemActions?: SystemAction[]
}

export interface CdsCard {
  uuid: string
  summary: string
  detail?: string
  indicator: 'info' | 'warning' | 'critical'
  source: {
    label: string
    url?: string
  }
  suggestions?: CdsSuggestion[]
  links?: CdsLink[]
}

export interface CdsSuggestion {
  uuid: string
  label: string
  isRecommended?: boolean
  actions?: CdsAction[]
}

export interface CdsAction {
  type: 'create' | 'update' | 'delete'
  description?: string
  resource?: unknown
}

export interface CdsLink {
  label: string
  url: string
  type: 'absolute' | 'smart'
}

export interface SystemAction {
  type: string
  description?: string
  resource?: unknown
}

export interface MeasureEvaluationRequest {
  measureId: string
  measureCql?: string
  patientId?: string
  periodStart?: string
  periodEnd?: string
  reportType?: string
  fhirServerUrl?: string
}

export interface MeasureEvaluationResult {
  measureId: string
  measureName?: string
  status: string
  periodStart: string
  periodEnd: string
  reportType: string
  groups: MeasureGroupResult[]
  supplementalData?: Record<string, unknown>
}

export interface MeasureGroupResult {
  groupId: string
  description?: string
  populations: PopulationResult[]
  measureScore?: number
  measureScoreUnit?: string
  stratifiers?: StratifierResult[]
}

export interface PopulationResult {
  populationType: string
  populationId: string
  count?: number
  subjectIds?: string[]
}

export interface StratifierResult {
  strataId: string
  strataValue: string
  populations: PopulationResult[]
  measureScore?: number
}
