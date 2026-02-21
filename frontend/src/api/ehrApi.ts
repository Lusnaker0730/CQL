import axios from 'axios'
import type { EhrConnection, PatientSearchResult, PatientImportPreview, PatientImport } from '../types'

const BASE = '/api/ehr'

export const ehrApi = {
  // Connections
  getConnections: async (department?: string): Promise<EhrConnection[]> => {
    const params = department ? { department } : {}
    const { data } = await axios.get(`${BASE}/connections`, { params })
    return data
  },

  getConnection: async (id: number): Promise<EhrConnection> => {
    const { data } = await axios.get(`${BASE}/connections/${id}`)
    return data
  },

  createConnection: async (connection: Partial<EhrConnection>): Promise<EhrConnection> => {
    const { data } = await axios.post(`${BASE}/connections`, connection)
    return data
  },

  updateConnection: async (id: number, connection: Partial<EhrConnection>): Promise<EhrConnection> => {
    const { data } = await axios.put(`${BASE}/connections/${id}`, connection)
    return data
  },

  deleteConnection: async (id: number): Promise<void> => {
    await axios.delete(`${BASE}/connections/${id}`)
  },

  testConnection: async (id: number): Promise<EhrConnection> => {
    const { data } = await axios.post(`${BASE}/connections/${id}/test`)
    return data
  },

  // Patient search
  searchPatients: async (
    connectionId: number,
    params: { nationalId?: string; mrn?: string; family?: string; given?: string }
  ): Promise<PatientSearchResult[]> => {
    const { data } = await axios.get(`${BASE}/connections/${connectionId}/patients`, { params })
    return data
  },

  // Patient preview
  getPatientPreview: async (connectionId: number, patientId: string): Promise<PatientImportPreview> => {
    const { data } = await axios.get(
      `${BASE}/connections/${connectionId}/patients/${encodeURIComponent(patientId)}/preview`
    )
    return data
  },

  // Import
  importPatient: async (connectionId: number, patientId: string, measureId?: number): Promise<PatientImport> => {
    const params = measureId ? { measureId } : {}
    const { data } = await axios.post(
      `${BASE}/connections/${connectionId}/patients/${encodeURIComponent(patientId)}/import`,
      null,
      { params }
    )
    return data
  },

  getImportHistory: async (importedBy?: string): Promise<PatientImport[]> => {
    const params = importedBy ? { importedBy } : {}
    const { data } = await axios.get(`${BASE}/imports`, { params })
    return data
  },
}
