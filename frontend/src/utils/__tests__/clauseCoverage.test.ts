import { describe, it, expect } from 'vitest'
import { buildSegments, describeClause, parseLocator, percentLabel } from '../clauseCoverage'
import type { ClauseCoverageClause } from '../../types'

// PAT-232 — turning ELM locators into coloured runs of the CQL text.

const clause = (partial: Partial<ClauseCoverageClause> & Pick<ClauseCoverageClause, 'localId' | 'locator'>): ClauseCoverageClause => ({
  type: 'Literal',
  hits: 1,
  ...partial,
})

describe('parseLocator', () => {
  it('reads a range and a single-character locator', () => {
    expect(parseLocator('3:5-3:12')).toEqual({ startLine: 3, startCol: 5, endLine: 3, endCol: 12 })
    expect(parseLocator('7:2')).toEqual({ startLine: 7, startCol: 2, endLine: 7, endCol: 2 })
    expect(parseLocator(' 1:1-2:4 ')).toEqual({ startLine: 1, startCol: 1, endLine: 2, endCol: 4 })
  })

  it('rejects anything that is not line:col', () => {
    expect(parseLocator('')).toBeNull()
    expect(parseLocator('abc')).toBeNull()
    expect(parseLocator('3:5-')).toBeNull()
  })
})

describe('buildSegments', () => {
  const cql = 'define "A":\n  40 > 18\n'
  // line 2: cols 3-4 "40", 3-9 "40 > 18", 8-9 "18"

  it('gives every character the state of the innermost clause, neutral text stays plain', () => {
    const segments = buildSegments(cql, [
      clause({ localId: '1', locator: '2:3-2:9', type: 'Greater', value: 'true' }),
      clause({ localId: '2', locator: '2:3-2:4', value: '40' }),
      clause({ localId: '3', locator: '2:8-2:9', hits: 0 }),
    ])

    expect(segments.map((s) => [s.text, s.state, s.clause?.localId])).toEqual([
      ['define "A":\n  ', 'none', undefined],
      ['40', 'covered', '2'],
      [' > ', 'covered', '1'],
      ['18', 'uncovered', '3'],
      ['\n', 'none', undefined],
    ])
  })

  it('does not depend on the order the clauses arrive in', () => {
    const inner = clause({ localId: '2', locator: '2:3-2:4' })
    const outer = clause({ localId: '1', locator: '2:3-2:9', type: 'Greater' })
    const a = buildSegments(cql, [inner, outer]).map((s) => s.clause?.localId)
    const b = buildSegments(cql, [outer, inner]).map((s) => s.clause?.localId)
    expect(a).toEqual(b)
  })

  it('ignores clauses whose locator is unparsable or points past the text', () => {
    const segments = buildSegments(cql, [
      clause({ localId: 'x', locator: 'nope' }),
      clause({ localId: 'y', locator: '9:1-9:4' }),
    ])
    expect(segments).toEqual([{ text: cql, state: 'none', clause: undefined }])
  })

  it('handles CRLF text by counting only line feeds', () => {
    const crlf = 'define "A":\r\n  true\r\n'
    const segments = buildSegments(crlf, [clause({ localId: '1', locator: '2:3-2:6', value: 'true' })])
    expect(segments.map((s) => [s.text, s.state])).toEqual([
      ['define "A":\r\n  ', 'none'],
      ['true', 'covered'],
      ['\r\n', 'none'],
    ])
  })

  it('returns nothing for empty text', () => {
    expect(buildSegments('', [clause({ localId: '1', locator: '1:1' })])).toEqual([])
  })
})

describe('describeClause / percentLabel', () => {
  it('describes a covered clause with its value and repeat count, an uncovered one by type only', () => {
    expect(describeClause(clause({ localId: '1', locator: '1:1', type: 'Retrieve', value: '[FHIR.Condition/c1]', hits: 1 })))
      .toBe('Retrieve → [FHIR.Condition/c1]')
    expect(describeClause(clause({ localId: '1', locator: '1:1', type: 'Greater', value: 'true', hits: 4 })))
      .toBe('Greater → true (4×)')
    expect(describeClause(clause({ localId: '1', locator: '1:1', type: 'Literal', hits: 2 })))
      .toBe('Literal → null (2×)')
    expect(describeClause(clause({ localId: '1', locator: '1:1', type: 'Literal', hits: 0, value: 'stale' })))
      .toBe('Literal')
  })

  it('formats the percentage with one decimal and the ratio', () => {
    expect(percentLabel({ percent: 66.7, coveredClauses: 2, totalClauses: 3 })).toBe('66.7% (2/3)')
    expect(percentLabel({ percent: 100, coveredClauses: 5, totalClauses: 5 })).toBe('100.0% (5/5)')
  })
})
