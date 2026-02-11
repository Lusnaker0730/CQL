import axios from 'axios'
import type {
  CqlTranslationRequest,
  CqlTranslationResponse,
  CqlExecutionRequest,
  CqlExecutionResponse,
  CqlLibrary,
  LibraryMetadata,
  CdsServiceDefinition,
  CdsRequest,
  CdsResponse,
  CdsServiceConfigRequest,
  CdsServiceConfigResponse,
  CdsFeedbackRequest,
  CdsServiceAnalytics,
  CdsSandboxRequest,
  ApiKey,
  MeasureEvaluationRequest,
  MeasureEvaluationResult,
  MeasureDefinition,
  MeasureAuditEntry,
  ValidationReport,
  MeasureReport,
  MeasureSchedule,
  MeasureComparisonResult,
  MeasureTrendResult,
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  User,
  UserFavorite,
  UserRecent,
  ValueSetSearchResult,
  ValueSetExpansion,
  CodeLookupResult,
  CodeSearchResult,
  CodeValidationResult,
  FhirValidationResult,
  PatientSearchParams,
  BulkExportParams,
  BulkExportKickOffResult,
  BulkExportStatusResult,
  CacheStats,
  TestCase,
  TestCaseRunResult,
  CoverageResult,
  VersionComparison,
  IgPackageMetadata,
  ProfileSummary,
  ValueSetSummary,
  CodeSystemSummary,
  BundleImportResult,
  DashboardSummary,
  BatchEvaluationRequest,
  BatchEvaluationResult,
  RepositoryLibrary,
  ResourceElementMetadata,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  ChangePasswordRequest,
  AdminResetPasswordResponse,
  UserSummary,
  AdminCreateUserRequest,
  AuditLogResponse,
  AuditLogSearchParams,
  AuditStatsResponse,
} from '../types'
import type { ArtifactSummary, Artifact, ArtifactRequest, FormTemplateCategory, ModifierDefinition, ExternalCqlLibrary, ArtifactTestResult, DeployResult, SaveLibraryResult, CqlImportResult, QueryBuilderResource, QueryBuilderOperator, TwcoreCatalogEntry, TwcoreCodeSystem } from '../types/authoring'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor: attach JWT token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor: handle 401/403 authentication errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: async (request: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/login', request)
    return response.data
  },

  register: async (request: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/register', request)
    return response.data
  },

  getMe: async (): Promise<User> => {
    const response = await api.get<User>('/auth/me')
    return response.data
  },

  forgotPassword: async (request: ForgotPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/forgot-password', request)
    return response.data
  },

  resetPassword: async (request: ResetPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/reset-password', request)
    return response.data
  },

  changePassword: async (request: ChangePasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/change-password', request)
    return response.data
  },
}

export const adminApi = {
  listUsers: async (): Promise<UserSummary[]> => {
    const response = await api.get<UserSummary[]>('/admin/users')
    return response.data
  },

  resetUserPassword: async (userId: number): Promise<AdminResetPasswordResponse> => {
    const response = await api.post<AdminResetPasswordResponse>(`/admin/users/${userId}/reset-password`)
    return response.data
  },

  createUser: async (request: AdminCreateUserRequest): Promise<UserSummary> => {
    const response = await api.post<UserSummary>('/admin/users', request)
    return response.data
  },

  updateUserRole: async (userId: number, role: string): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/admin/users/${userId}/role`, { role })
    return response.data
  },

  updateUserEnabled: async (userId: number, enabled: boolean): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/admin/users/${userId}/enabled`, { enabled })
    return response.data
  },

  // Audit Dashboard
  getAuditLogs: async (params: AuditLogSearchParams): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/logs', { params })
    return response.data
  },

  exportAuditLogs: async (params: AuditLogSearchParams): Promise<Blob> => {
    const response = await api.get('/admin/audit/logs/export', {
      params,
      responseType: 'blob',
    })
    return response.data
  },

  getAuditStats: async (): Promise<AuditStatsResponse> => {
    const response = await api.get<AuditStatsResponse>('/admin/audit/stats')
    return response.data
  },

  getPhiAccess: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/phi-access', {
      params: { page, size, startDate },
    })
    return response.data
  },

  getLoginActivity: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/login-activity', {
      params: { page, size, startDate },
    })
    return response.data
  },

  getSecurityEvents: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/security-events', {
      params: { page, size, startDate },
    })
    return response.data
  },
}

