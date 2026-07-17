import type { UserSummary, AdminCreateUserRequest } from '../types'
import { api } from './client'

// PAT-214: a clinic ADMIN manages the staff of their OWN tenant. Every call is
// tenant-scoped server-side (effectiveTenantId), so there is no tenant parameter —
// a clinic admin can only ever reach their own clinic's users.
export const tenantUserApi = {
  listUsers: async (): Promise<UserSummary[]> => {
    const response = await api.get<UserSummary[]>('/tenant/users')
    return response.data
  },

  createUser: async (request: AdminCreateUserRequest): Promise<UserSummary> => {
    const response = await api.post<UserSummary>('/tenant/users', request)
    return response.data
  },

  updateUserRole: async (userId: number, role: string): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/tenant/users/${userId}/role`, { role })
    return response.data
  },

  updateUserEnabled: async (userId: number, enabled: boolean): Promise<UserSummary> => {
    const response = await api.put<UserSummary>(`/tenant/users/${userId}/enabled`, { enabled })
    return response.data
  },

  // Returns a one-time setup link for the staff member to set their own password
  // (works without SMTP; the admin relays it). Shown once, no-store.
  resetUserPassword: async (userId: number): Promise<{ setupLink: string }> => {
    const response = await api.post<{ setupLink: string }>(`/tenant/users/${userId}/reset-password`)
    return response.data
  },
}
