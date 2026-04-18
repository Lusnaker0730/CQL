import { describe, it, expect, vi, afterEach } from 'vitest'
import {
  generateNhiId,
  generateName,
  generateAddress,
  generateMobile,
  generateLandline,
  generateBirthDate,
  randomDateInRange,
  randomGender,
  generateEmail,
} from '../twDemographics'

describe('twDemographics', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('generateNhiId', () => {
    /** Independent check-digit calculator to verify output validity */
    function nhiChecksum(id: string): boolean {
      const LETTER: Record<string, number> = {
        A: 10, B: 11, C: 12, D: 13, E: 14, F: 15, G: 16, H: 17,
        I: 34, J: 18, K: 19, L: 20, M: 21, N: 22, O: 35, P: 23,
        Q: 24, R: 25, S: 26, T: 27, U: 28, V: 29, W: 32, X: 30,
        Y: 31, Z: 33,
      }
      const letter = id[0]
      const n = LETTER[letter]
      const d1 = Math.floor(n / 10)
      const d2 = n % 10
      const weights = [1, 9, 8, 7, 6, 5, 4, 3, 2]
      const rest = id.slice(1).split('').map((c) => Number(c))
      const all = [d1, d2, ...rest.slice(0, 8)]
      let sum = 0
      for (let i = 0; i < 9; i++) sum += all[i] * weights[i]
      const expected = (10 - (sum % 10)) % 10
      return rest[8] === expected
    }

    it('produces a 10-char ID starting with uppercase letter', () => {
      const id = generateNhiId('male')
      expect(id).toHaveLength(10)
      expect(id[0]).toMatch(/^[A-Z]$/)
    })

    it('male ID has gender digit 1', () => {
      const id = generateNhiId('male')
      expect(id[1]).toBe('1')
    })

    it('female ID has gender digit 2', () => {
      const id = generateNhiId('female')
      expect(id[1]).toBe('2')
    })

    it('computed check digit is valid', () => {
      for (let i = 0; i < 30; i++) {
        const id = generateNhiId(i % 2 === 0 ? 'male' : 'female')
        expect(nhiChecksum(id)).toBe(true)
      }
    })
  })

  describe('generateName', () => {
    it('returns a surname and given name', () => {
      const { family, given } = generateName('male')
      expect(typeof family).toBe('string')
      expect(family.length).toBeGreaterThan(0)
      expect(typeof given).toBe('string')
      expect(given.length).toBeGreaterThan(0)
    })

    it('uses gender-appropriate given name pool', () => {
      // Force predictable picks
      vi.spyOn(Math, 'random').mockReturnValue(0)
      const male = generateName('male')
      const female = generateName('female')
      // First entry of MALE_GIVEN_NAMES vs FEMALE_GIVEN_NAMES differ
      expect(male.given).not.toBe(female.given)
    })
  })

  describe('generateAddress', () => {
    it('returns complete address with all fields populated', () => {
      const addr = generateAddress()
      expect(addr.city.length).toBeGreaterThan(0)
      expect(addr.district.length).toBeGreaterThan(0)
      expect(addr.postalCode).toMatch(/^\d{3}$/)
      expect(addr.line).toMatch(/^.+\d+號$/)
    })
  })

  describe('generateMobile', () => {
    it('matches Taiwan mobile format 09XX-XXX-XXXX', () => {
      const phone = generateMobile()
      expect(phone).toMatch(/^09\d{2}-\d{3}-\d{3}$/)
    })
  })

  describe('generateLandline', () => {
    it('matches Taiwan landline format XX-XXXX-XXXX or similar', () => {
      const phone = generateLandline()
      expect(phone).toMatch(/^0\d{1,2}-\d{4}-\d{4}$/)
    })
  })

  describe('generateBirthDate', () => {
    it('returns valid YYYY-MM-DD string', () => {
      const d = generateBirthDate()
      expect(d).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    })

    it('produces a date that implies age 18-80', () => {
      const d = generateBirthDate()
      const year = Number(d.slice(0, 4))
      const currentYear = new Date().getFullYear()
      const age = currentYear - year
      expect(age).toBeGreaterThanOrEqual(18)
      expect(age).toBeLessThanOrEqual(80)
    })
  })

  describe('randomDateInRange', () => {
    it('returns date within explicit range', () => {
      const d = randomDateInRange('2025-01-01', '2025-12-31')
      expect(d).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      expect(d >= '2025-01-01').toBe(true)
      expect(d <= '2025-12-31').toBe(true)
    })

    it('uses defaultDaysBack when no from provided', () => {
      const d = randomDateInRange(undefined, '2025-12-31', 30)
      expect(d >= '2025-12-01').toBe(true)
      expect(d <= '2025-12-31').toBe(true)
    })

    it('returns today when from == to', () => {
      const d = randomDateInRange('2025-06-15', '2025-06-15')
      expect(d).toBe('2025-06-15')
    })
  })

  describe('randomGender', () => {
    it('returns male when random < 0.5', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.3)
      expect(randomGender()).toBe('male')
    })

    it('returns female when random >= 0.5', () => {
      vi.spyOn(Math, 'random').mockReturnValue(0.7)
      expect(randomGender()).toBe('female')
    })
  })

  describe('generateEmail', () => {
    it('includes family/given and a number', () => {
      const email = generateEmail('Chen', 'Wei')
      expect(email).toMatch(/^wei.*chen.*@(gmail|yahoo|hotmail|outlook)\.(com|com\.tw)$/)
    })

    it('lowercases family and given names', () => {
      const email = generateEmail('CHEN', 'WEI')
      const local = email.split('@')[0]
      expect(local).toMatch(/^[a-z0-9]+$/i)
      expect(local).not.toMatch(/[A-Z]/)
    })
  })
})
