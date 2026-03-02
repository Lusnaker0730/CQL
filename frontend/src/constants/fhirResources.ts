/**
 * Shared FHIR resource types used by CQL builder components.
 */
export const FHIR_RESOURCE_TYPES = [
  'Observation',
  'Condition',
  'Procedure',
  'MedicationRequest',
  'MedicationStatement',
  'Encounter',
  'Immunization',
  'AllergyIntolerance',
  'Device',
  'ServiceRequest',
] as const

export type FhirResourceType = (typeof FHIR_RESOURCE_TYPES)[number]
