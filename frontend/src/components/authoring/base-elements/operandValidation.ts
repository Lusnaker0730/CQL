// PAT-161/162: shared validation utilities for arithmetic operands.
//
// Kept in a separate file from OperandField.tsx so that file only exports a
// component (required by eslint's react-refresh/only-export-components rule
// — mixing component + constant exports breaks HMR Fast Refresh).

// NUMERIC must be a plain CQL numeric literal; UCUM unit must use UCUM-safe
// chars only — no single-quote (would close the CQL Quantity literal early),
// no whitespace/backslash (would let arbitrary CQL fragments smuggle in).
// Mirrors the backend ARITHMETIC_NUMERIC_PATTERN / ARITHMETIC_UCUM_UNIT_PATTERN.
export const NUMERIC_LITERAL_RE = /^-?\d+(\.\d+)?$/
export const UCUM_UNIT_RE = /^[A-Za-z0-9./*+\-()[\]{}%_]{1,32}$/

export type OperandMode = 'element' | 'literal' | 'quantity'

/**
 * Convert a Quantity operand's separate value + unit to a CQL string literal
 * `<value> '<unit>'`. Returns empty string if either side fails validation —
 * callers treat empty as "preview not renderable" rather than emitting
 * malformed CQL.
 */
export function quantityToCql(value: string, unit: string): string {
  const v = value.trim()
  const u = unit.trim()
  if (!NUMERIC_LITERAL_RE.test(v) || !UCUM_UNIT_RE.test(u)) return ''
  return `${v} '${u}'`
}
