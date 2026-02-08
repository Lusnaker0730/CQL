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
  CodeValidationResult,
  FhirValidationResult,
  PatientSearchParams,
  BulkExportParams,
  BulkExportKickOffResult,
  BulkExportStatusResult,
  CacheStats,
} from '../types'

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
}
