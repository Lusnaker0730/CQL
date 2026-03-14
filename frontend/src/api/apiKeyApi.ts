import type { ApiKey } from '../types'
import { api } from './client'

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
