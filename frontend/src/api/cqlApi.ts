import type {
  CqlTranslationRequest,
  CqlTranslationResponse,
  CqlExecutionRequest,
  CqlExecutionResponse,
  CqlLibrary,
  CqlError,
  CqlFixSuggestionResponse,
  LibraryMetadata,
  VersionComparison,
  RepositoryLibrary,
  DependencyAnalysisResult,
} from '../types'
import { getStoredUsername } from '../utils/validation'
import { api } from './client'

export const cqlApi = {
  translate: async (request: CqlTranslationRequest, signal?: AbortSignal): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>('/cql/translate', request, { signal })
    return response.data
  },

  validate: async (cql: string): Promise<CqlTranslationResponse> => {
    const response = await api.post<CqlTranslationResponse>('/cql/validate', { cql })
    return response.data
  },

  fixSuggestion: async (cql: string, error: CqlError): Promise<CqlFixSuggestionResponse> => {
    const response = await api.post<CqlFixSuggestionResponse>('/cql/fix-suggestion', { cql, error })
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
    const currentUser = getStoredUsername()
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/share`, { targetUsername, currentUser })
    return response.data
  },

  unshareLibrary: async (id: string, targetUsername: string): Promise<CqlLibrary> => {
    const currentUser = getStoredUsername()
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/unshare`, { targetUsername, currentUser })
    return response.data
  },

  transferOwnership: async (id: string, newOwner: string): Promise<CqlLibrary> => {
    const currentUser = getStoredUsername()
    const response = await api.post<CqlLibrary>(`/cql/libraries/${id}/transfer`, { newOwner, currentUser })
    return response.data
  },

  setAccessLevel: async (id: string, accessLevel: string): Promise<CqlLibrary> => {
    const currentUser = getStoredUsername()
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

  analyzeDependencies: async (id: string): Promise<DependencyAnalysisResult> => {
    const response = await api.get<DependencyAnalysisResult>(`/cql/libraries/${id}/dependency-analysis`)
    return response.data
  },
}
