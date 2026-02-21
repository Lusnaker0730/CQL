import axios from 'axios'
import type { Department } from '../types'

const API = '/api/departments'

export const departmentApi = {
  getAll: async () => {
    const { data } = await axios.get<Department[]>(API)
    return data
  },

  getByCode: async (code: string) => {
    const { data } = await axios.get<Department>(`${API}/${code}`)
    return data
  },

  getChildren: async (code: string) => {
    const { data } = await axios.get<Department[]>(`${API}/${code}/children`)
    return data
  },

  create: async (dept: Department) => {
    const { data } = await axios.post<Department>(API, dept)
    return data
  },

  update: async (code: string, dept: Department) => {
    const { data } = await axios.put<Department>(`${API}/${code}`, dept)
    return data
  },
}
