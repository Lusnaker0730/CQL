import type {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  RefreshResponse,
  User,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  ChangePasswordRequest,
  OktaConfig,
  OktaCallbackRequest,
} from '../types'
import { api } from './client'

export const authApi = {
  login: async (request: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/login', request)
    return response.data
  },

  /**
   * PAT-157 — register may return EITHER an AuthResponse (when self-registration
   * is enabled and the username is fresh) OR a plain { message } envelope (when
   * the feature is disabled OR the username is already taken). Callers must
   * detect `response.token` to decide whether to dispatch credentials or just
   * surface the pending-approval message — see LoginPage / LoginForm.
   */
  register: async (request: RegisterRequest): Promise<AuthResponse | { message: string }> => {
    const response = await api.post<AuthResponse | { message: string }>('/auth/register', request)
    return response.data
  },

  getMe: async (): Promise<User> => {
    const response = await api.get<User>('/auth/me')
    return response.data
  },

  forgotPassword: async (request: ForgotPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/forgot-password', request)
    return response.data
  },

  resetPassword: async (request: ResetPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/reset-password', request)
    return response.data
  },

  changePassword: async (request: ChangePasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/change-password', request)
    return response.data
  },

  getOktaConfig: async (): Promise<OktaConfig> => {
    const response = await api.get<OktaConfig>('/auth/okta/config')
    return response.data
  },

  oktaCallback: async (request: OktaCallbackRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/okta/callback', request)
    return response.data
  },

  refresh: async (): Promise<RefreshResponse> => {
    const response = await api.post<RefreshResponse>('/auth/refresh')
    return response.data
  },

  logout: async (): Promise<void> => {
    await api.post('/auth/logout')
  },
}