export const cqlApi = {
  translate: async (request: CqlTranslationRequest): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>('/cql/translate', request)
    return response.data
  },

  validate: async (cql: string): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>('/cql/validate', { cql })
    return response.data
  },

  execute: async (request: CqlExecutionRequest): Promise<CqlExecutionResponse> => {
    const response = await api.post<CqlExecutionResponse>('/cql/execute', request)
    return response.data
  },

  getLibraries: async (search?: string): Promise<CqlLibrary[]> => {
    const params = search ? { search } : {}
    const response = await api.get<CqlLibrary[]>('/cql/libraries', { params })
    return response.data
  },

  getLibrary: async (id: string): Promise<CqlLibrary> => {
    const response = await api.get<CqlLibrary>(`/cql/libraries/${id}`)
    return response.data
  },

  createLibrary: async (cql: string, description?: string): Promise<CqlLibrary> => {
    const response = await api.post<CqlLibrary>('/cql/libraries', { cql, description })
    return response.data
  },

  updateLibrary: async (id: string, cql: string, description?: string): Promise<CqlLibrary> => {
    const response = await api.put<CqlLibrary>(`/cql/libraries/${id}`, { cql, description })
    return response.data
  },

  deleteLibrary: async (id: string): Promise<void> => {
    await api.delete(`/cql/libraries/${id}`)
  },

  getLatestLibrary: async (name: string): Promise<CqlLibrary> => {
    const response = await api.get<CqlLibrary>(`/cql/libraries/latest/${encodeURIComponent(name)}`)
    return response.data
  },

  getLibraryVersions: async (name: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/versions/${encodeURIComponent(name)}`)
    return response.data
  },

  getLibrariesMetadata: async (): Promise<LibraryMetadata[]> => {
    const response = await api.get<LibraryMetadata[]>('/cql/libraries/metadata')
    return response.data
  },

  exportFhirLibrary: async (id: string): Promise<unknown> => {
    const response = await api.get(`/cql/libraries/${id}/fhir`)
    return response.data
  },

  importFhirLibrary: async (fhirLibrary: unknown): Promise<CqlLibrary> => {
    const response = await api.post<CqlLibrary>('/cql/libraries/import/fhir', fhirLibrary)
    return response.data
  },

  // Version Management
  createLibraryVersion: async (name: string, type: string = 'minor'): Promise<CqlLibrary> => {
    const response = await api.post<CqlLibrary>(`/cql/libraries/${encodeURIComponent(name)}/version?type=${type}`)
    return response.data
  },

  getLibraryHistory: async (name: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/${encodeURIComponent(name)}/history`)
    return response.data
  },

  compareLibraryVersions: async (oldId: string, newId: string): Promise<VersionComparison> => {
    const response = await api.get<VersionComparison>(`/cql/libraries/compare?oldId=${encodeURIComponent(oldId)}&newId=${encodeURIComponent(newId)}`)
    return response.data
  },

  // Sharing & Permissions
  shareLibrary: async (id: string, targetUsername: string): Promise<CqlLibrary> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/share`, { targetUsername, currentUser })
    return response.data
  },

  unshareLibrary: async (id: string, targetUsername: string): Promise<CqlLibrary> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/unshare`, { targetUsername, currentUser })
    return response.data
  },

  transferOwnership: async (id: string, newOwner: string): Promise<CqlLibrary> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/transfer`, { newOwner, currentUser })
    return response.data
  },

  setAccessLevel: async (id: string, accessLevel: string): Promise<CqlLibrary> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.put<CqlLibrary>(`/cql/libraries/${id}/access`, { accessLevel, currentUser })
    return response.data
  },

  getLibrariesByOwner: async (username: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/owner/${encodeURIComponent(username)}`)
    return response.data
  },

  getSharedLibraries: async (username: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/shared/${encodeURIComponent(username)}`)
    return response.data
  },

  // CQL Repository
  getRepositoryLibraries: async (): Promise<RepositoryLibrary[]> => {
    const response = await api.get<RepositoryLibrary[]>('/cql/libraries/repository')
    return response.data
  },

  importRepositoryLibrary: async (name: string): Promise<CqlLibrary> => {
    const response = await api.post<CqlLibrary>(`/cql/libraries/repository/${encodeURIComponent(name)}/import`)
    return response.data
  },

  // Dependency Analysis
  getDependencies: async (id: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/${id}/dependencies`)
    return response.data
  },

  getDependents: async (name: string): Promise<CqlLibrary[]> => {
    const response = await api.get<CqlLibrary[]>(`/cql/libraries/dependents/${encodeURIComponent(name)}`)
    return response.data
  },
}

