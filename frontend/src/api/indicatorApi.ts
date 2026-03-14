import { api } from './client'
import type { IndicatorCatalogEntry } from '../types'

const API = '/indicators'

export const indicatorApi = {
  search: async (params?: { source?: string; category?: string; search?: string }) => {
    const { data } = await api.get<IndicatorCatalogEntry[]>(API, { params })
    return data
  },

  getByCode: async (code: string, source = 'MOH') => {
    const { data } = await api.get<IndicatorCatalogEntry>(`${API}/${code}`, { params: { source } })
    return data
  },

  create: async (entry: IndicatorCatalogEntry) => {
    const { data } = await api.post<IndicatorCatalogEntry>(API, entry)
    return data
  },

  update: async (code: string, entry: IndicatorCatalogEntry, source = 'MOH') => {
    const { data } = await api.put<IndicatorCatalogEntry>(`${API}/${code}`, entry, { params: { source } })
    return data
  },

  bulkImport: async (entries: Record<string, unknown>[]) => {
    const { data } = await api.post<{ created: number; skipped: number; errors: string[] }>(
      `${API}/import`,
      entries
    )
    return data
  },
}
