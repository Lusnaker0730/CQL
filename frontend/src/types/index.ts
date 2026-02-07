// Auth types
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface AuthResponse {
  token: string
  username: string
  role: string
  expiresIn: number
}

export interface User {
  username: string
  email: string
  role: string
}

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
  debugTrace?: DebugTrace
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

export interface CdsServiceConfigRequest {
  id: string
  hook: string
  title: string
  description?: string
  cqlContent?: string
  cqlLibraryId?: string
  defaultIndicator?: string
  enabled?: boolean
  prefetch?: Record<string, string>
}

export interface CdsServiceConfigResponse {
  id: string
  hook: string
  title: string
  description?: string
  cqlContent?: string
  cqlLibraryId?: string
  defaultIndicator?: string
  enabled: boolean
  version?: number
  serviceName?: string
  prefetch?: Record<string, string>
  createdAt?: string
  updatedAt?: string
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
  resourceId?: string
}

export interface CdsFeedbackItem {
  card: string
  outcome: 'accepted' | 'overridden'
  acceptedSuggestions?: { id: string }[]
  overrideReason?: { code: string; display: string }
  outcomeTimestamp?: string
}

export interface CdsFeedbackRequest {
  feedback: CdsFeedbackItem[]
}

export interface CdsServiceAnalytics {
  serviceId: string
  invocationCount: number
  errorCount: number
  avgResponseTimeMs: number
  errorRate: number
  lastInvokedAt?: string
  feedbackAcceptedCount: number
  feedbackOverriddenCount: number
}

export interface CdsSandboxRequest {
  serviceId: string
  hook: string
  hookInstance?: string
  context?: { userId?: string; patientId?: string; encounterId?: string }
  testData?: Record<string, unknown>
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

// Measure Definition types
export interface MeasureDefinition {
  id?: number
  name: string
  version: string
  title?: string
  description?: string
  status: string
  scoringType: string
  cqlLibraryId?: string
  cqlContent?: string
  fhirMeasureJson?: string
  groupDefinitions?: GroupDefinition[]
  compositeScoring?: string
  componentMeasureIds?: number[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface GroupDefinition {
  groupId: string
  description?: string
  populations?: PopulationDefinition[]
  stratifiers?: StratifierDefinition[]
}

export interface PopulationDefinition {
  populationType: string
  criteriaExpression: string
  description?: string
}

export interface StratifierDefinition {
  stratifierId: string
  criteriaExpression: string
  description?: string
}

export interface MeasureReport {
  id: number
  measureDefinitionId?: number
  measureName: string
  status?: string
  reportType?: string
  periodStart: string
  periodEnd: string
  scoringType?: string
  measureScore?: number
  totalPatients?: number
  resultJson: string
  evaluationResult?: MeasureEvaluationResult
  fhirServerUrl?: string
  evaluatedBy?: string
  evaluationDurationMs?: number
  createdAt: string
}

export interface MeasureSchedule {
  id?: number
  measureDefinitionId: number
  cronExpression: string
  fhirServerUrl?: string
  periodType: string
  enabled: boolean
  lastRunAt?: string
  lastRunStatus?: string
  nextRunAt?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface MeasureComparisonResult {
  measureName: string
  period1: PeriodSummary
  period2: PeriodSummary
  scoreDelta?: number
  scorePercentChange?: number
  populationDeltas?: Record<string, number>
  trend: string
}

export interface PeriodSummary {
  periodStart: string
  periodEnd: string
  measureScore?: number
  totalPatients?: number
  populationCounts?: Record<string, number>
}

export interface MeasureTrendResult {
  measureName: string
  dataPoints: TrendDataPoint[]
}

export interface TrendDataPoint {
  periodStart: string
  periodEnd: string
  score?: number
  populationCounts?: Record<string, number>
}

// Terminology types
export interface ValueSetSearchResult {
  id: string
  url: string
  name: string
  title: string
}

export interface ValueSetExpansion {
  url: string
  name?: string
  title?: string
  expansion: {
    total?: number
    contains: ValueSetCode[]
  }
}

export interface ValueSetCode {
  system: string
  code: string
  display: string
}

export interface CodeLookupResult {
  system: string
  code: string
  name: string
  display: string
  designations: string[]
}

export interface CodeValidationResult {
  result: boolean
  system: string
  code: string
  valueSet: string
}

export interface TerminologyValidationItem {
  type: 'valueset' | 'code' | 'codesystem'
  name: string
  url?: string
  system?: string
  code?: string
  status: 'valid' | 'invalid' | 'error'
  detail?: string
}

// FHIR Validation types
export interface FhirValidationResult {
  valid: boolean
  issues: FhirValidationIssue[]
}

export interface FhirValidationIssue {
  severity: string
  location: string
  message: string
}

// Patient search types
export interface PatientSearchParams {
  family?: string
  given?: string
  birthdate?: string
  identifier?: string
}

// Bulk Export types
export interface BulkExportParams {
  fhirServer: string
  exportType?: string
  _outputFormat?: string
  _since?: string
  _type?: string
}

export interface BulkExportKickOffResult {
  statusUrl: string
  exportType: string
  startedAt: string
}

export interface BulkExportStatusResult {
  status: string
  retryAfterSeconds: number
  output: BulkExportOutput[]
  errorMessage: string | null
}

export interface BulkExportOutput {
  type: string
  url: string
  count: number
}

// Cache types
export interface CacheStats {
  [cacheName: string]: {
    size: number
    hitCount: number
    missCount: number
    hitRate: number
  }
}

// Library metadata for IntelliSense
export interface LibraryMetadata {
  name: string
  version: string
  expressions: string[]
  valueSets: string[]
  codes: string[]
  functions: string[]
}

// Debug trace types
export interface DebugTrace {
  expressionTraces: ExpressionTrace[]
  retrieveTraces: RetrieveTrace[]
  totalTimeMs: number
}

export interface ExpressionTrace {
  name: string
  resultType: string
  resultDisplay: string
  evaluationTimeMs: number
  order: number
}

export interface RetrieveTrace {
  resourceType: string
  resourceCount: number
  retrieveTimeMs: number
}
