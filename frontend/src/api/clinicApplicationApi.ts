import { api } from './client'

/** Clinic self-service applications (#700). Public submit + operator review. */

export interface ClinicApplicationRequest {
  clinicName: string
  tenantCode: string
  adminUsername: string
  adminEmail: string
}

export interface ClinicApplication {
  id: number
  clinicName: string
  tenantCode: string
  adminUsername: string
  adminEmail: string
  status: 'pending' | 'approved' | 'rejected'
  rejectionReason: string | null
  reviewedBy: string | null
  reviewedAt: string | null
  createdTenantId: number | null
  createdUserId: number | null
  createdAt: string
}

export interface ApprovalResult {
  application: ClinicApplication
  /** One-time password-setup link — show once, never persist client-side. */
  setupLink: string
}

export const clinicApplicationApi = {
  /** Public — anonymous, rate-limited; response is a uniform message. */
  submit: async (request: ClinicApplicationRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/clinic-applications', request)
    return response.data
  },

  list: async (status?: string): Promise<ClinicApplication[]> => {
    const response = await api.get<ClinicApplication[]>('/admin/clinic-applications', {
      params: status ? { status } : undefined,
    })
    return response.data
  },

  approve: async (id: number): Promise<ApprovalResult> => {
    const response = await api.post<ApprovalResult>(`/admin/clinic-applications/${id}/approve`)
    return response.data
  },

  reject: async (id: number, reason: string): Promise<ClinicApplication> => {
    const response = await api.post<ClinicApplication>(`/admin/clinic-applications/${id}/reject`, {
      reason,
    })
    return response.data
  },
}
