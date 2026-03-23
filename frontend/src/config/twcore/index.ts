import conditionsData from './conditions.json'
import observationsData from './observations.json'
import medicationsData from './medications.json'
import allergiesData from './allergies.json'
import scenariosData from './scenarios.json'
import type {
  ConfigFile,
  ConditionItem,
  ObservationItem,
  MedicationItem,
  AllergyItem,
  ScenariosFile,
} from './types'

export const conditionsConfig = conditionsData as unknown as ConfigFile<ConditionItem>
export const observationsConfig = observationsData as unknown as ConfigFile<ObservationItem>
export const medicationsConfig = medicationsData as unknown as ConfigFile<MedicationItem>
export const allergiesConfig = allergiesData as unknown as ConfigFile<AllergyItem>
export const scenariosConfig = scenariosData as ScenariosFile

/** Flatten a categorized config into a flat array of items */
function flattenCategories<T>(
  config: ConfigFile<T>,
  itemKey: 'conditions' | 'observations' | 'medications' | 'allergies',
): T[] {
  return Object.values(config.categories).flatMap(
    (cat) => (cat[itemKey] as T[] | undefined) ?? [],
  )
}

export const allConditions = flattenCategories<ConditionItem>(conditionsConfig, 'conditions')
export const allObservations = flattenCategories<ObservationItem>(observationsConfig, 'observations')
export const allMedications = flattenCategories<MedicationItem>(medicationsConfig, 'medications')
export const allAllergies = flattenCategories<AllergyItem>(allergiesConfig, 'allergies')

export type { ConditionItem, ObservationItem, MedicationItem, AllergyItem } from './types'
export type {
  Scenario,
  BatchGenerationConfig,
  CustomGenerationConfig,
  GeneratedPatientData,
  FhirResource,
} from './types'
