/**
 * Runtime guards for FHIR-shaped values coming from `unknown` (loaded JSON,
 * cross-component props). Used by field components to avoid blind `as` casts.
 */

export function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function asObject(value: unknown): Record<string, unknown> {
  return isPlainObject(value) ? value : {}
}

export function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}
