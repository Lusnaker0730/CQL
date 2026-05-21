import { describe, it, expect } from 'vitest'
import { getScoreChipColor, getScoreThemeColor, getScoreHex } from '../scoreColors'

describe('scoreColors', () => {
  describe('getScoreChipColor', () => {
    it('returns success for score >= 80', () => {
      expect(getScoreChipColor(80)).toBe('success')
      expect(getScoreChipColor(95)).toBe('success')
      expect(getScoreChipColor(100)).toBe('success')
    })

    it('returns warning for score 60..79', () => {
      expect(getScoreChipColor(60)).toBe('warning')
      expect(getScoreChipColor(70)).toBe('warning')
      expect(getScoreChipColor(79.9)).toBe('warning')
    })

    it('returns error for score < 60', () => {
      expect(getScoreChipColor(0)).toBe('error')
      expect(getScoreChipColor(59)).toBe('error')
      expect(getScoreChipColor(-5)).toBe('error')
    })

    it('returns error for null/undefined', () => {
      expect(getScoreChipColor(null)).toBe('error')
      expect(getScoreChipColor(undefined)).toBe('error')
    })
  })

  describe('getScoreThemeColor', () => {
    it('returns disabled for null/undefined', () => {
      expect(getScoreThemeColor(null)).toBe('text.disabled')
      expect(getScoreThemeColor(undefined)).toBe('text.disabled')
    })

    it('matches chip color tiers for non-null values', () => {
      expect(getScoreThemeColor(85)).toBe('success.main')
      expect(getScoreThemeColor(65)).toBe('warning.main')
      expect(getScoreThemeColor(30)).toBe('error.main')
    })

    it('honors 80 / 60 boundaries', () => {
      expect(getScoreThemeColor(80)).toBe('success.main')
      expect(getScoreThemeColor(79)).toBe('warning.main')
      expect(getScoreThemeColor(60)).toBe('warning.main')
      expect(getScoreThemeColor(59)).toBe('error.main')
    })
  })

  describe('getScoreHex', () => {
    it('returns grey for null/undefined', () => {
      expect(getScoreHex(null)).toBe('#9E9E9E')
      expect(getScoreHex(undefined)).toBe('#9E9E9E')
    })

    it('returns hex consistent with the theme tiers', () => {
      expect(getScoreHex(85)).toBe('#2E7D32')  // green
      expect(getScoreHex(65)).toBe('#ED6C02')  // orange
      expect(getScoreHex(40)).toBe('#D32F2F')  // red
    })

    it('honors boundaries', () => {
      expect(getScoreHex(80)).toBe('#2E7D32')
      expect(getScoreHex(79)).toBe('#ED6C02')
      expect(getScoreHex(60)).toBe('#ED6C02')
      expect(getScoreHex(59)).toBe('#D32F2F')
    })
  })
})