export const userPrefsApi = {
  getFavorites: async (): Promise<UserFavorite[]> => {
    const response = await api.get<UserFavorite[]>('/cql/user-prefs/favorites')
    return response.data
  },

  addFavorite: async (libraryId: string): Promise<void> => {
    await api.post(`/cql/user-prefs/favorites/${libraryId}`)
  },

  removeFavorite: async (libraryId: string): Promise<void> => {
    await api.delete(`/cql/user-prefs/favorites/${libraryId}`)
  },

  getRecent: async (): Promise<UserRecent[]> => {
    const response = await api.get<UserRecent[]>('/cql/user-prefs/recent')
    return response.data
  },

  addRecent: async (libraryId: string): Promise<void> => {
    await api.post(`/cql/user-prefs/recent/${libraryId}`)
  },

  clearRecent: async (): Promise<void> => {
    await api.delete('/cql/user-prefs/recent')
  },
}

const cdsApi = axios.create({
  baseURL: import.meta.env.VITE_CDS_URL || '/cds-services',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach JWT token for sandbox requests
cdsApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export const cdsHooksApi = {
  discover: async (): Promise<{ services: CdsServiceDefinition[] }> => {
    const response = await cdsApi.get<{ services: CdsServiceDefinition[] }>('/')
    return response.data
  },

  invoke: async (serviceId: string, request: CdsRequest): Promise<CdsResponse> => {
    const response = await cdsApi.post<CdsResponse>(`/${serviceId}`, request)
    return response.data
  },

  // CDS Service Management APIs
  getAllServices: async (): Promise<CdsServiceConfigResponse[]> => {
    const response = await api.get<CdsServiceConfigResponse[]>('/cds/services')
    return response.data
  },

  getService: async (id: string): Promise<CdsServiceConfigResponse> => {
    const response = await api.get<CdsServiceConfigResponse>(`/cds/services/${id}`)
    return response.data
  },

  createService: async (request: CdsServiceConfigRequest): Promise<CdsServiceConfigResponse> => {
    const response = await api.post<CdsServiceConfigResponse>('/cds/services', request)
    return response.data
  },

  updateService: async (id: string, request: CdsServiceConfigRequest): Promise<CdsServiceConfigResponse> => {
    const response = await api.put<CdsServiceConfigResponse>(`/cds/services/${id}`, request)
    return response.data
  },

  deleteService: async (id: string): Promise<void> => {
    await api.delete(`/cds/services/${id}`)
  },

  enableService: async (id: string): Promise<CdsServiceConfigResponse> => {
    const response = await api.patch<CdsServiceConfigResponse>(`/cds/services/${id}/enable`)
    return response.data
  },

  disableService: async (id: string): Promise<CdsServiceConfigResponse> => {
    const response = await api.patch<CdsServiceConfigResponse>(`/cds/services/${id}/disable`)
    return response.data
  },

  toggleShared: async (id: string, shared: boolean): Promise<CdsServiceConfigResponse> => {
    const response = await api.patch<CdsServiceConfigResponse>(`/cds/services/${id}/share?shared=${shared}`)
    return response.data
  },

  // Feedback
  submitFeedback: async (serviceId: string, feedback: CdsFeedbackRequest): Promise<void> => {
    await cdsApi.post(`/${serviceId}/feedback`, feedback)
  },

  // Versioning
  getServiceVersions: async (serviceName: string): Promise<CdsServiceConfigResponse[]> => {
    const response = await api.get<CdsServiceConfigResponse[]>(`/cds/services/${serviceName}/versions`)
    return response.data
  },

  rollbackService: async (serviceName: string, version: number): Promise<CdsServiceConfigResponse> => {
    const response = await api.post<CdsServiceConfigResponse>(`/cds/services/${serviceName}/rollback/${version}`)
    return response.data
  },

  // Analytics
  getAllAnalytics: async (): Promise<CdsServiceAnalytics[]> => {
    const response = await api.get<CdsServiceAnalytics[]>('/cds/services/analytics')
    return response.data
  },

  getServiceAnalytics: async (serviceId: string): Promise<CdsServiceAnalytics> => {
    const response = await api.get<CdsServiceAnalytics>(`/cds/services/${serviceId}/analytics`)
    return response.data
  },

  // Sandbox
  sandboxInvoke: async (serviceId: string, request: CdsSandboxRequest): Promise<CdsResponse> => {
    const response = await cdsApi.post<CdsResponse>(`/${serviceId}/sandbox`, request)
    return response.data
  },
}

export const apiKeyApi = {
  listKeys: async (): Promise<ApiKey[]> => {
    const response = await api.get<ApiKey[]>('/user/api-keys')
    return response.data
  },

  generateKey: async (name: string): Promise<ApiKey> => {
    const response = await api.post<ApiKey>('/user/api-keys', { name })
    return response.data
  },

  revokeKey: async (id: number): Promise<void> => {
    await api.delete(`/user/api-keys/${id}`)
  },
}

export const measureApi = {
  // Evaluation
  evaluate: async (request: MeasureEvaluationRequest): Promise<MeasureEvaluationResult> => {
    const response = await api.post<MeasureEvaluationResult>('/measures/evaluate', request)
    return response.data
  },

  evaluateMeasure: async (
    measureId: string,
    subject?: string,
    periodStart?: string,
    periodEnd?: string,
    fhirServerUrl?: string
  ): Promise<MeasureEvaluationResult> => {
    const params = new URLSearchParams()
    if (subject) params.append('subject', subject)
    if (periodStart) params.append('periodStart', periodStart)
    if (periodEnd) params.append('periodEnd', periodEnd)

    const response = await api.post<MeasureEvaluationResult>(
      `/measures/${measureId}/$evaluate-measure?${params.toString()}`,
      { fhirServerUrl }
    )
    return response.data
  },

  // Measure Definition CRUD
  getMeasures: async (search?: string): Promise<MeasureDefinition[]> => {
    const params = search ? { search } : {}
    const response = await api.get<MeasureDefinition[]>('/measures', { params })
    return response.data
  },

  getMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.get<MeasureDefinition>(`/measures/${id}`)
    return response.data
  },

  createMeasure: async (definition: MeasureDefinition): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>('/measures', definition)
    return response.data
  },

  updateMeasure: async (id: number, definition: MeasureDefinition): Promise<MeasureDefinition> => {
    const response = await api.put<MeasureDefinition>(`/measures/${id}`, definition)
    return response.data
  },

  deleteMeasure: async (id: number): Promise<void> => {
    await api.delete(`/measures/${id}`)
  },

  // CQL Expressions (for population criteria mapping)
  getCqlExpressions: async (measureId: number): Promise<{ name: string; context: string; accessLevel: string; resultType: string | null }[]> => {
    const response = await api.get(`/measures/${measureId}/cql-expressions`)
    return response.data
  },

  // FHIR Import/Export
  importFhirMeasure: async (fhirMeasure: unknown): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>('/measures/import/fhir', fhirMeasure)
    return response.data
  },

  exportFhirMeasure: async (id: number): Promise<unknown> => {
    const response = await api.get(`/measures/${id}/fhir`)
    return response.data
  },

  // Reports
  getReports: async (): Promise<MeasureReport[]> => {
    const response = await api.get<MeasureReport[]>('/measures/reports')
    return response.data
  },

  getReportsForMeasure: async (measureId: number): Promise<MeasureReport[]> => {
    const response = await api.get<MeasureReport[]>(`/measures/${measureId}/reports`)
    return response.data
  },

  getReport: async (reportId: number): Promise<MeasureReport> => {
    const response = await api.get<MeasureReport>(`/measures/reports/${reportId}`)
    return response.data
  },

  deleteReport: async (reportId: number): Promise<void> => {
    await api.delete(`/measures/reports/${reportId}`)
  },

  exportReport: async (reportId: number, format: string): Promise<Blob> => {
    const response = await api.get(`/measures/reports/${reportId}/export`, {
      params: { format },
      responseType: 'blob',
    })
    return response.data
  },

  // Schedules
  getSchedules: async (measureId: number): Promise<MeasureSchedule[]> => {
    const response = await api.get<MeasureSchedule[]>(`/measures/${measureId}/schedules`)
    return response.data
  },

  createSchedule: async (measureId: number, schedule: Partial<MeasureSchedule>): Promise<MeasureSchedule> => {
    const response = await api.post<MeasureSchedule>(`/measures/${measureId}/schedules`, schedule)
    return response.data
  },

  updateSchedule: async (scheduleId: number, schedule: Partial<MeasureSchedule>): Promise<MeasureSchedule> => {
    const response = await api.put<MeasureSchedule>(`/measures/schedules/${scheduleId}`, schedule)
    return response.data
  },

  deleteSchedule: async (scheduleId: number): Promise<void> => {
    await api.delete(`/measures/schedules/${scheduleId}`)
  },

  triggerSchedule: async (scheduleId: number): Promise<MeasureEvaluationResult> => {
    const response = await api.post<MeasureEvaluationResult>(`/measures/schedules/${scheduleId}/trigger`)
    return response.data
  },

  // Comparison & Trends
  comparePeriods: async (
    measureName: string,
    p1Start: string,
    p1End: string,
    p2Start: string,
    p2End: string
  ): Promise<MeasureComparisonResult> => {
    const params = new URLSearchParams({ measureName, p1Start, p1End, p2Start, p2End })
    const response = await api.get<MeasureComparisonResult>(`/measures/compare?${params.toString()}`)
    return response.data
  },

  getTrend: async (measureName: string, periods: number = 4): Promise<MeasureTrendResult> => {
    const params = new URLSearchParams({ measureName, periods: periods.toString() })
    const response = await api.get<MeasureTrendResult>(`/measures/trend?${params.toString()}`)
    return response.data
  },

  // Test Cases
  getTestCases: async (measureId: number): Promise<TestCase[]> => {
    const response = await api.get<TestCase[]>(`/measures/${measureId}/test-cases`)
    return response.data
  },

  getTestCase: async (measureId: number, testCaseId: number): Promise<TestCase> => {
    const response = await api.get<TestCase>(`/measures/${measureId}/test-cases/${testCaseId}`)
    return response.data
  },

  createTestCase: async (measureId: number, testCase: TestCase): Promise<TestCase> => {
    const response = await api.post<TestCase>(`/measures/${measureId}/test-cases`, testCase)
    return response.data
  },

  updateTestCase: async (measureId: number, testCaseId: number, testCase: TestCase): Promise<TestCase> => {
    const response = await api.put<TestCase>(`/measures/${measureId}/test-cases/${testCaseId}`, testCase)
    return response.data
  },

  deleteTestCase: async (measureId: number, testCaseId: number): Promise<void> => {
    await api.delete(`/measures/${measureId}/test-cases/${testCaseId}`)
  },

  runTestCase: async (measureId: number, testCaseId: number): Promise<TestCaseRunResult> => {
    const response = await api.post<TestCaseRunResult>(`/measures/${measureId}/test-cases/${testCaseId}/run`)
    return response.data
  },

  runAllTestCases: async (measureId: number): Promise<TestCaseRunResult[]> => {
    const response = await api.post<TestCaseRunResult[]>(`/measures/${measureId}/test-cases/run`)
    return response.data
  },

  runWithCoverage: async (measureId: number, testCaseId: number): Promise<CoverageResult> => {
    const response = await api.post<CoverageResult>(`/measures/${measureId}/test-cases/${testCaseId}/run-with-coverage`)
    return response.data
  },

  // Version Management
  createMeasureVersion: async (id: number, type: string = 'minor'): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/version?type=${type}`)
    return response.data
  },

  getMeasureHistory: async (id: number): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/${id}/history`)
    return response.data
  },

  compareMeasureVersions: async (oldId: number, newId: number): Promise<VersionComparison> => {
    const response = await api.get<VersionComparison>(`/measures/version-compare?oldId=${oldId}&newId=${newId}`)
    return response.data
  },

  // Sharing & Permissions
  shareMeasure: async (id: number, targetUsername: string): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/share`, { targetUsername, currentUser })
    return response.data
  },

  unshareMeasure: async (id: number, targetUsername: string): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/unshare`, { targetUsername, currentUser })
    return response.data
  },

  transferMeasureOwnership: async (id: number, newOwner: string): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/transfer`, { newOwner, currentUser })
    return response.data
  },

  setMeasureAccessLevel: async (id: number, accessLevel: string): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.put<MeasureDefinition>(`/measures/${id}/access`, { accessLevel, currentUser })
    return response.data
  },

  getMeasuresByOwner: async (username: string): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/owner/${encodeURIComponent(username)}`)
    return response.data
  },

  getSharedMeasures: async (username: string): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/shared/${encodeURIComponent(username)}`)
    return response.data
  },

  // Workflow
  submitForReview: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/submit-for-review`, { currentUser })
    return response.data
  },

  approveMeasure: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/approve`, { currentUser })
    return response.data
  },

  rejectMeasure: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/reject`, { currentUser })
    return response.data
  },

  retireMeasure: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/retire`, { currentUser })
    return response.data
  },

  // Locking
  lockMeasure: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/lock`, { currentUser })
    return response.data
  },

  unlockMeasure: async (id: number): Promise<MeasureDefinition> => {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}').username || 'anonymous'
    const response = await api.post<MeasureDefinition>(`/measures/${id}/unlock`, { currentUser })
    return response.data
  },

  // Validation
  validateMeasure: async (id: number): Promise<ValidationReport> => {
    const response = await api.post<ValidationReport>(`/measures/${id}/validate`)
    return response.data
  },

  quickValidateMeasure: async (id: number): Promise<ValidationReport> => {
    const response = await api.post<ValidationReport>(`/measures/${id}/validate/quick`)
    return response.data
  },

  // Audit Trail
  getAuditTrail: async (id: number): Promise<MeasureAuditEntry[]> => {
    const response = await api.get<MeasureAuditEntry[]>(`/measures/${id}/audit`)
    return response.data
  },

  // Bundle Export/Import
  exportBundle: async (id: number, format: string = 'json'): Promise<Blob> => {
    const response = await api.get(`/measures/${id}/export/bundle`, {
      params: { format },
      responseType: 'blob',
    })
    return response.data
  },

  exportCql: async (id: number): Promise<Blob> => {
    const response = await api.get(`/measures/${id}/export/cql`, {
      responseType: 'blob',
    })
    return response.data
  },

  exportElm: async (id: number): Promise<Blob> => {
    const response = await api.get(`/measures/${id}/export/elm`, {
      responseType: 'blob',
    })
    return response.data
  },

  exportHqmf: async (id: number): Promise<Blob> => {
    const response = await api.get(`/measures/${id}/export/hqmf`, {
      responseType: 'blob',
    })
    return response.data
  },

  importBundle: async (json: unknown): Promise<BundleImportResult> => {
    const response = await api.post<BundleImportResult>('/measures/import/bundle', json)
    return response.data
  },

  // Dashboard
  getDashboard: async (): Promise<DashboardSummary> => {
    const response = await api.get<DashboardSummary>('/measures/dashboard')
    return response.data
  },

  // Batch Evaluation
  batchEvaluate: async (request: BatchEvaluationRequest): Promise<BatchEvaluationResult> => {
    const response = await api.post<BatchEvaluationResult>('/measures/batch-evaluate', request)
    return response.data
  },
}

