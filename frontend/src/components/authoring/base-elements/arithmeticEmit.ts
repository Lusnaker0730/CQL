// PAT-163: N-ary arithmetic CQL emission with explicit precedence parens.
// TypeScript counterpart of backend ExpressionCqlEngine.emitNaryArithmeticCql
// — must produce byte-for-byte identical output so the UI preview matches
// what the server actually emits.
//
// Lives in its own .ts file (not .tsx) so it can be tested without React
// renderer setup and so eslint react-refresh/only-export-components doesn't
// complain about mixing utility + component exports.

import { NUMERIC_LITERAL_RE, UCUM_UNIT_RE, type OperandMode } from './operandValidation'

export const ARITHMETIC_OPERATORS = ['+', '-', '*', '/', 'mod', 'div', '^'] as const
export type ArithmeticOperator = typeof ARITHMETIC_OPERATORS[number]
export const NARY_MIN_OPERANDS = 2
export const NARY_MAX_OPERANDS = 10

export interface NaryOperand {
  mode: OperandMode
  operand_id?: string
  operand_literal?: string
  operand_literal_value?: string
  operand_literal_unit?: string
}

interface AvailableElement {
  uniqueId: string
  name: string
}

/**
 * Resolve a single operand to its CQL string. Mirrors the backend
 * resolveNaryOperand exactly so preview UI matches server output.
 *
 * Returns empty string when the operand fails validation (caller treats
 * empty as "expression unrenderable" rather than emitting bad CQL).
 */
export function resolveNaryOperand(
  operand: NaryOperand,
  availableElements: AvailableElement[],
  escapeIdentifier: (name: string) => string,
): string {
  const mode = operand.mode ?? 'element'
  if (mode === 'literal') {
    const literal = (operand.operand_literal ?? '').trim()
    if (!literal) return ''
    if (!NUMERIC_LITERAL_RE.test(literal)) return ''
    return literal
  }
  if (mode === 'quantity') {
    const value = (operand.operand_literal_value ?? '').trim()
    const unit = (operand.operand_literal_unit ?? '').trim()
    if (!value || !unit) return ''
    if (!NUMERIC_LITERAL_RE.test(value)) return ''
    if (!UCUM_UNIT_RE.test(unit)) return ''
    return `${value} '${unit}'`
  }
  // Element reference mode
  const refId = operand.operand_id ?? ''
  if (!refId) return ''
  const found = availableElements.find((e) => e.uniqueId === refId)
  return found ? `"${escapeIdentifier(found.name)}"` : ''
}

function precedence(op: string): number {
  switch (op) {
    case '^':
      return 3
    case '*':
    case '/':
    case 'mod':
    case 'div':
      return 2
    case '+':
    case '-':
      return 1
    default:
      return 1
  }
}

/**
 * Group operand CQL strings by operator precedence, inserting parens around
 * any tighter-binding sub-expression. Mirrors backend groupByPrecedence.
 *
 * Splits on the rightmost lowest-precedence operator (left-associative).
 * Wraps any non-trivial sub-group in parens — readers see precedence
 * structure without doing the math in their head.
 */
function groupByPrecedence(operandCqls: string[], operators: string[]): string {
  if (operators.length === 0) return operandCqls[0]
  // Find rightmost lowest-precedence operator
  let minPrec = Infinity
  let splitAt = -1
  for (let i = operators.length - 1; i >= 0; i--) {
    const p = precedence(operators[i])
    if (p < minPrec) {
      minPrec = p
      splitAt = i
    }
  }
  const leftOperands = operandCqls.slice(0, splitAt + 1)
  const leftOperators = operators.slice(0, splitAt)
  const rightOperands = operandCqls.slice(splitAt + 1)
  const rightOperators = operators.slice(splitAt + 1)
  let left = groupByPrecedence(leftOperands, leftOperators)
  let right = groupByPrecedence(rightOperands, rightOperators)
  if (leftOperators.length > 0) left = `(${left})`
  if (rightOperators.length > 0) right = `(${right})`
  return `${left} ${operators[splitAt]} ${right}`
}

/**
 * Emit N-ary arithmetic CQL with explicit precedence parens.
 *
 * Returns empty string when any operand/operator fails validation or the
 * shape is malformed (out of [2, 10] operand range, operator count mismatch).
 * Empty output signals "no renderable preview" to the caller.
 */
export function emitNaryArithmeticCql(
  operands: NaryOperand[],
  operators: string[],
  availableElements: AvailableElement[],
  escapeIdentifier: (name: string) => string,
): string {
  if (!Array.isArray(operands) || !Array.isArray(operators)) return ''
  if (operands.length < NARY_MIN_OPERANDS || operands.length > NARY_MAX_OPERANDS) return ''
  if (operators.length !== operands.length - 1) return ''

  const operandCqls: string[] = []
  for (const op of operands) {
    const cql = resolveNaryOperand(op, availableElements, escapeIdentifier)
    if (!cql) return ''
    operandCqls.push(cql)
  }
  const safeOps = operators.map((o) =>
    (ARITHMETIC_OPERATORS as readonly string[]).includes(o) ? o : '+',
  )
  return groupByPrecedence(operandCqls, safeOps)
}

/**
 * Convert a legacy PAT-161 2-ary fields[] shape (left_x / right_x / operator
 * scalars) to the new N-ary {operands, operators} shape. Used by the UI to
 * read artifacts that escaped the V56 Flyway migration so the editor can
 * always work with the new representation.
 */
export function convertLegacy2aryToNary(
  legacyValues: Record<string, string>,
): { operands: NaryOperand[]; operators: string[] } {
  const buildOperand = (side: 'left' | 'right'): NaryOperand => ({
    mode: (legacyValues[`${side}_mode`] as OperandMode) || 'element',
    operand_id: legacyValues[`${side}_operand_id`] ?? '',
    operand_literal: legacyValues[`${side}_literal`] ?? '',
    operand_literal_value: legacyValues[`${side}_literal_value`] ?? '',
    operand_literal_unit: legacyValues[`${side}_literal_unit`] ?? '',
  })
  return {
    operands: [buildOperand('left'), buildOperand('right')],
    operators: [legacyValues.operator || '+'],
  }
}
