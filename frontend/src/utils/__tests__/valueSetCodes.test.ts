import { describe, it, expect } from 'vitest'
import { cqlDeclaration, mergeConcepts, parsePastedCodes, suggestNextVersion } from '../valueSetCodes'

const NHI = 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw'

describe('parsePastedCodes — PAT-230', () => {
  it('reads three-column lines, and two- or one-column lines with the default system', () => {
    const { concepts, skippedLines } = parsePastedCodes(
      `http://loinc.org, 4548-4, HbA1c/Hb.total\n09006C, HbA1c\n09139C`,
      NHI,
    )
    expect(concepts).toEqual([
      { system: 'http://loinc.org', code: '4548-4', display: 'HbA1c/Hb.total' },
      { system: NHI, code: '09006C', display: 'HbA1c' },
      { system: NHI, code: '09139C' },
    ])
    expect(skippedLines).toEqual([])
  })

  it('takes tab-separated spreadsheet rows, keeping commas inside the display', () => {
    const { concepts } = parsePastedCodes(`${NHI}\t09006C\tHbA1c, glycated haemoglobin`, '')
    expect(concepts).toEqual([{ system: NHI, code: '09006C', display: 'HbA1c, glycated haemoglobin' }])
  })

  it('treats a URL-looking first column as the system even with two columns', () => {
    expect(parsePastedCodes('http://loinc.org;4548-4', NHI).concepts).toEqual([{ system: 'http://loinc.org', code: '4548-4' }])
    expect(parsePastedCodes('urn:oid:2.16.840.1.113883.6.96，44054006', '').concepts)
      .toEqual([{ system: 'urn:oid:2.16.840.1.113883.6.96', code: '44054006' }])
  })

  it('ignores blank lines and a header row, strips quotes', () => {
    const { concepts } = parsePastedCodes(`system,code,display\n\n"${NHI}","09006C","HbA1c"\n`, '')
    expect(concepts).toEqual([{ system: NHI, code: '09006C', display: 'HbA1c' }])
  })

  it('reports the lines it could not read instead of dropping them silently', () => {
    const { concepts, skippedLines } = parsePastedCodes(`09006C, HbA1c\n${NHI}, , no code here\n09139C`, '')
    // No default system: the two bare codes cannot be placed, and line 2 has no code.
    expect(concepts).toEqual([])
    expect(skippedLines).toEqual([1, 2, 3])
  })
})

describe('mergeConcepts', () => {
  it('appends new codes and keeps the first of a duplicate (same system + version + code)', () => {
    const existing = [{ system: NHI, code: '09006C', display: 'HbA1c' }]
    const merged = mergeConcepts(existing, [
      { system: NHI, code: '09006C', display: 'renamed' },
      { system: 'http://loinc.org', code: '09006C' },
      { system: NHI, code: '09139C' },
    ])
    expect(merged.map((c) => `${c.system === NHI ? 'nhi' : 'loinc'}:${c.code}:${c.display ?? ''}`))
      .toEqual(['nhi:09006C:HbA1c', 'loinc:09006C:', 'nhi:09139C:'])
    expect(existing).toHaveLength(1) // not mutated
  })
})

describe('cqlDeclaration', () => {
  const vs = { name: 'HbA1cOrders', title: 'HbA1c "order" codes', url: "https://h.example.tw/fhir/ValueSet/it's", version: '1.2.0' }

  it('uses the title as identifier and the URL as the reference, escaped for CQL', () => {
    expect(cqlDeclaration(vs, false)).toBe(`valueset "HbA1c \\"order\\" codes": 'https://h.example.tw/fhir/ValueSet/it\\'s'`)
  })

  it('pins the version on request, and falls back to the name without a title', () => {
    expect(cqlDeclaration({ ...vs, title: undefined, url: 'https://h.example.tw/fhir/ValueSet/x' }, true))
      .toBe(`valueset "HbA1cOrders": 'https://h.example.tw/fhir/ValueSet/x' version '1.2.0'`)
  })
})

describe('suggestNextVersion', () => {
  it('bumps the patch of a semantic version, and never returns the same version otherwise', () => {
    expect(suggestNextVersion('1.2.9')).toBe('1.2.10')
    expect(suggestNextVersion(' 2024A ')).toBe('2024A-2')
  })
})