export const fhirApi = {
  search: async (resourceType: string, params?: string, fhirServer?: string): Promise<unknown> => {
    const queryParams = new URLSearchParams()
    if (params) queryParams.append('params', params)
    if (fhirServer) queryParams.append('fhirServer', fhirServer)

    const response = await api.get(`/fhir/${resourceType}?${queryParams.toString()}`)
    return response.data
  },

  read: async (resourceType: string, id: string, fhirServer?: string): Promise<unknown> => {
    const params = fhirServer ? `?fhirServer=${encodeURIComponent(fhirServer)}` : ''
    const response = await api.get(`/fhir/${resourceType}/${id}${params}`)
    return response.data
  },

  expandValueSet: async (url: string, filter?: string): Promise<ValueSetExpansion> => {
    const params = new URLSearchParams({ url })
    if (filter) params.append('filter', filter)
    const response = await api.get<ValueSetExpansion>(`/fhir/ValueSet/$expand?${params.toString()}`)
    return response.data
  },

  searchValueSets: async (title?: string): Promise<ValueSetSearchResult[]> => {
    const params = title ? `?title=${encodeURIComponent(title)}` : ''
    const response = await api.get<ValueSetSearchResult[]>(`/fhir/ValueSet${params}`)
    return response.data
  },

  validateCode: async (system: string, code: string, valueSetUrl: string): Promise<CodeValidationResult> => {
    const params = new URLSearchParams({ system, code, url: valueSetUrl })
    const response = await api.get<CodeValidationResult>(`/fhir/CodeSystem/$validate-code?${params.toString()}`)
    return response.data
  },

  lookupCode: async (system: string, code: string): Promise<CodeLookupResult> => {
    const params = new URLSearchParams({ system, code })
    const response = await api.get<CodeLookupResult>(`/fhir/CodeSystem/$lookup?${params.toString()}`)
    return response.data
  },

  searchCodes: async (system: string, text: string, maxResults: number = 20): Promise<CodeSearchResult[]> => {
    const params = new URLSearchParams({ system, text, maxResults: maxResults.toString() })
    const response = await api.get<CodeSearchResult[]>(`/fhir/CodeSystem/$search-codes?${params.toString()}`)
    return response.data
  },

  validateResource: async (resourceJson: string): Promise<FhirValidationResult> => {
    const response = await api.post<FhirValidationResult>('/fhir/$validate', resourceJson, {
      headers: { 'Content-Type': 'application/json' },
    })
    return response.data
  },

  searchPatientsByDemographics: async (
    params: PatientSearchParams,
    fhirServer?: string
  ): Promise<unknown> => {
    const queryParams = new URLSearchParams()
    if (params.family) queryParams.append('family', params.family)
    if (params.given) queryParams.append('given', params.given)
    if (params.birthdate) queryParams.append('birthdate', params.birthdate)
    if (params.identifier) queryParams.append('identifier', params.identifier)
    if (fhirServer) queryParams.append('fhirServer', fhirServer)
    const response = await api.get(`/fhir/Patient/$search-by-demographics?${queryParams.toString()}`)
    return response.data
  },

  executeTransaction: async (bundleJson: string, fhirServer?: string): Promise<unknown> => {
    const params = fhirServer ? `?fhirServer=${encodeURIComponent(fhirServer)}` : ''
    const response = await api.post(`/fhir/Bundle/$transaction${params}`, bundleJson, {
      headers: { 'Content-Type': 'application/json' },
    })
    return response.data
  },

  vsacGetValueSet: async (oid: string): Promise<unknown> => {
    const response = await api.get(`/fhir/vsac/ValueSet/${encodeURIComponent(oid)}`)
    return response.data
  },

  vsacExpandValueSet: async (oid: string): Promise<unknown> => {
    const response = await api.get(`/fhir/vsac/ValueSet/${encodeURIComponent(oid)}/$expand`)
    return response.data
  },

  vsacSearchValueSets: async (title?: string): Promise<ValueSetSearchResult[]> => {
    const params = title ? `?title=${encodeURIComponent(title)}` : ''
    const response = await api.get<ValueSetSearchResult[]>(`/fhir/vsac/ValueSet${params}`)
    return response.data
  },

  kickOffExport: async (params: BulkExportParams): Promise<BulkExportKickOffResult> => {
    const queryParams = new URLSearchParams({ fhirServer: params.fhirServer })
    if (params.exportType) queryParams.append('exportType', params.exportType)
    if (params._outputFormat) queryParams.append('_outputFormat', params._outputFormat)
    if (params._since) queryParams.append('_since', params._since)
    if (params._type) queryParams.append('_type', params._type)
    const response = await api.post<BulkExportKickOffResult>(`/fhir/$export?${queryParams.toString()}`)
    return response.data
  },

  pollExportStatus: async (statusUrl: string): Promise<BulkExportStatusResult> => {
    const response = await api.get<BulkExportStatusResult>(
      `/fhir/$export-status?statusUrl=${encodeURIComponent(statusUrl)}`
    )
    return response.data
  },

  getCacheStats: async (): Promise<CacheStats> => {
    const response = await api.get<CacheStats>('/fhir/cache/stats')
    return response.data
  },

  evictCache: async (cacheName: string): Promise<void> => {
    await api.delete(`/fhir/cache/${encodeURIComponent(cacheName)}`)
  },

  // Implementation Guide
  getIgPackages: async (): Promise<IgPackageMetadata[]> => {
    const response = await api.get<IgPackageMetadata[]>('/fhir/ig/packages')
    return response.data
  },

  browseProfiles: async (resourceType?: string, search?: string): Promise<ProfileSummary[]> => {
    const params = new URLSearchParams()
    if (resourceType) params.append('resourceType', resourceType)
    if (search) params.append('search', search)
    const query = params.toString()
    const response = await api.get<ProfileSummary[]>(`/fhir/ig/profiles${query ? '?' + query : ''}`)
    return response.data
  },

  getProfile: async (url: string): Promise<unknown> => {
    const response = await api.get(`/fhir/ig/profiles/${encodeURIComponent(url)}`)
    return response.data
  },

  browseIgValueSets: async (search?: string): Promise<ValueSetSummary[]> => {
    const params = search ? `?search=${encodeURIComponent(search)}` : ''
    const response = await api.get<ValueSetSummary[]>(`/fhir/ig/valuesets${params}`)
    return response.data
  },

  getIgValueSet: async (url: string): Promise<unknown> => {
    const response = await api.get(`/fhir/ig/valuesets/${encodeURIComponent(url)}`)
    return response.data
  },

  browseIgCodeSystems: async (search?: string): Promise<CodeSystemSummary[]> => {
    const params = search ? `?search=${encodeURIComponent(search)}` : ''
    const response = await api.get<CodeSystemSummary[]>(`/fhir/ig/codesystems${params}`)
    return response.data
  },

  getIgCodeSystem: async (url: string): Promise<unknown> => {
    const response = await api.get(`/fhir/ig/codesystems/${encodeURIComponent(url)}`)
    return response.data
  },

  // StructureDefinition Metadata (Visual Builder)
  getResourceTypes: async (): Promise<string[]> => {
    const response = await api.get<string[]>('/fhir/structure-definitions/resource-types')
    return response.data
  },

  getResourceMetadata: async (resourceType: string): Promise<ResourceElementMetadata> => {
    const response = await api.get<ResourceElementMetadata>(
      `/fhir/structure-definitions/${encodeURIComponent(resourceType)}`
    )
    return response.data
  },
}

