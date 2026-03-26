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
  forcePasswordChange: boolean
}

export interface User {
  username: string
  email: string
  role: string
  forcePasswordChange: boolean
  authProvider?: string
  displayName?: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface RefreshResponse {
  token: string
  expiresIn: number
}

export interface AdminResetPasswordResponse {
  temporaryPassword: string
  username: string
  message: string
}

export interface UserSummary {
  id: number
  username: string
  email: string
  role: string
  enabled: boolean
  forcePasswordChange: boolean
  authProvider?: string
  department?: string
  createdAt: string
}

export interface OktaConfig {
  enabled: boolean
  authorizationEndpoint?: string
  clientId?: string
  scopes?: string
}

export interface OktaCallbackRequest {
  code: string
  redirectUri: string
  nonce?: string
}

export interface AdminCreateUserRequest {
  username: string
  password: string
  email?: string
  role: 'ADMIN' | 'USER' | 'DEPARTMENT_ADMIN'
  department?: string
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

export interface CqlFixSuggestionResponse {
  success: boolean
  explanation?: string
  suggestedCql?: string
  errorMessage?: string
  model?: string
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
  ownerUsername?: string
  sharedWith?: string[]
  accessLevel?: string
  createdAt: string
  updatedAt: string
}

// Dependency analysis types
export interface DependencyAnalysisResult {
  dependencies: DependencyInfo[]
  conflicts: VersionConflict[]
  mismatches: VersionMismatch[]
  circularDependencies?: CircularDependency[]
  hasIssues: boolean
}

export interface DependencyInfo {
  name: string
  declaredVersion?: string
  resolvedVersion?: string
  available: boolean
  versionMatch: boolean
  transitiveDeps?: DependencyInfo[]
}

export interface VersionConflict {
  libraryName: string
  requestedVersions: string[]
  requestedBy: string[]
  resolvedVersion: string
  severity: string
}

export interface VersionMismatch {
  libraryName: string
  declaredVersion: string
  availableVersion: string
  requestedBy: string
}

export interface CircularDependency {
  libraryName: string
  version?: string
  cycle: string[]
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
  ownerUsername?: string
  shared?: boolean
  prefetch?: Record<string, string>
  createdAt?: string
  updatedAt?: string
}

export interface ApiKey {
  id: number
  name: string
  key?: string
  keyPreview?: string
  createdAt: string
  lastUsedAt?: string
  active: boolean
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
  overrideReasons?: { code?: { system?: string; code?: string; display?: string }; display?: string }[]
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
  draftOrders?: unknown
}

export interface SandboxPresetRequest {
  name: string
  description?: string
  serviceId?: string
  patientId?: string
  prefetchJson: string
  shared?: boolean
}

export interface SandboxPresetResponse {
  id: number
  name: string
  description?: string
  ownerUsername: string
  serviceId?: string
  patientId?: string
  prefetchJson: string
  shared: boolean
  createdAt: string
  updatedAt: string
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
  errorMessage?: string
}

export interface ObservationStatistics {
  aggregateMethod: string
  aggregateValue: number
  observationCount: number
  minimum: number
  maximum: number
  average: number
  median: number
  unit?: string
}

export interface MeasureGroupResult {
  groupId: string
  description?: string
  populations: PopulationResult[]
  measureScore?: number
  measureScoreUnit?: string
  stratifiers?: StratifierResult[]
  totalPatients?: number
  observationStatistics?: ObservationStatistics
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
  setting?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  // Enhanced metadata
  rationale?: string
  clinicalGuidance?: string
  steward?: string
  developers?: string[]
  references?: MeasureReference[]
  disclaimer?: string
  copyright?: string
  measureSet?: string
  nqfNumber?: string
  cmsMeasureId?: string
  supplementalDataGuidance?: string
  riskAdjustmentDescription?: string
  riskAdjustments?: RiskAdjustmentDef[]
  supplementalData?: SupplementalDataDef[]
  improvementNotation?: string
  rateAggregation?: string
  ownerUsername?: string
  sharedWith?: string[]
  accessLevel?: string
  lockedBy?: string
  lockedAt?: string
  reviewedBy?: string
  approvedBy?: string
  reviewComment?: string
  reviewedAt?: string
  // Department
  department?: string
  // Indicator code mapping
  mohIndicatorCode?: string
  nhiaP4pCode?: string
  drgIndicatorCode?: string
  indicatorCategory?: string
}

export interface MeasureAuditEntry {
  id: number
  measureId: number
  action: string
  performedBy?: string
  details?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}

// Validation types
export interface ValidationReport {
  issues: ValidationIssue[]
  errorCount: number
  warningCount: number
  infoCount: number
  valid: boolean
  validationTimeMs: number
}

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  category: 'CQL' | 'POPULATIONS' | 'METADATA' | 'TEST_CASES' | 'QI_CORE'
  message: string
  field?: string
  fixHint?: string
}

export interface MeasureReference {
  type: string
  reference: string
}

export interface RiskAdjustmentDef {
  definition: string
  description: string
}

export interface SupplementalDataDef {
  definition: string
  description: string
}

export interface ObservationDefinition {
  criteriaExpression: string
  aggregateMethod: string
  populationRef: string
  description?: string
}

export interface GroupDefinition {
  groupId: string
  description?: string
  populationBasis?: string
  populations?: PopulationDefinition[]
  stratifiers?: StratifierDefinition[]
  observations?: ObservationDefinition[]
  scoringUnit?: string
  rateIndex?: number
  rateDescription?: string
}

export interface PopulationDefinition {
  populationType: string
  criteriaExpression: string
  description?: string
  associationType?: string
}

export interface StratifierDefinition {
  stratifierId: string
  criteriaExpression: string
  description?: string
  associations?: string[]
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

export interface CodeSearchResult {
  system: string
  code: string
  display: string
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

// User library preferences
export interface UserFavorite {
  id: number
  libraryId: string
  libraryName: string
  libraryVersion: string
  createdAt: string
}

export interface UserRecent {
  id: number
  libraryId: string
  libraryName: string
  libraryVersion: string
  accessedAt: string
}

// Debug trace types
export interface DebugTrace {
  expressionTraces: ExpressionTrace[]
  retrieveTraces: RetrieveTrace[]
  totalTimeMs: number
  sourceLocators?: Record<string, string>
}

export interface ExpressionTrace {
  name: string
  resultType: string
  resultDisplay: string
  evaluationTimeMs: number
  order: number
  sourceLocator?: string
  dependencies?: string[]
}

export interface RetrieveTrace {
  resourceType: string
  resourceCount: number
  retrieveTimeMs: number
}

// Test Case types
export interface TestCase {
  id?: number
  measureDefinitionId?: number
  title: string
  description?: string
  patientBundleJson?: string
  expectedPopulations?: Record<string, boolean>
  status?: string
  lastRunResultJson?: string
  lastRunActualPopulations?: Record<string, boolean>
  lastRunAt?: string
  createdAt?: string
  updatedAt?: string
  series?: string
  sortOrder?: number
}

export interface TestCaseRunResult {
  testCaseId: number
  testCaseTitle: string
  status: string
  expectedPopulations?: Record<string, boolean>
  actualPopulations?: Record<string, boolean>
  comparisons?: PopulationComparison[]
  errorMessage?: string
  executionTimeMs?: number
}

export interface PopulationComparison {
  populationType: string
  expected?: boolean
  actual?: boolean
  match: boolean
}

export interface BatchTestCaseImportResult {
  totalReceived: number
  successCount: number
  failureCount: number
  imported: TestCase[]
  errors: string[]
}

// Implementation Guide types
export interface IgPackageMetadata {
  name: string
  version: string
  title: string
  canonical: string
  fhirVersion: string
  profileCount: number
  valueSetCount: number
  codeSystemCount: number
}

export interface ProfileSummary {
  url: string
  name: string
  title: string
  type: string
  kind: string
  status: string
}

export interface ValueSetSummary {
  url: string
  name: string
  title: string
  status: string
  conceptCount: number
}

export interface CodeSystemSummary {
  url: string
  name: string
  title: string
  status: string
  conceptCount: number
}

// Coverage types
export interface CoverageResult {
  definitions: ExpressionCoverage[]
  functions: ExpressionCoverage[]
}

export interface ExpressionCoverage {
  name: string
  type: string
  relevance: 'TRUE' | 'FALSE' | 'NA'
  result: string
}

// Version comparison types
export interface VersionComparison {
  oldCql: string
  newCql: string
  oldVersion: string
  newVersion: string
  metadataChanges?: string[]
  populationChanges?: string[]
}

// Bundle import result
export interface BundleImportResult {
  measure: MeasureDefinition
  librariesImported: number
  librariesSkipped: number
  valueSetsFound: number
}

// Dashboard types
export interface DashboardSummary {
  totalMeasures: number
  byStatus: Record<string, number>
  byScoring: Record<string, number>
  bySteward: Record<string, number>
  recentEvaluations: DashboardEvaluation[]
  pendingReview: DashboardPendingReview[]
}

export interface DashboardEvaluation {
  id: number
  measureName: string
  score?: number
  status?: string
  createdAt?: string
}

export interface DashboardPendingReview {
  id: number
  name: string
  version: string
  owner?: string
}

// Batch evaluation
export interface BatchEvaluationRequest {
  measureIds: number[]
  fhirServerUrl?: string
  periodStart?: string
  periodEnd?: string
}

export interface BatchEvaluationResult {
  results: MeasureEvaluationResult[]
  totalMeasures: number
  successCount: number
  errorCount: number
  totalDurationMs: number
}

// StructureDefinition / Visual Builder types
export interface ElementMetadata {
  name: string
  path: string
  type: string
  isArray: boolean
  isRequired: boolean
  min: number
  max: string
  isChoiceType: boolean
  choiceTypes: string[]
  bindingStrength: string | null
  bindingValueSetUrl: string | null
  bindingCodeSystemUrl: string | null
  boundCodes: string[]
  children: ElementMetadata[]
  description: string | null
  referenceTargets: string[]
}

export interface ResourceElementMetadata {
  resourceType: string
  elements: ElementMetadata[]
}

export interface BundleEntry {
  id: string
  resourceType: string
  resourceData: Record<string, unknown>
}

export type BundleBuilderAction =
  | { type: 'ADD_ENTRY'; payload: BundleEntry }
  | { type: 'REMOVE_ENTRY'; payload: string }
  | { type: 'UPDATE_ENTRY'; payload: { id: string; resourceData: Record<string, unknown> } }
  | { type: 'SET_ACTIVE_ENTRY'; payload: string | null }
  | { type: 'LOAD_FROM_JSON'; payload: BundleEntry[] }

export interface BundleBuilderState {
  entries: BundleEntry[]
  activeEntryId: string | null
}

// Audit Dashboard types
export interface AuditLogEntry {
  id: number
  username: string
  method: string
  path: string
  resourceType?: string
  resourceId?: string
  action: string
  statusCode?: number
  ipAddress?: string
  userAgent?: string
  responseTimeMs?: number
  phiAccess: boolean
  queryParameters?: string
  createdAt: string
}

export interface AuditLogResponse {
  content: AuditLogEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditLogSearchParams {
  username?: string
  action?: string
  resourceType?: string
  startDate?: string
  endDate?: string
  statusCode?: number
  page?: number
  size?: number
}

export interface UserActivitySummary {
  username: string
  eventCount: number
  lastActivityAt?: string
}

export interface DailyActivityCount {
  date: string
  count: number
}

export interface AuditStatsResponse {
  totalEventsToday: number
  totalEventsWeek: number
  totalEventsMonth: number
  phiAccessCount: number
  failedLoginAttempts: number
  activeUsersToday: number
  actionCounts: Record<string, number>
  topUsers: UserActivitySummary[]
  dailyActivity: DailyActivityCount[]
}

// Data Requirements (extracted from ELM)
export interface DataRequirementInfo {
  type: string
  profile?: string[]
  codeFilter?: CodeFilterInfo[]
  dateFilter?: DateFilterInfo[]
}

export interface CodeFilterInfo {
  path: string
  valueSet?: string
  valueSetName?: string
  codeSystemUrl?: string
  codeSystemName?: string
  code?: CodingInfo[]
}

export interface DateFilterInfo {
  path: string
}

export interface CodingInfo {
  system?: string
  code?: string
  display?: string
}

// Enhanced Dashboard types
export interface EnhancedDashboardData {
  totalMeasures: number
  byStatus: Record<string, number>
  byScoring: Record<string, number>
  departmentScores: Record<string, number>
  alerts: ThresholdAlert[]
  recentTrends?: TrendSeriesPoint[]
  recentEvaluations: EnhancedDashboardEvaluation[]
}

export interface ThresholdAlert {
  measureId: number
  measureName: string
  thresholdType: string
  thresholdValue: number
  actualScore: number
  comparisonOperator: string
  department?: string
  severity: string
}

export interface TrendSeriesPoint {
  period: string
  measureName: string
  measureId?: number
  score?: number
}

export interface MeasureThreshold {
  id?: number
  measureDefinitionId: number
  thresholdType: string
  thresholdValue: number
  comparisonOperator: string
  department?: string
  active?: boolean
}

export interface EnhancedDashboardEvaluation {
  id: number
  measureName: string
  score?: number
  status?: string
  department?: string
  createdAt?: string
}

export interface QualityReportData {
  reportType: string
  periodLabel: string
  department?: string
  totalMeasures: number
  measuresAboveTarget: number
  measuresBelowTarget: number
  averageScore: number
  measureScores: MeasureScoreSummary[]
  departmentAverages: Record<string, number>
}

export interface MeasureScoreSummary {
  measureId: number
  measureName: string
  score?: number
  status: string
  targetThreshold?: number
}

// Department types
export interface Department {
  id?: number
  code: string
  name: string
  description?: string
  parentCode?: string
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

// Indicator Catalog types
export interface IndicatorCatalogEntry {
  id?: number
  code: string
  name: string
  nameEn?: string
  category?: string
  subcategory?: string
  description?: string
  source: string
  version?: string
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

// CQL Repository
export interface RepositoryLibrary {
  name: string
  version: string
  description: string
  filename: string
}

// EHR/HIS Integration types
export interface EhrConnection {
  id?: number
  name: string
  fhirServerUrl: string
  authType: 'none' | 'basic' | 'bearer' | 'smart_backend'
  credentials?: string
  department?: string
  tokenEndpoint?: string
  status?: string
  lastTestedAt?: string
  lastTestMessage?: string
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface PatientSearchResult {
  id: string
  name: string
  birthDate?: string
  gender?: string
  identifier?: string
}

export interface PatientImportPreview {
  patientId: string
  patientName: string
  resourceCounts: Record<string, number>
  totalResources: number
}

export interface PatientImport {
  id?: number
  connectionId: number
  patientFhirId: string
  patientIdentifier?: string
  patientName?: string
  resourceCount: number
  targetMeasureId?: number
  targetTestCaseId?: number
  importedBy?: string
  createdAt?: string
}
