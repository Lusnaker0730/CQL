import axios from 'axios'
import type {
  CqlTranslationRequest,
  CqlTranslationResponse,
  CqlExecutionRequest,
  CqlExecutionResponse,
  CqlLibrary,
  CdsServiceDefinition,
  CdsRequest,
  CdsResponse,
  CdsServiceConfigRequest,
  CdsServiceConfigResponse,
  MeasureEvaluationRequest,
  MeasureEvaluationResult,
} from '../types'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

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
}

const cdsApi = axios.create({
  baseURL: '/cds-services',
  headers: {
    'Content-Type': 'application/json',
  },
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
}

export const measureApi = {
  evaluate: async (request: MeasureEvaluationRequest): Promise<MeasureEvaluationResult> => {
    const response = await api.post<MeasureEvaluationResult>('/measures/evaluate', request)
    return response.data
  },

  evaluateMeasure: async (
    measureId: string,
    subject?: string,
    periodStart?: string,
    periodEnd?: string
  ): Promise<MeasureEvaluationResult> => {
    const params = new URLSearchParams()
    if (subject) params.append('subject', subject)
    if (periodStart) params.append('periodStart', periodStart)
    if (periodEnd) params.append('periodEnd', periodEnd)

    const response = await api.post<MeasureEvaluationResult>(
      `/measures/${measureId}/$evaluate-measure?${params.toString()}`,
      {}
    )
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

  expandValueSet: async (url: string, filter?: string): Promise<unknown> => {
    const params = new URLSearchParams({ url })
    if (filter) params.append('filter', filter)
    const response = await api.get(`/fhir/ValueSet/$expand?${params.toString()}`)
    return response.data
  },

  searchValueSets: async (title?: string): Promise<unknown[]> => {
    const params = title ? `?title=${encodeURIComponent(title)}` : ''
    const response = await api.get(`/fhir/ValueSet${params}`)
    return response.data
  },
}