// ===== CDS Authoring API =====

export const authoringApi = {
  listArtifacts: async (): Promise<ArtifactSummary[]> => {
    const response = await api.get<ArtifactSummary[]>('/authoring/artifacts')
    return response.data
  },

  getArtifact: async (id: number): Promise<Artifact> => {
    const response = await api.get<Artifact>(`/authoring/artifacts/${id}`)
    return response.data
  },

  createArtifact: async (request: ArtifactRequest): Promise<Artifact> => {
    const response = await api.post<Artifact>('/authoring/artifacts', request)
    return response.data
  },

  updateArtifact: async (id: number, request: ArtifactRequest): Promise<Artifact> => {
    const response = await api.put<Artifact>(`/authoring/artifacts/${id}`, request)
    return response.data
  },

  deleteArtifact: async (id: number): Promise<void> => {
    await api.delete(`/authoring/artifacts/${id}`)
  },

  duplicateArtifact: async (id: number): Promise<Artifact> => {
    const response = await api.post<Artifact>(`/authoring/artifacts/${id}/duplicate`)
    return response.data
  },

  getTemplates: async (): Promise<FormTemplateCategory[]> => {
    const response = await api.get<FormTemplateCategory[]>('/authoring/templates')
    return response.data
  },

  getModifiers: async (inputType?: string): Promise<ModifierDefinition[]> => {
    const params = inputType ? `?inputType=${encodeURIComponent(inputType)}` : ''
    const response = await api.get<ModifierDefinition[]>(`/authoring/modifiers${params}`)
    return response.data
  },

  generateCql: async (id: number, fhirVersion?: string): Promise<{ cql: string }> => {
    const params = fhirVersion ? `?fhirVersion=${encodeURIComponent(fhirVersion)}` : ''
    const response = await api.post<{ cql: string }>(`/authoring/artifacts/${id}/cql${params}`)
    return response.data
  },

  generateElm: async (id: number): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>(`/authoring/artifacts/${id}/elm`)
    return response.data
  },

  validateArtifactCql: async (id: number): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>(`/authoring/artifacts/${id}/validate`)
    return response.data
  },

  // External CQL Libraries
  listExternalCql: async (artifactId: number): Promise<ExternalCqlLibrary[]> => {
    const response = await api.get<ExternalCqlLibrary[]>(`/authoring/artifacts/${artifactId}/external-cql`)
    return response.data
  },

  getExternalCql: async (artifactId: number, libId: number): Promise<ExternalCqlLibrary> => {
    const response = await api.get<ExternalCqlLibrary>(`/authoring/artifacts/${artifactId}/external-cql/${libId}`)
    return response.data
  },

  uploadExternalCql: async (artifactId: number, file: File): Promise<ExternalCqlLibrary> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post<ExternalCqlLibrary>(
      `/authoring/artifacts/${artifactId}/external-cql/upload`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    return response.data
  },

  deleteExternalCql: async (artifactId: number, libId: number): Promise<void> => {
    await api.delete(`/authoring/artifacts/${artifactId}/external-cql/${libId}`)
  },

  // Testing & Deployment
  testArtifact: async (id: number, patientIds: string[], fhirServerUrl: string): Promise<ArtifactTestResult> => {
    const response = await api.post<ArtifactTestResult>(`/authoring/artifacts/${id}/test`, {
      patientIds,
      fhirServerUrl,
    })
    return response.data
  },

  deployCdsService: async (id: number, serviceId: string, hook: string): Promise<DeployResult> => {
    const response = await api.post<DeployResult>(`/authoring/artifacts/${id}/deploy-cds`, {
      serviceId,
      hook,
    })
    return response.data
  },

  saveAsLibrary: async (id: number): Promise<SaveLibraryResult> => {
    const response = await api.post<SaveLibraryResult>(`/authoring/artifacts/${id}/save-library`)
    return response.data
  },

  // CQL Import
  importCql: async (cql: string): Promise<CqlImportResult> => {
    const response = await api.post<CqlImportResult>('/authoring/import-cql', { cql })
    return response.data
  },

  // Query Builder
  getQueryBuilderResources: async (): Promise<QueryBuilderResource[]> => {
    const response = await api.get<QueryBuilderResource[]>('/authoring/query-builder/resources')
    return response.data
  },

  getQueryBuilderOperators: async (type?: string): Promise<QueryBuilderOperator[]> => {
    const params = type ? `?type=${encodeURIComponent(type)}` : ''
    const response = await api.get<QueryBuilderOperator[]>(`/authoring/query-builder/operators${params}`)
    return response.data
  },

  // TWCORE Catalog
  getTwcoreCatalog: async (resourceType?: string): Promise<TwcoreCatalogEntry[]> => {
    const params = resourceType ? `?resourceType=${encodeURIComponent(resourceType)}` : ''
    const response = await api.get<TwcoreCatalogEntry[]>(`/authoring/twcore-catalog${params}`)
    return response.data
  },

  getTwcoreCodeSystems: async (): Promise<TwcoreCodeSystem[]> => {
    const response = await api.get<TwcoreCodeSystem[]>('/authoring/twcore-catalog/code-systems')
    return response.data
  },
}
