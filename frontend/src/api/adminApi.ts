import type {
  AuditLogSearchParams,
  AuditLogResponse,
  AuditStatsResponse,
  AdminResetPasswordResponse,
  UserSummary,
  AdminCreateUserRequest,
} from '../types'
import { api } from './client'

export const adminApi = {
  listUsers: async (): Promise<UserSummary[]> => {
    const response = await api.get<UserSummary[]>('/admin/users')
    return response.data
  },

  resetUserPassword: async (userId: number): Promise<AdminResetPasswordResponse> => {
    const response = await api.post<AdminResetPasswordResponse>(`/admin/users/${userId}/reset-password`)
    return response.data
  },

  createUser: async (request: AdminCreateUserRequest): Promise<UserSummary> => {
    const response = await api.post<UserSummary>('/admin/users', request)
    return response.data
  },

  updateUserRole: async (userId: number, role: string): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/admin/users/${userId}/role`, { role })
    return response.data
  },

  updateUserEnabled: async (userId: number, enabled: boolean): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/admin/users/${userId}/enabled`, { enabled })
    return response.data
  },

  // Audit Dashboard
  getAuditLogs: async (params: AuditLogSearchParams): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/logs', { params })
    return response.data
  },

  exportAuditLogs: async (params: AuditLogSearchParams): Promise<Blob> => {
    const response = await api.get('/admin/audit/logs/export', {
      params,
      responseType: 'blob',
    })
    return response.data
  },

  getAuditStats: async (): Promise<AuditStatsResponse> => {
    const response = await api.get<AuditStatsResponse>('/admin/audit/stats')
    return response.data
  },

  getPhiAccess: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/phi-access', {
      params: { page, size, startDate },
    })
    return response.data
  },

  getLoginActivity: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/login-activity', {
      params: { page, size, startDate },
    })
    return response.data
  },

  getSecurityEvents: async (page: number = 0, size: number = 20, startDate?: string): Promise<AuditLogResponse> => {
    const response = await api.get<AuditLogResponse>('/admin/audit/security-events', {
      params: { page, size, startDate },
    })
    return response.data
  },
}
