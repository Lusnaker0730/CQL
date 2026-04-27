/**
 * CQL string escaping utilities.
 *
 * Escapes backslashes and single quotes for safe embedding
 * inside CQL string literals ('...').
 */

export function escapeCqlString(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

/**
 * Escape a value for safe embedding inside a CQL delimited identifier ("...").
 * CQL identifier delimiters require escaping of `\` and `"`.
 */
export function escapeCqlIdentifier(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

export interface FieldState {
  value: string
  mode: 'literal' | 'expression'
}

export function formatFieldValue(field: FieldState): string {
  if (!field.value) return ''
  return field.mode === 'literal'
    ? `'${escapeCqlString(field.value)}'`
    : field.value
}
