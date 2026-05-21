import { describe, it, expect, vi, afterEach } from 'vitest'
import { randomInt, randomElement, randomFloat, pickRandom } from '../random'

describe('random', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('randomInt', () => {
    it('returns min when Math.random returns 0', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0)
      expect(randomInt(5, 10)).toBe(5)
    })

    it('returns max when Math.random returns value just below 1', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.999999)
      // (0.999999 * 6) -> 5.99... -> floor 5, +5 = 10
      expect(randomInt(5, 10)).toBe(10)
    })

    it('returns a value in [min, max] range over 100 calls', () => {
      for (let i = 0; i < 100; i++) {
        const v = randomInt(3, 7)
        expect(v).toBeGreaterThanOrEqual(3)
        expect(v).toBeLessThanOrEqual(7)
        expect(Number.isInteger(v)).toBe(true)
      }
    })

    it('handles single-value range', () => {
      expect(randomInt(4, 4)).toBe(4)
    })
  })

  describe('randomElement', () => {
    it('picks the first element when random is 0', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0)
      expect(randomElement(['a', 'b', 'c'])).toBe('a')
    })

    it('picks the last element when random is nearly 1', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.999)
      expect(randomElement(['a', 'b', 'c'])).toBe('c')
    })

    it('returns typed element from readonly array', () => {
      const arr = ['x', 'y'] as const
      const picked = randomElement(arr)
      expect(['x', 'y']).toContain(picked)
    })
  })

  describe('randomFloat', () => {
    it('returns min when Math.random returns 0', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0)
      expect(randomFloat(2.5, 7.5)).toBe(2.5)
    })

    it('rounds to default 1 decimal', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.5)
      // 0.5 * (10 - 0) + 0 = 5.0 → rounded to 5
      expect(randomFloat(0, 10)).toBe(5)
    })

    it('respects custom decimal places', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.33333)
      expect(randomFloat(0, 1, 3)).toBe(0.333)
    })

    it('produces values within bounds', () => {
      for (let i = 0; i < 100; i++) {
        const v = randomFloat(1, 5, 2)
        expect(v).toBeGreaterThanOrEqual(1)
        expect(v).toBeLessThanOrEqual(5)
      }
    })
  })

  describe('pickRandom', () => {
    it('returns empty array when count is 0', () => {
      expect(pickRandom([1, 2, 3], 0)).toEqual([])
    })

    it('returns all elements when count >= arr length', () => {
      const result = pickRandom([1, 2, 3], 10)
      expect(result).toHaveLength(3)
      expect(result.sort()).toEqual([1, 2, 3])
    })

    it('returns exactly count elements', () => {
      const result = pickRandom([1, 2, 3, 4, 5], 3)
      expect(result).toHaveLength(3)
    })

    it('returns only elements from the source array', () => {
      const src = ['a', 'b', 'c', 'd', 'e']
      const picked = pickRandom(src, 3)
      picked.forEach((x) => expect(src).toContain(x))
    })

    it('does not mutate the input array', () => {
      const src = [1, 2, 3, 4, 5]
      const snapshot = [...src]
      pickRandom(src, 3)
      expect(src).toEqual(snapshot)
    })

    it('returns distinct elements (no duplicates)', () => {
      const picked = pickRandom([1, 2, 3, 4, 5], 5)
      expect(new Set(picked).size).toBe(picked.length)
    })

    it('handles empty input', () => {
      expect(pickRandom([], 3)).toEqual([])
    })
  })
})
