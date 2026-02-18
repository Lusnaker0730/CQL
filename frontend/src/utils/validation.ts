import { isValidHookType } from '../constants/cdsHooks'

export function validateRequired(value: string, fieldName: string): string | null {
  if (!value.trim()) return `${fieldName} is required`
  return null
}

export function validateUsername(value: string): string | null {
  if (!value.trim()) return 'Username is required'
  if (value.length < 3) return 'Username must be at least 3 characters'
  if (value.length > 50) return 'Username must be at most 50 characters'
  if (!/^[a-zA-Z0-9_-]+$/.test(value)) return 'Username can only contain letters, numbers, hyphens, and underscores'
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) return 'Password is required'
  if (value.length < 8) return 'Password must be at least 8 characters'
  if (!/[A-Z]/.test(value)) return 'Password must contain at least one uppercase letter'
  if (!/[a-z]/.test(value)) return 'Password must contain at least one lowercase letter'
  if (!/[0-9]/.test(value)) return 'Password must contain at least one number'
  return null
}

export function isValidPassword(password: string): boolean {
  if (password.length < 8) return false
  if (!/[A-Z]/.test(password)) return false
  if (!/[a-z]/.test(password)) return false
  if (!/[0-9]/.test(password)) return false
  return true
}

export function validateFhirUrl(value: string): string | null {
  if (!value.trim()) return 'FHIR server URL is required'
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol)) {
      return 'URL must start with http:// or https://'
    }
  } catch {
    return 'Invalid URL format'
  }
  return null
}

export function validateJson(value: string): string | null {
  if (!value.trim()) return null
  try {
    JSON.parse(value)
  } catch {
    return 'Invalid JSON format'
  }
  return null
}

export function validateDateRange(start: string, end: string): string | null {
  if (!start || !end) return null
  if (new Date(start) > new Date(end)) return 'Start date must be before end date'
  return null
}

export function generateId(): string {
  return crypto.randomUUID()
}

export function safeParseJson<T = unknown>(json: string | null, fallback: T): T {
  if (!json) return fallback
  try {
    return JSON.parse(json) as T
  } catch {
    return fallback
  }
}

export function getStoredUsername(): string {
  return safeParseJson<{ username?: string }>(localStorage.getItem('user'), {}).username || 'anonymous'
}

export function validateHookType(value: string): string | null {
  if (!value) return 'Hook type is required'
  if (!isValidHookType(value)) return 'Invalid hook type'
  return null
}
