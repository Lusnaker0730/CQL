import axios, { type InternalAxiosRequestConfig } from 'axios'
import type {
  CdsServiceDefinition,
  CdsRequest,
  CdsResponse,
  CdsServiceConfigRequest,
  CdsServiceConfigResponse,
  CdsFeedbackRequest,
  CdsServiceAnalytics,
  CdsSandboxRequest,
  SandboxPresetRequest,
  SandboxPresetResponse,
} from '../types'
import { api } from './client'

const cdsApi = axios.create({
  baseURL: import.meta.env.VITE_CDS_URL || '/cds-services',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach JWT token for sandbox requests
cdsApi.interceptors.request.use((config: InternalAxiosRequestConfig) => {
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

  // Sandbox Presets
  listPresets: async (): Promise<SandboxPresetResponse[]> => {
    const response = await api.get<SandboxPresetResponse[]>('/sandbox/presets')
    return response.data
  },

  createPreset: async (request: SandboxPresetRequest): Promise<SandboxPresetResponse> => {
    const response = await api.post<SandboxPresetResponse>('/sandbox/presets', request)
    return response.data
  },

  updatePreset: async (id: number, request: SandboxPresetRequest): Promise<SandboxPresetResponse> => {
    const response = await api.put<SandboxPresetResponse>(`/sandbox/presets/${id}`, request)
    return response.data
  },

  deletePreset: async (id: number): Promise<void> => {
    await api.delete(`/sandbox/presets/${id}`)
  },
}
