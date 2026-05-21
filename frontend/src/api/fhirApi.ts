import type {
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
  IgPackageMetadata,
  ProfileSummary,
  ValueSetSummary,
  CodeSystemSummary,
  ResourceElementMetadata,
} from '../types'
import { api } from './client'

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
    // PAT-119: use query-param form; Tomcat rejects encoded slashes in path.
    const response = await api.get(`/fhir/ig/profiles/detail`, { params: { url } })
    return response.data
  },

  browseIgValueSets: async (search?: string): Promise<ValueSetSummary[]> => {
    const params = search ? `?search=${encodeURIComponent(search)}` : ''
    const response = await api.get<ValueSetSummary[]>(`/fhir/ig/valuesets${params}`)
    return response.data
  },

  getIgValueSet: async (url: string): Promise<unknown> => {
    // PAT-119: use query-param form; Tomcat rejects encoded slashes in path.
    const response = await api.get(`/fhir/ig/valuesets/detail`, { params: { url } })
    return response.data
  },

  browseIgCodeSystems: async (search?: string): Promise<CodeSystemSummary[]> => {
    const params = search ? `?search=${encodeURIComponent(search)}` : ''
    const response = await api.get<CodeSystemSummary[]>(`/fhir/ig/codesystems${params}`)
    return response.data
  },

  getIgCodeSystem: async (url: string): Promise<unknown> => {
    // PAT-119: use query-param form; Tomcat rejects encoded slashes in path.
    const response = await api.get(`/fhir/ig/codesystems/detail`, { params: { url } })
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

  createResource: async (resourceType: string, resourceJson: string, fhirServer?: string): Promise<unknown> => {
    const params = fhirServer ? `?fhirServer=${encodeURIComponent(fhirServer)}` : ''
    const response = await api.post(`/fhir/${resourceType}${params}`, resourceJson, {
      headers: { 'Content-Type': 'application/json' },
    })
    return response.data
  },

  updateResource: async (resourceType: string, id: string, resourceJson: string, fhirServer?: string): Promise<unknown> => {
    const params = fhirServer ? `?fhirServer=${encodeURIComponent(fhirServer)}` : ''
    const response = await api.put(`/fhir/${resourceType}/${id}${params}`, resourceJson, {
      headers: { 'Content-Type': 'application/json' },
    })
    return response.data
  },

  deleteResource: async (resourceType: string, id: string, fhirServer?: string): Promise<void> => {
    const params = fhirServer ? `?fhirServer=${encodeURIComponent(fhirServer)}` : ''
    await api.delete(`/fhir/${resourceType}/${id}${params}`)
  },
}
