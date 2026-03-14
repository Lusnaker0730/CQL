import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { extractApiError } from '../errorUtils'

function createAxiosError(
  responseData?: Record<string, unknown>,
  message = 'Request failed',
): AxiosError {
  const headers = new AxiosHeaders()
  const error = new AxiosError(message, 'ERR_BAD_REQUEST', undefined, undefined, responseData ? {
    data: responseData,
    status: 400,
    statusText: 'Bad Request',
    headers,
    config: { headers },
  } : undefined)
  return error
}

describe('extractApiError', () => {
  // 1. AxiosError with response.data.message (GlobalExceptionHandler format)
  it('should extract message from GlobalExceptionHandler response', () => {
    const error = createAxiosError({
      timestamp: '2024-01-01T00:00:00',
      status: 404,
      error: 'Not Found',
      message: 'Measure not found: 42',
    })

    expect(extractApiError(error)).toBe('Measure not found: 42')
  })

  // 2. AxiosError with response.data.error (legacy format)
  it('should extract error string from legacy response format', () => {
    const error = createAxiosError({
      error: 'Username already exists',
    })

    expect(extractApiError(error)).toBe('Username already exists')
  })

  // 3. AxiosError with no response (network error)
  it('should return AxiosError message for network errors', () => {
    const error = new AxiosError('Network Error', 'ERR_NETWORK')

    expect(extractApiError(error)).toBe('Network Error')
  })

  // 4. Standard Error object
  it('should return message from standard Error object', () => {
    const error = new Error('Something went wrong')

    expect(extractApiError(error)).toBe('Something went wrong')
  })

  // 5. Unknown types → fallback
  it('should return fallback for string input', () => {
    expect(extractApiError('some string')).toBe('An unknown error occurred')
  })

  it('should return fallback for null input', () => {
    expect(extractApiError(null)).toBe('An unknown error occurred')
  })

  // 6. AxiosError with empty data
  it('should return AxiosError message when response data has no message or error', () => {
    const error = createAxiosError({})

    expect(extractApiError(error)).toBe('Request failed')
  })

  // 7. Real-world: GlobalExceptionHandler 400 with details
  it('should extract message from 400 response with details array', () => {
    const error = createAxiosError({
      timestamp: '2024-01-01T00:00:00',
      status: 400,
      error: 'Validation Error',
      message: 'Validation failed',
      details: ["Field 'name' is required"],
    })

    expect(extractApiError(error)).toBe('Validation failed')
  })
})
