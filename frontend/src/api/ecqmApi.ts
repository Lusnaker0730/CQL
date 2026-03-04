import { api } from './client'
import type {
  EcqmArtifact,
  EcqmArtifactSummary,
  EcqmArtifactRequest,
  PublishResult,
  ScoringTypesResponse,
} from '../types/ecqm'
import type { FormTemplateCategory, ModifierDefinition } from '../types/authoring'

const BASE = '/ecqm'

export const ecqmApi = {
  // CRUD
  listArtifacts: () =>
    api.get<EcqmArtifactSummary[]>(`${BASE}/artifacts`).then((r) => r.data),

  getArtifact: (id: number) =>
    api.get<EcqmArtifact>(`${BASE}/artifacts/${id}`).then((r) => r.data),

  createArtifact: (request: EcqmArtifactRequest) =>
    api.post<EcqmArtifact>(`${BASE}/artifacts`, request).then((r) => r.data),

  updateArtifact: (id: number, request: EcqmArtifactRequest) =>
    api.put<EcqmArtifact>(`${BASE}/artifacts/${id}`, request).then((r) => r.data),

  deleteArtifact: (id: number) =>
    api.delete(`${BASE}/artifacts/${id}`).then((r) => r.data),

  duplicateArtifact: (id: number) =>
    api.post<EcqmArtifact>(`${BASE}/artifacts/${id}/duplicate`).then((r) => r.data),

  // CQL Generation
  generateCql: (id: number) =>
    api.post<{ cql: string; warnings?: string[] }>(`${BASE}/artifacts/${id}/cql`).then((r) => r.data),

  generateElm: (id: number) =>
    api.post(`${BASE}/artifacts/${id}/elm`).then((r) => r.data),

  validateCql: (id: number) =>
    api.post(`${BASE}/artifacts/${id}/validate`).then((r) => r.data),

  // Publish
  publish: (id: number) =>
    api.post<PublishResult>(`${BASE}/artifacts/${id}/publish`).then((r) => r.data),

  // Templates & Modifiers
  getTemplates: () =>
    api.get<FormTemplateCategory[]>(`${BASE}/templates`).then((r) => r.data),

  getModifiers: (inputType?: string) =>
    api.get<ModifierDefinition[]>(`${BASE}/modifiers`, { params: inputType ? { inputType } : {} }).then((r) => r.data),

  getScoringTypes: () =>
    api.get<ScoringTypesResponse>(`${BASE}/scoring-types`).then((r) => r.data),
}
