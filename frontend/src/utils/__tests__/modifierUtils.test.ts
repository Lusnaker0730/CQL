import { describe, it, expect } from 'vitest'
import {
  getEffectiveReturnType,
  getReturnTypeAtPosition,
  isModifierComplete,
} from '../modifierUtils'
import type { Modifier } from '../../types/authoring'

const mod = (overrides: Partial<Modifier>): Modifier => ({
  id: 'X',
  name: 'X',
  inputTypes: ['list_of_observations'],
  returnType: 'list_of_observations',
  cqlTemplate: 'BaseModifier',
  ...overrides,
})

describe('modifierUtils', () => {
  describe('getEffectiveReturnType', () => {
    it('returns base when no modifiers', () => {
      expect(getEffectiveReturnType('list_of_observations')).toBe('list_of_observations')
    })

    it('returns the last complete modifier returnType', () => {
      const chain = [
        mod({ id: 'Verified', returnType: 'list_of_observations' }),
        mod({ id: 'MostRecent', returnType: 'observation' }),
        mod({ id: 'QuantityValue', returnType: 'system_quantity' }),
      ]
      expect(getEffectiveReturnType('list_of_observations', chain)).toBe('system_quantity')
    })

    it('skips incomplete modifiers at tail', () => {
      const chain = [
        mod({ id: 'Verified', returnType: 'list_of_observations' }),
        // incomplete LookBack — no values
        mod({
          id: 'LookBack',
          returnType: 'list_of_observations',
          cqlTemplate: 'LookBackModifier',
          values: {},
        }),
      ]
      expect(isModifierComplete(chain[1])).toBe(false)
      // Skips incomplete LookBack, returns last complete (Verified)
      expect(getEffectiveReturnType('list_of_observations', chain)).toBe('list_of_observations')
    })
  })

  describe('getReturnTypeAtPosition', () => {
    const chain = [
      mod({ id: 'Verified', returnType: 'list_of_observations' }),
      mod({ id: 'MostRecent', returnType: 'observation' }),
      mod({ id: 'QuantityValue', returnType: 'system_quantity' }),
    ]

    it('returns base type at position 0', () => {
      expect(getReturnTypeAtPosition('list_of_observations', chain, 0)).toBe('list_of_observations')
    })

    it('returns type after first modifier when inserting at 1', () => {
      expect(getReturnTypeAtPosition('list_of_observations', chain, 1)).toBe('list_of_observations')
    })

    it('returns type after two modifiers when inserting at 2', () => {
      expect(getReturnTypeAtPosition('list_of_observations', chain, 2)).toBe('observation')
    })

    it('returns type after all modifiers when inserting at tail', () => {
      expect(getReturnTypeAtPosition('list_of_observations', chain, 3)).toBe('system_quantity')
    })

    it('handles empty chain', () => {
      expect(getReturnTypeAtPosition('list_of_observations', [], 0)).toBe('list_of_observations')
      expect(getReturnTypeAtPosition('list_of_observations', undefined, 0)).toBe('list_of_observations')
    })
  })
})
