import type { ConjunctionGroup, ConjunctionKind, ElementInstance } from '../types/authoring'
import { elementToConjunctionGroup, createConjunctionElement } from '../types/authoring'
import { generateId } from './validation'
import {
  CONJUNCTION_COLOR_AND, CONJUNCTION_COLOR_OR,
  CONJUNCTION_COLOR_UNION, CONJUNCTION_COLOR_INTERSECT,
} from '../constants/authoringConstants'

/** Resolve the conjunction kind from a group id, defaulting to 'And' */
export function resolveKind(id: string): ConjunctionKind {
  if (id === 'Or') return 'Or'
  if (id === 'Union') return 'Union'
  if (id === 'Intersect') return 'Intersect'
  return 'And'
}

/** Ordered cycle for toggling conjunction types */
export const CONJ_CYCLE: ConjunctionKind[] = ['And', 'Or', 'Union', 'Intersect']

/** Get the next conjunction kind in the cycle */
export function nextConjunction(current: ConjunctionKind): ConjunctionKind {
  const idx = CONJ_CYCLE.indexOf(current)
  return CONJ_CYCLE[(idx + 1) % CONJ_CYCLE.length]
}

/** Map a conjunction kind to its display color */
export function conjColor(kind: ConjunctionKind): string {
  switch (kind) {
    case 'Or': return CONJUNCTION_COLOR_OR
    case 'Union': return CONJUNCTION_COLOR_UNION
    case 'Intersect': return CONJUNCTION_COLOR_INTERSECT
    default: return CONJUNCTION_COLOR_AND
  }
}

/**
 * When the connector at position `index` is toggled to a different conjunction
 * than the group's current type, wrap the adjacent pair into a sub-group.
 *
 * If the new conjunction is the SAME as the parent, this is a no-op.
 */
export function changeConnectorAt(
  group: ConjunctionGroup,
  index: number,
  newConj: ConjunctionKind
): ConjunctionGroup {
  const children = group.childInstances
  if (index < 0 || index >= children.length - 1) return group

  // Same as parent conjunction → no-op (no wrapping needed)
  if (newConj === resolveKind(group.id)) return group

  const left = children[index]
  const right = children[index + 1]

  const newSubGroup = createConjunctionElement(newConj, [left, right], generateId())

  const newChildren = [
    ...children.slice(0, index),
    newSubGroup,
    ...children.slice(index + 2),
  ]

  return simplifyTree({ ...group, childInstances: newChildren })
}

/**
 * Append an empty sub-group of the given conjunction type.
 */
export function addSubGroup(
  group: ConjunctionGroup,
  conjType: ConjunctionKind
): ConjunctionGroup {
  const newSubGroup = createConjunctionElement(conjType, [], generateId())
  return {
    ...group,
    childInstances: [...group.childInstances, newSubGroup],
  }
}

/**
 * Simplify the tree:
 * 1. Flatten single-child sub-groups (unwrap the child to parent level)
 * 2. Merge nested groups with the same conjunction into parent
 * 3. Repeat until stable (a single pass may expose new simplification opportunities)
 */
export function simplifyTree(group: ConjunctionGroup): ConjunctionGroup {
  const parentKind = resolveKind(group.id)
  const simplified: ElementInstance[] = []

  for (const child of group.childInstances) {
    if (!child.conjunction) {
      simplified.push(child)
      continue
    }

    // Recursively simplify child groups first
    const childGroup = simplifyTree(elementToConjunctionGroup(child))
    const simplifiedChild: ElementInstance = {
      ...child,
      type: childGroup.id,
      name: childGroup.name,
      childInstances: childGroup.childInstances,
    }

    // Single-child sub-group → unwrap the lone child to parent level
    if (childGroup.childInstances.length === 1) {
      simplified.push(childGroup.childInstances[0])
      continue
    }

    // Same conjunction as parent AND has 2+ children → merge children up
    // (empty sub-groups are preserved — user created them intentionally)
    if (resolveKind(childGroup.id) === parentKind && childGroup.childInstances.length >= 2) {
      simplified.push(...childGroup.childInstances)
      continue
    }

    simplified.push(simplifiedChild)
  }

  let result: ConjunctionGroup = { ...group, childInstances: simplified }

  // If this group itself has exactly 1 child that is a conjunction group,
  // this layer is redundant — adopt the child's conjunction type and children.
  // e.g. OR([AND(A, B)]) → AND(A, B)
  if (result.childInstances.length === 1) {
    const only = result.childInstances[0]
    if (only.conjunction && only.childInstances && only.childInstances.length > 0) {
      result = {
        ...result,
        id: only.type || only.name,
        name: only.name || only.type,
        childInstances: only.childInstances,
      }
      // Recurse once more — the promoted children may need further simplification
      return simplifyTree(result)
    }
  }

  return result
}
