// PAT-164: type inference for arithmetic operands + operator/function
// compatibility matrix. Drives the UI filtering of operator dropdowns
// (ArithmeticElement, binary N-ary) and function dropdowns
// (ArithmeticUnaryElement). Mirrors CQL 1.5 operator-type spec.
//
// Lives in a separate .ts file (not .tsx) so non-component exports don't
// trip react-refresh/only-export-components (see PR #507 lint fix).

import type { NaryOperand } from './arithmeticEmit'
import { ARITHMETIC_OPERATORS, type ArithmeticOperator } from './arithmeticEmit'

export type ArithmeticType = 'integer' | 'decimal' | 'quantity' | 'string' | 'date' | 'unknown'

// Map a base-element returnType string (as stored in the artifact JSON)
// to our coarser-grained ArithmeticType. Anything not in this table falls
// to 'unknown' which is the permissive default (don't over-filter).
const RETURN_TYPE_MAP: Record<string, ArithmeticType> = {
  system_integer: 'integer',
  system_decimal: 'decimal',
  system_quantity: 'quantity',
  system_string: 'string',
  system_date: 'date',
  system_datetime: 'date',
  integer: 'integer',
  decimal: 'decimal',
  quantity: 'quantity',
  string: 'string',
  date: 'date',
  datetime: 'date',
}

export interface OperandTypeContext {
  uniqueId: string
  returnType: string
}

/**
 * Infer the {@link ArithmeticType} of a single operand. Literal mode is
 * always treated as 'decimal' (CQL numeric literals are Decimal/Integer);
 * quantity mode is always 'quantity'; element-ref mode resolves through
 * the available-elements table; anything unresolved is 'unknown'.
 */
export function inferOperandType(
  operand: NaryOperand,
  availableElements: OperandTypeContext[],
): ArithmeticType {
  const mode = operand.mode ?? 'element'
  if (mode === 'literal') return 'decimal'
  if (mode === 'quantity') return 'quantity'
  // element ref
  const refId = operand.operand_id ?? ''
  if (!refId) return 'unknown'
  const ref = availableElements.find((e) => e.uniqueId === refId)
  if (!ref) return 'unknown'
  return RETURN_TYPE_MAP[ref.returnType?.toLowerCase()] ?? 'unknown'
}

// CQL 1.5 operator-type compatibility. {@code +} on strings is concat (per
// CQL spec §10.1); {@code mod}/{@code div} are integer-only. {@code ^} is
// numeric (Decimal/Integer; Quantity not generally supported as exponent).
const OPERATOR_ALLOWED_TYPES: Record<ArithmeticOperator, ArithmeticType[]> = {
  '+': ['integer', 'decimal', 'quantity', 'string'],
  '-': ['integer', 'decimal', 'quantity'],
  '*': ['integer', 'decimal', 'quantity'],
  '/': ['integer', 'decimal', 'quantity'],
  mod: ['integer'],
  div: ['integer'],
  '^': ['integer', 'decimal'],
}

/**
 * Return the subset of {@link ARITHMETIC_OPERATORS} that is valid for the
 * given operand-type tuple. An operator is allowed iff every operand type
 * is in its allow-list. When any operand is 'unknown' we fall back to the
 * full list (conservative — don't over-filter when types can't be
 * resolved, per PAT-164 risk-control choice).
 */
export function allowedOperators(operandTypes: ArithmeticType[]): ArithmeticOperator[] {
  if (operandTypes.some((t) => t === 'unknown')) return [...ARITHMETIC_OPERATORS]
  return ARITHMETIC_OPERATORS.filter((op) => {
    const allowed = OPERATOR_ALLOWED_TYPES[op]
    return operandTypes.every((t) => allowed.includes(t))
  })
}

// Unary function compatibility. Floor/Ceiling/Round/Truncate are
// Decimal/Integer only (Quantity not generally supported per CQL spec).
// Abs/Negate work on numeric + Quantity.
export const UNARY_FUNCTIONS = ['Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate'] as const
export type UnaryFunction = typeof UNARY_FUNCTIONS[number]

const FUNCTION_ALLOWED_TYPES: Record<UnaryFunction, ArithmeticType[]> = {
  Abs: ['integer', 'decimal', 'quantity'],
  Negate: ['integer', 'decimal', 'quantity'],
  Floor: ['integer', 'decimal'],
  Ceiling: ['integer', 'decimal'],
  Round: ['integer', 'decimal'],
  Truncate: ['integer', 'decimal'],
}

/**
 * Return the subset of unary functions valid for a given operand type.
 * 'unknown' is permissive (returns all functions); 'string' returns empty
 * (no unary numeric function applies to a String).
 */
export function allowedUnaryFunctions(operandType: ArithmeticType): UnaryFunction[] {
  if (operandType === 'unknown') return [...UNARY_FUNCTIONS]
  return UNARY_FUNCTIONS.filter((fn) => FUNCTION_ALLOWED_TYPES[fn].includes(operandType))
}

// PAT-164: Round precision validator — must be a non-negative integer.
// Empty string is allowed (means "round to nearest integer", emits 1-arg form).
export const ROUND_PRECISION_RE = /^\d+$/
