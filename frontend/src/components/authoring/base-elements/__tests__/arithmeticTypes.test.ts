import { describe, it, expect } from 'vitest'
import {
  inferOperandType,
  allowedOperators,
  allowedUnaryFunctions,
} from '../arithmeticTypes'

describe('inferOperandType (PAT-164)', () => {
  it('literal mode -> decimal', () => {
    expect(inferOperandType({ mode: 'literal' }, [])).toBe('decimal')
  })

  it('quantity mode -> quantity', () => {
    expect(inferOperandType({ mode: 'quantity' }, [])).toBe('quantity')
  })

  it('element mode with known integer returnType -> integer', () => {
    expect(inferOperandType(
      { mode: 'element', operand_id: 'be-1' },
      [{ uniqueId: 'be-1', returnType: 'system_integer' }],
    )).toBe('integer')
  })

  it('element mode with known decimal returnType -> decimal', () => {
    expect(inferOperandType(
      { mode: 'element', operand_id: 'be-1' },
      [{ uniqueId: 'be-1', returnType: 'system_decimal' }],
    )).toBe('decimal')
  })

  it('element mode with system_string -> string', () => {
    expect(inferOperandType(
      { mode: 'element', operand_id: 'be-1' },
      [{ uniqueId: 'be-1', returnType: 'system_string' }],
    )).toBe('string')
  })

  it('element mode with unknown returnType -> unknown', () => {
    expect(inferOperandType(
      { mode: 'element', operand_id: 'be-1' },
      [{ uniqueId: 'be-1', returnType: 'list_of_observations' }],
    )).toBe('unknown')
  })

  it('element mode with missing ref -> unknown', () => {
    expect(inferOperandType(
      { mode: 'element', operand_id: 'be-missing' },
      [{ uniqueId: 'be-other', returnType: 'system_integer' }],
    )).toBe('unknown')
  })

  it('element mode with empty operand_id -> unknown', () => {
    expect(inferOperandType(
      { mode: 'element' },
      [],
    )).toBe('unknown')
  })
})

describe('allowedOperators (PAT-164)', () => {
  it('two integer operands -> all 7 operators', () => {
    expect(allowedOperators(['integer', 'integer'])).toEqual([
      '+', '-', '*', '/', 'mod', 'div', '^',
    ])
  })

  it('two decimal operands -> 5 operators (no mod/div)', () => {
    const ops = allowedOperators(['decimal', 'decimal'])
    expect(ops).toContain('+')
    expect(ops).toContain('-')
    expect(ops).toContain('*')
    expect(ops).toContain('/')
    expect(ops).toContain('^')
    expect(ops).not.toContain('mod')
    expect(ops).not.toContain('div')
  })

  it('two string operands -> only + (concat)', () => {
    expect(allowedOperators(['string', 'string'])).toEqual(['+'])
  })

  it('two quantity operands -> 4 operators (no mod/div/^)', () => {
    expect(allowedOperators(['quantity', 'quantity'])).toEqual([
      '+', '-', '*', '/',
    ])
  })

  it('mixed integer + decimal -> intersection (5 operators, no mod/div)', () => {
    const ops = allowedOperators(['integer', 'decimal'])
    expect(ops).not.toContain('mod')
    expect(ops).not.toContain('div')
    expect(ops).toContain('+')
    expect(ops).toContain('^')
  })

  it('any unknown -> permissive (all 7 operators)', () => {
    expect(allowedOperators(['unknown', 'decimal'])).toEqual([
      '+', '-', '*', '/', 'mod', 'div', '^',
    ])
  })

  it('N-ary 3 operands all decimal -> still 5 operators', () => {
    const ops = allowedOperators(['decimal', 'decimal', 'decimal'])
    expect(ops).toContain('+')
    expect(ops).not.toContain('mod')
  })
})

describe('allowedUnaryFunctions (PAT-164)', () => {
  it('decimal operand -> all 6 functions', () => {
    expect(allowedUnaryFunctions('decimal')).toEqual([
      'Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate',
    ])
  })

  it('integer operand -> all 6 functions', () => {
    expect(allowedUnaryFunctions('integer')).toEqual([
      'Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate',
    ])
  })

  it('quantity operand -> only Abs + Negate', () => {
    expect(allowedUnaryFunctions('quantity')).toEqual(['Abs', 'Negate'])
  })

  it('string operand -> no functions', () => {
    expect(allowedUnaryFunctions('string')).toEqual([])
  })

  it('unknown operand -> permissive (all 6 functions)', () => {
    expect(allowedUnaryFunctions('unknown')).toEqual([
      'Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate',
    ])
  })

  it('date operand -> no functions (numeric only)', () => {
    expect(allowedUnaryFunctions('date')).toEqual([])
  })
})
