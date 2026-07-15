import { api } from './client'

/** Tenant (clinic) management — platform operator only (PAT-201 / #699). */

export interface TenantSummary {
  id: number
  code: string
  name: string
  active: boolean
  createdAt: string | null
}

export interface TenantUser {
  id: number
  username: string
  role: string
  enabled: boolean
  /** Null for legacy default-tenant users whose tenant_id was never assigned. */
  tenantId: number | null
}

export interface TenantCreateRequest {
  code: string
  name: string
}

export const tenantApi = {
  listTenants: async (): Promise<TenantSummary[]> => {
    const response = await api.get<TenantSummary[]>('/admin/tenants')
    return response.data
  },

  createTenant: async (request: TenantCreateRequest): Promise<TenantSummary> => {
    const response = await api.post<TenantSummary>('/admin/tenants', request)
    return response.data
  },

  setTenantActive: async (id: number, active: boolean): Promise<TenantSummary> => {
    const response = await api.put<TenantSummary>(`/admin/tenants/${id}/active`, { active })
    return response.data
  },

  listTenantUsers: async (id: number): Promise<TenantUser[]> => {
    const response = await api.get<TenantUser[]>(`/admin/tenants/${id}/users`)
    return response.data
  },

  /** Assign a user to the tenant — the backend invalidates the user's sessions. */
  assignUser: async (tenantId: number, userId: number): Promise<TenantUser> => {
    const response = await api.put<TenantUser>(`/admin/tenants/${tenantId}/users/${userId}`)
    return response.data
  },
}
