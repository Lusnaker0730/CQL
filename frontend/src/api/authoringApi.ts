import type { CqlTranslationResponse } from '../types'
import type {
  ArtifactSummary,
  Artifact,
  ArtifactRequest,
  FormTemplateCategory,
  ModifierDefinition,
  ExternalCqlLibrary,
  ArtifactTestResult,
  DeployResult,
  SaveLibraryResult,
  CqlImportResult,
  QueryBuilderResource,
  QueryBuilderOperator,
  TwcoreCatalogEntry,
  TwcoreCodeSystem,
} from '../types/authoring'
import { api } from './client'

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

  generateCql: async (id: number): Promise<{ cql: string }> => {
    const response = await api.post<{ cql: string }>(`/authoring/artifacts/${id}/cql`)
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

  uploadExternalCqlContent: async (artifactId: number, cqlContent: string): Promise<ExternalCqlLibrary> => {
    const response = await api.post<ExternalCqlLibrary>(
      `/authoring/artifacts/${artifactId}/external-cql/content`,
      { cqlContent }
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

  // ZIP Export
  exportZip: async (id: number): Promise<Blob> => {
    const response = await api.get(`/authoring/artifacts/${id}/export/zip`, {
      responseType: 'blob',
    })
    return response.data
  },

  // CQL Formatter
  formatCql: async (cql: string): Promise<{ cql: string }> => {
    const response = await api.post<{ cql: string }>('/authoring/format-cql', { cql })
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
