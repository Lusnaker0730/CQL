import axios, { type InternalAxiosRequestConfig, type AxiosResponse, type AxiosError } from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

// Request interceptor: attach JWT token + X-Request-ID for tracing
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Request-ID'] = crypto.randomUUID()
  return config
})

// Silent refresh + request queuing
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value: unknown) => void
  reject: (reason: unknown) => void
  config: InternalAxiosRequestConfig
}> = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject, config }) => {
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
      resolve(api(config))
    } else {
      reject(error)
    }
  })
  failedQueue = []
}

// Response interceptor: handle 401 with silent refresh
api.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status = error.response?.status

    // Only attempt refresh on 401, and not for auth endpoints or already-retried requests
    const url = originalRequest?.url || ''
    const isAuthEndpoint = url.includes('/auth/login') ||
      url.includes('/auth/refresh') ||
      url.includes('/auth/register')

    if (status === 401 && !isAuthEndpoint && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue this request until the refresh completes
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject, config: originalRequest })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshResponse = await axios.post(
          `${api.defaults.baseURL}/auth/refresh`,
          {},
          { withCredentials: true }
        )

        const newToken = refreshResponse.data.token
        localStorage.setItem('token', newToken)

        // Dispatch event so Redux stays in sync
        window.dispatchEvent(new CustomEvent('token-refreshed', { detail: { token: newToken } }))

        originalRequest.headers.Authorization = `Bearer ${newToken}`
        processQueue(null, newToken)

        return api(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError, null)
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // For 403 or non-recoverable 401 (auth endpoints), redirect to login
    if (status === 403 || (status === 401 && isAuthEndpoint)) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    return Promise.reject(error)
  }
)

export { api }
