/**
 * FHIR `value[x]` (choice type) helpers.
 *
 * FHIR flattens choice elements into typed sibling keys: `value` + `Quantity`
 * becomes `valueQuantity`, `value` + `CodeableConcept` becomes `valueCodeableConcept`,
 * etc. These helpers are the single source of truth for that name <-> components
 * mapping so callers (ResourceForm, ChoiceTypeField) stay consistent.
 */

/** Build the flattened FHIR field name for a choice variant (e.g. "value", "Quantity" → "valueQuantity"). */
export function buildChoiceFieldName(baseName: string, type: string): string {
  if (!type) return baseName
  return baseName + type.charAt(0).toUpperCase() + type.slice(1)
}

/**
 * Among `choiceTypes`, return which one is currently materialized in `data`
 * (e.g. data has `valueQuantity` set → returns "Quantity"). Returns undefined
 * when none of the variants is present.
 */
export function detectChoiceType(
  baseName: string,
  choiceTypes: readonly string[],
  data: Record<string, unknown>,
): string | undefined {
  for (const ct of choiceTypes) {
    if (buildChoiceFieldName(baseName, ct) in data) return ct
  }
  return undefined
}

/**
 * Returns the set of all flattened keys that *could* hold a value for the
 * given choice element, e.g. for "value" + ["Quantity","string"] returns
 * {"valueQuantity","valueString"}. Used by callers that need to wipe stale
 * variants when the user switches type.
 */
export function listChoiceFieldNames(
  baseName: string,
  choiceTypes: readonly string[],
): Set<string> {
  const out = new Set<string>()
  for (const ct of choiceTypes) {
    out.add(buildChoiceFieldName(baseName, ct))
  }
  return out
}
