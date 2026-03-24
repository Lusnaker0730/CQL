/** Shared types for TW Core patient data generation config */

export interface CodingItem {
  code: string
  display: string
  system: string
}

export type ConditionItem = CodingItem

export interface ObservationItem extends CodingItem {
  unit: string
  ucum_code: string
  min_val: number
  max_val: number
}

export interface MedicationItem extends CodingItem {
  atc: string
  category: string
  dosage_form: string
  strength: string
}

export interface AllergyItem extends CodingItem {
  type: 'medication' | 'food' | 'environment'
  criticality: 'high' | 'low'
}

export interface CategoryGroup<T> {
  name: string
  conditions?: T[]
  observations?: T[]
  medications?: T[]
  allergies?: T[]
}

export interface ConfigFile<T> {
  description: string
  version: string
  last_updated: string
  categories: Record<string, CategoryGroup<T>>
}

export interface Scenario {
  id: string
  name: string
  icon: string
  description: string
  conditions: string[]
  condition_names: string[]
  observations: string[]
  observation_names: string[]
  medications: string[]
  medication_names: string[]
  num_encounters: number
  recommended_patient_count: number
}

export interface ScenariosFile {
  scenarios: Scenario[]
}

/** Generation request parameters */
export interface BatchGenerationConfig {
  numPatients: number
  numConditions: number
  numObservations: number
  numMedications: number
  numEncounters: number
  numAllergies: number
  dateFrom?: string
  dateTo?: string
}

export interface CustomGenerationConfig {
  selectedConditions: string[]
  selectedObservations: string[]
  selectedMedications: string[]
  selectedAllergies: string[]
  numEncounters: number
  numPatients?: number
  dateFrom?: string
  dateTo?: string
}

/** Generated FHIR resource types */
export interface FhirResource {
  resourceType: string
  id: string
  [key: string]: unknown
}

export interface GeneratedPatientData {
  patient: FhirResource
  encounters: FhirResource[]
  conditions: FhirResource[]
  observations: FhirResource[]
  medications: FhirResource[]
  medication_requests: FhirResource[]
  allergies: FhirResource[]
}
