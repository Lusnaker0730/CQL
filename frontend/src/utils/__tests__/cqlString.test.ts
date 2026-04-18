import { describe, it, expect } from 'vitest'
import { escapeCqlString, formatFieldValue } from '../cqlString'

describe('cqlString', () => {
  describe('escapeCqlString', () => {
    it('escapes single quotes', () => {
      expect(escapeCqlString("it's")).toBe("it\\'s")
    })

    it('escapes backslashes', () => {
      expect(escapeCqlString('a\\b')).toBe('a\\\\b')
    })

    it('escapes backslashes before quotes (correct order)', () => {
      // Input: \'  (a literal backslash followed by a quote)
      // Must become: \\\'  (escaped backslash + escaped quote)
      expect(escapeCqlString("\\'")).toBe("\\\\\\'")
    })

    it('handles empty string', () => {
      expect(escapeCqlString('')).toBe('')
    })

    it('leaves ordinary text untouched', () => {
      expect(escapeCqlString('hello world')).toBe('hello world')
    })

    it('escapes multiple quotes in one string', () => {
      expect(escapeCqlString("don't say 'hi'")).toBe("don\\'t say \\'hi\\'")
    })
  })

  describe('formatFieldValue', () => {
    it('returns empty string for empty value', () => {
      expect(formatFieldValue({ value: '', mode: 'literal' })).toBe('')
      expect(formatFieldValue({ value: '', mode: 'expression' })).toBe('')
    })

    it('wraps literal mode in single quotes with escaping', () => {
      expect(formatFieldValue({ value: 'hello', mode: 'literal' })).toBe("'hello'")
      expect(formatFieldValue({ value: "it's", mode: 'literal' })).toBe("'it\\'s'")
    })

    it('returns expression mode verbatim', () => {
      expect(formatFieldValue({ value: 'Patient.name', mode: 'expression' })).toBe('Patient.name')
    })

    it('does not escape expression mode content', () => {
      // Expression mode is raw CQL — quotes belong to the author
      expect(formatFieldValue({ value: "\"MyVS\"", mode: 'expression' })).toBe('"MyVS"')
    })
  })
})
