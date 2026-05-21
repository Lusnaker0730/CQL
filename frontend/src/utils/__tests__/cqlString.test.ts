import { describe, it, expect } from 'vitest'
import { escapeCqlString, escapeCqlIdentifier, formatFieldValue } from '../cqlString'

describe('escapeCqlString', () => {
  it('passes through plain ASCII unchanged', () => {
    expect(escapeCqlString('hello world')).toBe('hello world')
  })

  it("escapes single quotes for embedding inside '...' literals", () => {
    expect(escapeCqlString("it's a code")).toBe("it\\'s a code")
  })

  it('escapes backslashes', () => {
    expect(escapeCqlString('back\\slash')).toBe('back\\\\slash')
  })

  it('escapes both backslashes and single quotes', () => {
    expect(escapeCqlString("a'b\\c")).toBe("a\\'b\\\\c")
  })
})

describe('escapeCqlIdentifier', () => {
  it("does NOT escape single quotes — only backslash + double quote", () => {
    expect(escapeCqlIdentifier("don't")).toBe("don't")
  })

  it('escapes double quotes for embedding inside "..." identifiers', () => {
    expect(escapeCqlIdentifier('Diabetes "Type 2"')).toBe('Diabetes \\"Type 2\\"')
  })

  it('escapes backslashes', () => {
    expect(escapeCqlIdentifier('back\\slash')).toBe('back\\\\slash')
  })
})

describe('PAT-136 regression — terminology CQL output', () => {
  it('ValueSetTab valueset declaration survives a quoted title (escapeCqlIdentifier)', () => {
    const title = 'Diabetes "Type 2"'
    const url = "http://example.com/x's"
    const cql = `valueset "${escapeCqlIdentifier(title)}": '${escapeCqlString(url)}'`
    // The output should not contain unescaped " inside the identifier or
    // unescaped ' inside the literal — exactly one balanced pair of each.
    expect(cql).toBe('valueset "Diabetes \\"Type 2\\"": \'http://example.com/x\\\'s\'')
  })

  it('CodeLookupTab code declaration escapes display + system label', () => {
    const display = 'Heart "Failure"'
    const code = "I50.9"
    const systemLabel = 'ICD-10 "fictional" name'
    const cql = `code "${escapeCqlIdentifier(display)}": '${escapeCqlString(code)}' from "${escapeCqlIdentifier(systemLabel)}"`
    expect(cql).toBe('code "Heart \\"Failure\\"": \'I50.9\' from "ICD-10 \\"fictional\\" name"')
  })
})

describe('formatFieldValue', () => {
  it('returns empty string for empty value', () => {
    expect(formatFieldValue({ value: '', mode: 'literal' })).toBe('')
  })

  it("wraps literal values in single quotes with escaping", () => {
    expect(formatFieldValue({ value: "it's", mode: 'literal' })).toBe("'it\\'s'")
  })

  it('returns expression mode values verbatim', () => {
    expect(formatFieldValue({ value: 'Patient.name', mode: 'expression' })).toBe('Patient.name')
  })
})
