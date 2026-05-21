import { describe, it, expect } from 'vitest'
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

/**
 * Independent implementation of Taiwan ROC National ID validation, mirroring
 * the official algorithm (Article 4 of 戶籍法施行細則 / well-documented public
 * spec). Used to verify that {@link generateNhiId} produces IDs that pass
 * external validators (FHIR servers / IG conformance).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Map first letter to two digits via {@link NHI_LETTER_TABLE}.</li>
 *   <li>Build 11 digits: [d1, d2, gender, r1..r7, check].</li>
 *   <li>Multiply by weights {@code [1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1]}.</li>
 *   <li>Sum must be divisible by 10.</li>
 * </ol>
 */
const NHI_LETTER_TABLE: Record<string, number> = {
  A: 10, B: 11, C: 12, D: 13, E: 14, F: 15, G: 16, H: 17,
  I: 34, J: 18, K: 19, L: 20, M: 21, N: 22, O: 35, P: 23,
  Q: 24, R: 25, S: 26, T: 27, U: 28, V: 29, W: 32, X: 30,
  Y: 31, Z: 33,
}

const NHI_WEIGHTS = [1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1] as const

function isValidTwNhi(id: string): boolean {
  if (!/^[A-Z][12]\d{8}$/.test(id)) return false
  const letterValue = NHI_LETTER_TABLE[id[0]]
  if (letterValue === undefined) return false
  const digits = [
    Math.floor(letterValue / 10),
    letterValue % 10,
    ...id.slice(1).split('').map(Number),
  ]
  const sum = digits.reduce((acc, d, i) => acc + d * NHI_WEIGHTS[i], 0)
  return sum % 10 === 0
}

describe('isValidTwNhi (test helper sanity check)', () => {
  it('rejects clearly invalid formats', () => {
    expect(isValidTwNhi('')).toBe(false)
    expect(isValidTwNhi('123456789')).toBe(false) // no letter
    expect(isValidTwNhi('A12345678')).toBe(false) // 9 chars
    expect(isValidTwNhi('A1234567899')).toBe(false) // 11 chars
    expect(isValidTwNhi('A323456789')).toBe(false) // gender digit 3
    expect(isValidTwNhi('a123456789')).toBe(false) // lowercase
  })

  it('passes the canonical valid test ID A123456789', () => {
    // Hand-verified using the official algorithm:
    //   A → 10 → digits [1, 0]
    //   sequence [1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
    //   weights  [1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1]
    //   sum = 1+0+8+14+18+20+20+18+14+8+9 = 130 → 130 mod 10 = 0 ✓
    expect(isValidTwNhi('A123456789')).toBe(true)
  })
})

describe('generateNhiId — PAT-148 validity regression', () => {
  it('produces 10-character IDs in the expected format', () => {
    for (let i = 0; i < 50; i++) {
      const id = generateNhiId('male')
      expect(id).toMatch(/^[A-Z]1\d{8}$/)
    }
    for (let i = 0; i < 50; i++) {
      const id = generateNhiId('female')
      expect(id).toMatch(/^[A-Z]2\d{8}$/)
    }
  })

  it('passes the official Taiwan NHI checksum across many samples', () => {
    // PAT-148: lock the checksum invariant. If the generator drifts (omits a
    // digit from the sum, picks the wrong weight, etc.), this test will catch
    // it across a 200-sample batch — even probabilistic regressions where
    // only some IDs become invalid.
    const samples = Array.from({ length: 200 }, () =>
      generateNhiId(Math.random() < 0.5 ? 'male' : 'female')
    )
    const invalid = samples.filter((id) => !isValidTwNhi(id))
    expect(invalid).toEqual([])
  })

  it('encodes gender correctly: male → 1, female → 2', () => {
    expect(generateNhiId('male')[1]).toBe('1')
    expect(generateNhiId('female')[1]).toBe('2')
  })

  it('starts with one of the 26 valid letters', () => {
    const letters = new Set(
      Array.from({ length: 100 }, () => generateNhiId('male')[0])
    )
    for (const l of letters) {
      expect(NHI_LETTER_TABLE).toHaveProperty(l)
    }
  })
})

describe('generateName', () => {
  it('returns Chinese family + given name pieces for both genders', () => {
    const m = generateName('male')
    expect(m.family).toMatch(/^.+$/)
    expect(m.given).toMatch(/^.+$/)
    const f = generateName('female')
    expect(f.family).toMatch(/^.+$/)
    expect(f.given).toMatch(/^.+$/)
  })
})

describe('generateAddress', () => {
  it('returns city/district/postalCode/line shape', () => {
    const a = generateAddress()
    expect(typeof a.city).toBe('string')
    expect(typeof a.district).toBe('string')
    expect(a.postalCode).toMatch(/^\d{3}$/)
    expect(a.line).toMatch(/^.+\d+號$/)
  })
})

describe('generateMobile', () => {
  it('produces a 09XX-XXX-XXXX shape', () => {
    for (let i = 0; i < 20; i++) {
      const phone = generateMobile()
      expect(phone).toMatch(/^09\d{2}-\d{3}-\d{3,4}$/)
    }
  })
})

describe('generateLandline', () => {
  it('produces a known area code prefix', () => {
    for (let i = 0; i < 20; i++) {
      const phone = generateLandline()
      expect(phone).toMatch(/^0\d-\d{4}-\d{4}$/)
    }
  })
})

describe('generateBirthDate', () => {
  it('produces ISO date for an adult age (18-80)', () => {
    const now = new Date()
    for (let i = 0; i < 20; i++) {
      const dateStr = generateBirthDate()
      expect(dateStr).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      const year = Number(dateStr.slice(0, 4))
      const ageThisYear = now.getFullYear() - year
      expect(ageThisYear).toBeGreaterThanOrEqual(18)
      expect(ageThisYear).toBeLessThanOrEqual(80)
    }
  })
})

describe('randomDateInRange', () => {
  it('clamps to the explicit from/to range', () => {
    for (let i = 0; i < 20; i++) {
      const result = randomDateInRange('2024-01-01', '2024-12-31')
      expect(result >= '2024-01-01').toBe(true)
      expect(result <= '2024-12-31').toBe(true)
    }
  })

  it('falls back to defaultDaysBack when no range supplied', () => {
    const result = randomDateInRange(undefined, undefined, 30)
    const today = new Date().toISOString().split('T')[0]
    expect(result <= today).toBe(true)
  })
})

describe('randomGender', () => {
  it('returns either male or female', () => {
    const sample = new Set(Array.from({ length: 200 }, () => randomGender()))
    expect(sample.size).toBeLessThanOrEqual(2)
    expect([...sample].every((g) => g === 'male' || g === 'female')).toBe(true)
  })
})

describe('generateEmail', () => {
  it('produces an email with one of the configured providers', () => {
    const email = generateEmail('Lin', 'Alice')
    expect(email).toMatch(/^alicelin\d+@(gmail\.com|yahoo\.com\.tw|hotmail\.com|outlook\.com)$/)
  })
})
