import { api } from './client'

export interface VsacStatus {
  configured: boolean
  url: string
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
}
