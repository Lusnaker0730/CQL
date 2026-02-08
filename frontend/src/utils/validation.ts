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
  if (value.length < 6) return 'Password must be at least 6 characters'
  return null
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

export function validateHookType(value: string): string | null {
  const valid = [
    'patient-view',
    'order-select',
    'order-sign',
    'appointment-book',
    'encounter-start',
    'encounter-discharge',
  ]
  if (!value) return 'Hook type is required'
  if (!valid.includes(value)) return 'Invalid hook type'
  return null
}
