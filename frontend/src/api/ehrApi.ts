import { api } from './client'
import type { EhrConnection, PatientSearchResult, PatientImportPreview, PatientImport, FhirBundleImportResult } from '../types'

const BASE = '/ehr'

// PAT-111: snapshot of one EHR connection's live health, emitted by
// GET /api/ehr/health/overview. Fields mirror
// ConnectionHealthService#ConnectionHealthOverview (Java record).
export interface ConnectionHealthOverview {
  connectionId: number
  connectionName: string
  fhirServerUrl: string
  /** 'healthy' | 'degraded' | 'down' | 'unknown' — wire value, use the healthStatusColor helper to map. */
  currentStatus: string
  lastResponseTimeMs: number | null
  lastCheckedAt: string | null
  avgResponseTimeMs24h: number | null
  availability24h: number | null
  totalChecks24h: number
  errorCount24h: number
}

export const ehrApi = {
  // Connections
  getConnections: async (department?: string): Promise<EhrConnection[]> => {
    const params = department ? { department } : {}
    const { data } = await api.get(`${BASE}/connections`, { params })
    return data
  },

  getConnection: async (id: number): Promise<EhrConnection> => {
    const { data } = await api.get(`${BASE}/connections/${id}`)
    return data
  },

  createConnection: async (connection: Partial<EhrConnection>): Promise<EhrConnection> => {
    const { data } = await api.post(`${BASE}/connections`, connection)
    return data
  },

  updateConnection: async (id: number, connection: Partial<EhrConnection>): Promise<EhrConnection> => {
    const { data } = await api.put(`${BASE}/connections/${id}`, connection)
    return data
  },

  deleteConnection: async (id: number): Promise<void> => {
    await api.delete(`${BASE}/connections/${id}`)
  },

  testConnection: async (id: number): Promise<EhrConnection> => {
    const { data } = await api.post(`${BASE}/connections/${id}/test`)
    return data
  },

  // Patient search
  searchPatients: async (
    connectionId: number,
    params: { nationalId?: string; mrn?: string; family?: string; given?: string }
  ): Promise<PatientSearchResult[]> => {
    const { data } = await api.get(`${BASE}/connections/${connectionId}/patients`, { params })
    return data
  },

  // Patient preview
  getPatientPreview: async (connectionId: number, patientId: string): Promise<PatientImportPreview> => {
    const { data } = await api.get(
      `${BASE}/connections/${connectionId}/patients/${encodeURIComponent(patientId)}/preview`
    )
    return data
  },

  // Import
  importPatient: async (connectionId: number, patientId: string, measureId?: number): Promise<PatientImport> => {
    const params = measureId ? { measureId } : {}
    const { data } = await api.post(
      `${BASE}/connections/${connectionId}/patients/${encodeURIComponent(patientId)}/import`,
      null,
      { params }
    )
    return data
  },

  // PAT-206: import an uploaded FHIR bundle file (e.g. a 健康存摺 / My Health Bank export).
  // No EHR connection required — the bundle is parsed and landed as a tenant-scoped import.
  // TW Core conformance validation is opt-in (slow for large bundles); when requested the
  // result carries the conformant / non-conformant resource counts.
  importFhirBundle: async (
    file: File,
    measureId?: number,
    validate = false,
  ): Promise<FhirBundleImportResult> => {
    const form = new FormData()
    form.append('file', file)
    const params: Record<string, unknown> = { validate }
    if (measureId) params.measureId = measureId
    const { data } = await api.post(`${BASE}/import/fhir-bundle`, form, {
      params,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  getImportHistory: async (importedBy?: string): Promise<PatientImport[]> => {
    const params = importedBy ? { importedBy } : {}
    const { data } = await api.get(`${BASE}/imports`, { params })
    return data
  },

  // PAT-111: live health overview across all configured EHR connections.
  // Polled by the admin table every 30s via React Query refetchInterval.
  getHealthOverview: async (): Promise<ConnectionHealthOverview[]> => {
    const { data } = await api.get(`${BASE}/health/overview`)
    return data
  },
}
