import type { PlatformValueSet, PlatformValueSetInput } from '../types'
import { api } from './client'

/** PAT-230 — value sets owned by this installation (`/api/value-sets`), scoped to the caller's tenant. */
export const valueSetApi = {
  list: async (search?: string): Promise<PlatformValueSet[]> => {
    const params = search ? `?search=${encodeURIComponent(search)}` : ''
    const response = await api.get<PlatformValueSet[]>(`/value-sets${params}`)
    return response.data
  },

  get: async (id: number): Promise<PlatformValueSet> => {
    const response = await api.get<PlatformValueSet>(`/value-sets/${id}`)
    return response.data
  },

  create: async (body: PlatformValueSetInput): Promise<PlatformValueSet> => {
    const response = await api.post<PlatformValueSet>('/value-sets', body)
    return response.data
  },

  update: async (id: number, body: PlatformValueSetInput): Promise<PlatformValueSet> => {
    const response = await api.put<PlatformValueSet>(`/value-sets/${id}`, body)
    return response.data
  },

  createVersion: async (id: number, version: string): Promise<PlatformValueSet> => {
    const response = await api.post<PlatformValueSet>(`/value-sets/${id}/versions`, { version })
    return response.data
  },

  activate: async (id: number): Promise<PlatformValueSet> => {
    const response = await api.post<PlatformValueSet>(`/value-sets/${id}/activate`)
    return response.data
  },

  retire: async (id: number): Promise<PlatformValueSet> => {
    const response = await api.post<PlatformValueSet>(`/value-sets/${id}/retire`)
    return response.data
  },

  remove: async (id: number): Promise<void> => {
    await api.delete(`/value-sets/${id}`)
  },

  exportFhir: async (id: number): Promise<unknown> => {
    const response = await api.get<unknown>(`/value-sets/${id}/fhir`)
    return response.data
  },

  importFhir: async (resource: unknown): Promise<PlatformValueSet> => {
    const response = await api.post<PlatformValueSet>('/value-sets/import/fhir', resource)
    return response.data
  },
}
