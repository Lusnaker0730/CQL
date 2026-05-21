/**
 * FHIR profile types that all share the Quantity shape — used by ElementField
 * and getDefaultValue to dispatch to QuantityField regardless of the specific profile.
 */
export const QUANTITY_TYPES: ReadonlySet<string> = new Set([
  'Quantity',
  'SimpleQuantity',
  'Age',
  'Duration',
  'Distance',
  'Count',
])

export function isQuantityType(type: string): boolean {
  return QUANTITY_TYPES.has(type)
}
