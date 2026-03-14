import type { Modifier } from '../types/authoring'

/**
 * Check if a modifier has all required fields filled.
 * Returns true if the modifier is "complete" and its returnType is reliable.
 */
export function isModifierComplete(mod: Modifier): boolean {
  const tpl = mod.cqlTemplate || ''
  const vals = mod.values

  if (tpl === 'LookBackModifier' || mod.id.startsWith('LookBack')) {
    return !!(vals?.value && vals?.unit)
  }
  if (tpl === 'ValueComparisonNumber' || tpl === 'ValueComparisonObservation') {
    return !!(vals?.minOperator && (vals?.minValue || vals?.minValue === 0))
  }
  if (tpl === 'ConvertUnits' || tpl === 'WithUnit') {
    return !!vals?.unit
  }
  if (['EqualsString', 'StartsWithString', 'EndsWithString'].includes(tpl)) {
    return !!vals?.value
  }
  if (tpl === 'Qualifier') {
    const q = (vals?.qualifier as string) ?? 'value set'
    return q === 'value set' ? !!vals?.valueSet : !!vals?.code
  }
  if (tpl === 'BeforeInterval' || tpl === 'AfterInterval') {
    return !!vals?.value
  }
  // Modifiers without values (BaseModifier, CheckExistence, etc.) are always complete
  return true
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
