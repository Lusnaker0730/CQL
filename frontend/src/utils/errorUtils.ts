import { AxiosError } from 'axios'

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  details?: string[]
}

/**
 * Extracts a human-readable error message from an API error response.
 * Handles GlobalExceptionHandler format, legacy Map format, Axios errors,
 * and generic Error objects.
 */
export function extractApiError(error: unknown): string {
  // 1. Try AxiosError.response.data.message (GlobalExceptionHandler format)
  if (error instanceof AxiosError && error.response?.data) {
    const data = error.response.data as Record<string, unknown>
    if (typeof data.message === 'string') {
      return data.message
    }
    // 2. Try legacy Map format { error: "..." }
    if (typeof data.error === 'string') {
      return data.error
    }
  }

  // 3. Try AxiosError.message (network errors etc.)
  if (error instanceof AxiosError && error.message) {
    return error.message
  }

  // 4. Try Error.message
  if (error instanceof Error && error.message) {
    return error.message
  }

  // 5. Fallback
  return 'An unknown error occurred'
}

/**
 * Extracts the details array from a GlobalExceptionHandler error response.
 */
export function extractApiErrorDetails(error: unknown): string[] | undefined {
  if (error instanceof AxiosError && error.response?.data) {
    const data = error.response.data as Record<string, unknown>
    if (Array.isArray(data.details)) {
      return data.details.filter((d): d is string => typeof d === 'string')
    }
  }
  return undefined
}
