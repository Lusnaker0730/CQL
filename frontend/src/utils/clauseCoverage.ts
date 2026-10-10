import type { ClauseCoverage, ClauseCoverageClause } from '../types'

/** An ELM locator: `startLine:startCol-endLine:endCol` (1-based, end inclusive) or `line:col` for one character. */
export interface Locator {
  startLine: number
  startCol: number
  endLine: number
  endCol: number
}

export function parseLocator(locator: string): Locator | null {
  const m = /^(\d+):(\d+)(?:-(\d+):(\d+))?$/.exec(locator.trim())
  if (!m) return null
  const startLine = Number(m[1])
  const startCol = Number(m[2])
  return {
    startLine,
    startCol,
    endLine: m[3] ? Number(m[3]) : startLine,
    endCol: m[4] ? Number(m[4]) : startCol,
  }
}

export type SegmentState = 'covered' | 'uncovered' | 'none'

/** A run of source text with one highlight state; `clause` is the innermost clause it belongs to. */
export interface Segment {
  text: string
  state: SegmentState
  clause?: ClauseCoverageClause
}

/**
 * Cut the CQL text into runs for rendering. Clauses nest (`42 >= 18` contains `42` and `18`),
 * so every character takes the state of the innermost clause that contains it; text no clause
 * covers (keywords, define names, comments) stays neutral. Line numbers are matched against the
 * text as given — it must be the exact text the locators were computed from.
 */
export function buildSegments(cql: string, clauses: ClauseCoverageClause[]): Segment[] {
  const lineStarts: number[] = [0]
  for (let i = 0; i < cql.length; i++) {
    if (cql[i] === '\n') lineStarts.push(i + 1)
  }
  const offset = (line: number, col: number): number => {
    const start = lineStarts[line - 1]
    if (start === undefined) return -1
    return start + col - 1
  }

  const owner: (ClauseCoverageClause | undefined)[] = new Array<ClauseCoverageClause | undefined>(cql.length).fill(undefined)
  const spans = clauses
    .map((clause) => ({ clause, loc: parseLocator(clause.locator) }))
    .filter((s): s is { clause: ClauseCoverageClause; loc: Locator } => s.loc !== null)
    .map(({ clause, loc }) => ({ clause, from: offset(loc.startLine, loc.startCol), to: offset(loc.endLine, loc.endCol) }))
    .filter((s) => s.from >= 0 && s.to >= s.from)
    // widest first, so an inner clause written later wins
    .sort((a, b) => b.to - b.from - (a.to - a.from))
  for (const { clause, from, to } of spans) {
    for (let i = from; i <= to && i < cql.length; i++) owner[i] = clause
  }

  const segments: Segment[] = []
  let current: Segment | null = null
  for (let i = 0; i < cql.length; i++) {
    const clause = owner[i]
    const state: SegmentState = clause ? (clause.hits > 0 ? 'covered' : 'uncovered') : 'none'
    if (current && current.state === state && current.clause === clause) {
      current.text += cql[i]
    } else {
      current = { text: cql[i], state, clause }
      segments.push(current)
    }
  }
  return segments
}

/** `Retrieve → [FHIR.Condition/c1] (2×)` for a tooltip. */
export function describeClause(clause: ClauseCoverageClause): string {
  const hits = clause.hits === 1 ? '' : ` (${clause.hits}×)`
  if (clause.hits === 0) return clause.type
  return `${clause.type} → ${clause.value ?? 'null'}${hits}`
}

export function percentLabel(coverage: Pick<ClauseCoverage, 'coveredClauses' | 'totalClauses' | 'percent'>): string {
  return `${coverage.percent.toFixed(1)}% (${coverage.coveredClauses}/${coverage.totalClauses})`
}
