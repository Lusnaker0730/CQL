/**
 * Generate a short CQL-library alias from a library name.
 *
 * <p>Extracts an uppercase-initial abbreviation from PascalCase /
 * camelCase / snake_case / kebab-case names — e.g. `DiabetesCohort` →
 * `DC`, `shared_logic` → `SL`. Falls back to an uppercase prefix of the
 * first three characters when the input yields no usable word boundaries.
 *
 * <p>Lives in its own module (not inside `LibraryDefinitionPicker.tsx`)
 * because React Fast Refresh complains when a component module also
 * exports non-component helpers.
 */
export function generateAlias(name: string): string {
  // Split on uppercase boundaries: "DiabetesCohort" -> ["Diabetes", "Cohort"]
  const words = name.replace(/([a-z])([A-Z])/g, '$1 $2').split(/[\s_-]+/)
  const alias = words
    .map((w) => w.charAt(0).toUpperCase())
    .join('')
  return alias || name.substring(0, 3).toUpperCase()
}
