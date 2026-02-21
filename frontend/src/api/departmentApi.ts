import { api } from './client'
import type { Department } from '../types'

const API = '/departments'

export const departmentApi = {
  getAll: async () => {
    const { data } = await api.get<Department[]>(API)
    return data
  },

  getByCode: async (code: string) => {
    const { data } = await api.get<Department>(`${API}/${code}`)
    return data
  },

  getChildren: async (code: string) => {
    const { data } = await api.get<Department[]>(`${API}/${code}/children`)
    return data
  },

  create: async (dept: Department) => {
    const { data } = await api.post<Department>(API, dept)
    return data
  },

  update: async (code: string, dept: Department) => {
    const { data } = await api.put<Department>(`${API}/${code}`, dept)
    return data
  },
}
