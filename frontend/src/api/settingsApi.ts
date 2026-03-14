import { api } from './client'

export interface VsacStatus {
  configured: boolean
  url: string
}

export interface AiStatus {
  enabled: boolean
  provider: 'none' | 'ollama' | 'cloud'
  model: string | null
  configured?: boolean
}

export const settingsApi = {
  getVsacStatus: async (): Promise<VsacStatus> => {
    const response = await api.get<VsacStatus>('/settings/vsac-status')
    return response.data
  },

  updateVsacApiKey: async (apiKey: string): Promise<{ configured: boolean; message: string }> => {
    const response = await api.put<{ configured: boolean; message: string }>('/settings/vsac-api-key', { apiKey })
    return response.data
  },

  getAiStatus: async (): Promise<AiStatus> => {
    const response = await api.get<AiStatus>('/settings/ai-status')
    return response.data
  },

  updateAiApiKey: async (apiKey: string): Promise<{ configured: boolean; message: string }> => {
    const response = await api.put<{ configured: boolean; message: string }>('/settings/ai-api-key', { apiKey })
    return response.data
  },
}
