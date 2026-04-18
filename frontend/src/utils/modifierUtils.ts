import type { Modifier } from '../types/authoring'

/**
 * Returns the list of required field names that are missing for the given modifier.
 * Works with the full `Modifier` type as well as the narrower modifier-like objects
 * used in the artifact expression tree.
 */
export function getModifierMissingFields(mod: Modifier): string[] {
  const tpl = mod.cqlTemplate || ''
  const vals = mod.values

  if (tpl === 'LookBackModifier' || mod.id.startsWith('LookBack')) {
    const missing: string[] = []
    if (!vals?.value) missing.push('value')
    if (!vals?.unit) missing.push('unit')
    return missing
  }
  if (tpl === 'ValueComparisonNumber' || tpl === 'ValueComparisonObservation') {
    const missing: string[] = []
    if (!vals?.minOperator) missing.push('operator')
    if (!vals?.minValue && vals?.minValue !== 0) missing.push('value')
    return missing
  }
  if (tpl === 'ConvertUnits' || tpl === 'WithUnit') {
    return vals?.unit ? [] : ['unit']
  }
  if (['EqualsString', 'StartsWithString', 'EndsWithString'].includes(tpl)) {
    return vals?.value ? [] : ['value']
  }
  if (tpl === 'Qualifier') {
    const q = (vals?.qualifier as string) ?? 'value set'
    return q === 'value set' ? (vals?.valueSet ? [] : ['valueSet']) : (vals?.code ? [] : ['code'])
  }
  if (tpl === 'BeforeInterval' || tpl === 'AfterInterval') {
    return vals?.value ? [] : ['value']
  }
  // Modifiers without required values (BaseModifier, CheckExistence, etc.) are always complete
  return []
}

/**
 * Check if a modifier has all required fields filled.
 * Returns true if the modifier is "complete" and its returnType is reliable.
 */
export function isModifierComplete(mod: Modifier): boolean {
  return getModifierMissingFields(mod).length === 0
}

/**
 * Compute the effective return type after applying all modifiers.
 * Follows AHRQ pattern: iterates backward from the last modifier,
 * returning the returnType of the first COMPLETE modifier found.
 * If no modifiers or all are incomplete, returns the base returnType.
 */
export function getEffectiveReturnType(baseReturnType: string, modifiers?: Modifier[]): string {
  if (!modifiers || modifiers.length === 0) return baseReturnType

  for (let i = modifiers.length - 1; i >= 0; i--) {
    if (isModifierComplete(modifiers[i])) {
      return modifiers[i].returnType
    }
  }
  return baseReturnType
}

/**
 * Compute the type exposed at a specific insertion position in the modifier chain.
 * `insertAtIndex = 0` means "before all existing modifiers" → baseReturnType.
 * `insertAtIndex = N` means "after modifier[N-1]" → effective type of modifiers[0..N-1].
 * `insertAtIndex >= modifiers.length` means "at end" → getEffectiveReturnType of the whole chain.
 *
 * Used by the UI to determine which modifiers are applicable at that insertion slot.
 */
export function getReturnTypeAtPosition(
  baseReturnType: string,
  modifiers: Modifier[] | undefined,
  insertAtIndex: number,
): string {
  if (!modifiers || modifiers.length === 0) return baseReturnType
  if (insertAtIndex <= 0) return baseReturnType
  const slice = modifiers.slice(0, insertAtIndex)
  return getEffectiveReturnType(baseReturnType, slice)
}
