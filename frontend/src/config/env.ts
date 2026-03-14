/**
 * Environment configuration — reads from Vite env vars with sensible defaults.
 * Add new VITE_* variables to .env.example when adding entries here.
 */

export const DEFAULT_FHIR_SERVER_URL =
  import.meta.env.VITE_DEFAULT_FHIR_SERVER_URL || 'http://hapi-fhir:8080/fhir'
