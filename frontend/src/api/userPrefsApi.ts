import type { UserFavorite, UserRecent } from '../types'
import { api } from './client'

export const userPrefsApi = {
  getFavorites: async (): Promise<UserFavorite[]> => {
    const response = await api.get<UserFavorite[]>('/cql/user-prefs/favorites')
    return response.data
  },

  addFavorite: async (libraryId: string): Promise<void> => {
    await api.post(`/cql/user-prefs/favorites/${libraryId}`)
  },

  removeFavorite: async (libraryId: string): Promise<void> => {
    await api.delete(`/cql/user-prefs/favorites/${libraryId}`)
  },

  getRecent: async (): Promise<UserRecent[]> => {
    const response = await api.get<UserRecent[]>('/cql/user-prefs/recent')
    return response.data
  },

  addRecent: async (libraryId: string): Promise<void> => {
    await api.post(`/cql/user-prefs/recent/${libraryId}`)
  },

  clearRecent: async (): Promise<void> => {
    await api.delete('/cql/user-prefs/recent')
  },
}
