import axios from 'axios'
import type { IndicatorCatalogEntry } from '../types'

const API = '/api/indicators'

export const indicatorApi = {
  search: async (params?: { source?: string; category?: string; search?: string }) => {
    const { data } = await axios.get<IndicatorCatalogEntry[]>(API, { params })
    return data
  },

  getByCode: async (code: string, source = 'MOH') => {
    const { data } = await axios.get<IndicatorCatalogEntry>(`${API}/${code}`, { params: { source } })
    return data
  },

  create: async (entry: IndicatorCatalogEntry) => {
    const { data } = await axios.post<IndicatorCatalogEntry>(API, entry)
    return data
  },

  update: async (code: string, entry: IndicatorCatalogEntry, source = 'MOH') => {
    const { data } = await axios.put<IndicatorCatalogEntry>(`${API}/${code}`, entry, { params: { source } })
    return data
  },

  bulkImport: async (entries: Record<string, unknown>[]) => {
    const { data } = await axios.post<{ created: number; skipped: number; errors: string[] }>(
      `${API}/import`,
      entries
    )
    return data
  },
}
