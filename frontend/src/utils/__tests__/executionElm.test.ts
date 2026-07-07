import { describe, it, expect } from 'vitest'
import { freshPrecompiledElm } from '../executionElm'

describe('freshPrecompiledElm', () => {
  const ELM = '{"library":{"identifier":{"id":"X"}}}'
  const CQL = 'library X version \'1\'\ndefine "A": true'

  it('returns the ELM when it was translated from this exact CQL', () => {
    expect(freshPrecompiledElm(CQL, ELM, CQL)).toBe(ELM)
  })

  it('returns undefined when the CQL differs from the ELM source (stale ELM)', () => {
    const edited = CQL + '\ndefine "B": false'
    // This is the critical safety case: user edited after translating. Sending the
    // stale ELM would execute old logic → wrong clinical results.
    expect(freshPrecompiledElm(edited, ELM, CQL)).toBeUndefined()
  })

  it('returns undefined when there is no ELM', () => {
    expect(freshPrecompiledElm(CQL, null, CQL)).toBeUndefined()
    expect(freshPrecompiledElm(CQL, undefined, CQL)).toBeUndefined()
  })

  it('returns undefined when there is no recorded ELM source', () => {
    expect(freshPrecompiledElm(CQL, ELM, null)).toBeUndefined()
    expect(freshPrecompiledElm(CQL, ELM, undefined)).toBeUndefined()
  })

  it('re-enables reuse when the text returns to a previously-translated value', () => {
    // elmSourceCql is not cleared on edit; equality is what matters, so editing
    // away and back to the translated text lets the optimization apply again.
    expect(freshPrecompiledElm(CQL, ELM, CQL)).toBe(ELM)
  })

  it('does not match on whitespace-only differences (exact equality)', () => {
    expect(freshPrecompiledElm(CQL + ' ', ELM, CQL)).toBeUndefined()
  })
})
