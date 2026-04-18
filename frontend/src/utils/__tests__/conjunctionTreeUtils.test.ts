import { describe, it, expect } from 'vitest'
import {
  resolveKind,
  nextConjunction,
  conjColor,
  changeConnectorAt,
  addSubGroup,
  simplifyTree,
  CONJ_CYCLE,
} from '../conjunctionTreeUtils'
import type { ConjunctionGroup, ElementInstance } from '../../types/authoring'
import { createConjunctionElement } from '../../types/authoring'

// ── Test helpers ──
function leaf(id: string): ElementInstance {
  return {
    uniqueId: id,
    type: 'Observation',
    name: id,
    returnType: 'boolean',
    fields: [],
    modifiers: [],
  }
}

function group(kind: string, children: ElementInstance[]): ConjunctionGroup {
  return {
    id: kind,
    name: kind,
    conjunction: true,
    returnType: 'boolean',
    childInstances: children,
  }
}

describe('conjunctionTreeUtils', () => {
  describe('resolveKind', () => {
    it('returns the matching kind for each supported id', () => {
      expect(resolveKind('And')).toBe('And')
      expect(resolveKind('Or')).toBe('Or')
      expect(resolveKind('Union')).toBe('Union')
      expect(resolveKind('Intersect')).toBe('Intersect')
    })

    it('defaults to And for unknown ids', () => {
      expect(resolveKind('Unknown')).toBe('And')
      expect(resolveKind('')).toBe('And')
    })
  })

  describe('nextConjunction', () => {
    it('cycles through the conjunction order', () => {
      expect(nextConjunction('And')).toBe('Or')
      expect(nextConjunction('Or')).toBe('Union')
      expect(nextConjunction('Union')).toBe('Intersect')
      expect(nextConjunction('Intersect')).toBe('And')
    })

    it('CONJ_CYCLE exposes all 4 kinds in order', () => {
      expect(CONJ_CYCLE).toEqual(['And', 'Or', 'Union', 'Intersect'])
    })
  })

  describe('conjColor', () => {
    it('returns distinct colors per conjunction kind', () => {
      const colors = new Set([
        conjColor('And'),
        conjColor('Or'),
        conjColor('Union'),
        conjColor('Intersect'),
      ])
      expect(colors.size).toBe(4)
    })
  })

  describe('changeConnectorAt', () => {
    it('is a no-op when new kind equals parent kind', () => {
      const g = group('And', [leaf('A'), leaf('B'), leaf('C')])
      const result = changeConnectorAt(g, 0, 'And')
      expect(result).toBe(g)
    })

    it('is a no-op for out-of-range index (negative)', () => {
      const g = group('And', [leaf('A'), leaf('B')])
      expect(changeConnectorAt(g, -1, 'Or')).toBe(g)
    })

    it('is a no-op for out-of-range index (past last connector)', () => {
      const g = group('And', [leaf('A'), leaf('B')])
      // Last valid connector index is 0 (between A and B); 1 is past the end
      expect(changeConnectorAt(g, 1, 'Or')).toBe(g)
    })

    it('wraps adjacent pair into an OR sub-group when inside an AND parent', () => {
      const g = group('And', [leaf('A'), leaf('B'), leaf('C')])
      const result = changeConnectorAt(g, 1, 'Or')

      // Children should now be [A, OR(B, C)]
      expect(result.childInstances).toHaveLength(2)
      expect(result.childInstances[0].uniqueId).toBe('A')
      const sub = result.childInstances[1]
      expect(sub.conjunction).toBe(true)
      expect(sub.type).toBe('Or')
      expect(sub.childInstances?.map((c) => c.uniqueId)).toEqual(['B', 'C'])
    })
  })

  describe('addSubGroup', () => {
    it('appends an empty sub-group of the requested kind', () => {
      const g = group('And', [leaf('A')])
      const result = addSubGroup(g, 'Or')
      expect(result.childInstances).toHaveLength(2)
      const sub = result.childInstances[1]
      expect(sub.type).toBe('Or')
      expect(sub.conjunction).toBe(true)
      expect(sub.childInstances).toEqual([])
    })

    it('does not modify the original group', () => {
      const g = group('And', [leaf('A')])
      const snapshot = [...g.childInstances]
      addSubGroup(g, 'Or')
      expect(g.childInstances).toEqual(snapshot)
    })
  })

  describe('simplifyTree', () => {
    it('returns single-leaf tree unchanged structurally', () => {
      const g = group('And', [leaf('A')])
      const result = simplifyTree(g)
      expect(result.childInstances).toHaveLength(1)
      expect(result.childInstances[0].uniqueId).toBe('A')
    })

    it('unwraps a single-child sub-group (flatten)', () => {
      // Parent AND contains [A, OR(B)] → single-child OR unwraps to B
      const orSub = createConjunctionElement('Or', [leaf('B')], 'or-1')
      const g = group('And', [leaf('A'), orSub])
      const result = simplifyTree(g)

      expect(result.childInstances).toHaveLength(2)
      expect(result.childInstances[0].uniqueId).toBe('A')
      expect(result.childInstances[1].uniqueId).toBe('B')
    })

    it('merges same-kind nested groups into parent', () => {
      // AND(A, AND(B, C), D) → AND(A, B, C, D)
      const innerAnd = createConjunctionElement('And', [leaf('B'), leaf('C')], 'and-inner')
      const g = group('And', [leaf('A'), innerAnd, leaf('D')])
      const result = simplifyTree(g)

      expect(result.childInstances.map((c) => c.uniqueId)).toEqual(['A', 'B', 'C', 'D'])
    })

    it('preserves different-kind nested groups (no merge)', () => {
      // AND(A, OR(B, C), D) stays as AND(A, OR(B, C), D)
      const orSub = createConjunctionElement('Or', [leaf('B'), leaf('C')], 'or-inner')
      const g = group('And', [leaf('A'), orSub, leaf('D')])
      const result = simplifyTree(g)

      expect(result.childInstances).toHaveLength(3)
      expect(result.childInstances[0].uniqueId).toBe('A')
      expect(result.childInstances[1].type).toBe('Or')
      expect(result.childInstances[1].childInstances?.map((c) => c.uniqueId)).toEqual(['B', 'C'])
      expect(result.childInstances[2].uniqueId).toBe('D')
    })

    it('preserves empty sub-groups (user intent)', () => {
      // AND(A, AND()) → empty sub-group kept (single-child unwrap applies only to >=1 child)
      // Single-child unwrap trigger: childInstances.length === 1.
      // Empty sub-group (length === 0) goes through and is preserved.
      const emptySub = createConjunctionElement('And', [], 'and-empty')
      const g = group('And', [leaf('A'), emptySub])
      const result = simplifyTree(g)

      expect(result.childInstances).toHaveLength(2)
      expect(result.childInstances[0].uniqueId).toBe('A')
      expect(result.childInstances[1].conjunction).toBe(true)
      expect(result.childInstances[1].childInstances).toEqual([])
    })

    it('promotes single-child wrapper group to its inner kind (OR([AND(A,B)]) → AND(A,B))', () => {
      const innerAnd = createConjunctionElement('And', [leaf('A'), leaf('B')], 'and-inner')
      const wrapper = group('Or', [innerAnd])
      const result = simplifyTree(wrapper)

      // Single child is a conjunction group with >=1 children → adopt its kind/children
      expect(result.id).toBe('And')
      expect(result.childInstances.map((c) => c.uniqueId)).toEqual(['A', 'B'])
    })
  })
})
