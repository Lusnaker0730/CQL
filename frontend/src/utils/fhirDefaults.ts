import { QUANTITY_TYPES } from '../constants/fhirTypes'
import type { ElementMetadata } from '../types'

/**
 * Returns a sensible empty value to seed a newly added FHIR element of `type`.
 * Used when the user clicks "Add Attribute" or adds an array item — the form
 * needs a non-undefined starting shape so the corresponding field component
 * has something to render.
 */
export function getDefaultValue(element: ElementMetadata): unknown {
  const type = element.type
  if (type === 'boolean') return false
  if (type === 'CodeableConcept') return { coding: [{ system: '', code: '', display: '' }] }
  if (type === 'Coding') return { system: '', code: '', display: '' }
  if (type === 'Period') return { start: '', end: '' }
  if (QUANTITY_TYPES.has(type)) return { value: 0, unit: '' }
  if (type === 'Reference') return { reference: '' }
  if (type === 'Identifier') return { system: '', value: '' }
  if (type === 'HumanName') return { family: '', given: [] }
  if (type === 'ContactPoint') return { value: '' }
  if (type === 'Address') return { line: [], city: '' }
  if ((element.children?.length ?? 0) > 0) return {}
  return ''
}
