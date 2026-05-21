import { describe, it, expect } from 'vitest'
import { isPlainObject, asObject, asStringArray } from '../fhirGuards'

describe('isPlainObject', () => {
  it('accepts plain objects', () => {
    expect(isPlainObject({})).toBe(true)
    expect(isPlainObject({ a: 1 })).toBe(true)
  })

  it('rejects arrays', () => {
    expect(isPlainObject([])).toBe(false)
    expect(isPlainObject(['a'])).toBe(false)
  })

  it('rejects null, undefined, and primitives', () => {
    expect(isPlainObject(null)).toBe(false)
    expect(isPlainObject(undefined)).toBe(false)
    expect(isPlainObject('hello')).toBe(false)
    expect(isPlainObject(42)).toBe(false)
    expect(isPlainObject(true)).toBe(false)
  })
})

describe('asObject', () => {
  it('returns the value when it is a plain object', () => {
    const obj = { code: 'abc' }
    expect(asObject(obj)).toBe(obj)
  })

  it('returns an empty object for null/undefined/array/primitive', () => {
    expect(asObject(null)).toEqual({})
    expect(asObject(undefined)).toEqual({})
    expect(asObject([1, 2])).toEqual({})
    expect(asObject('string')).toEqual({})
  })
})

describe('asStringArray', () => {
  it('keeps only string elements', () => {
    expect(asStringArray(['a', 1, 'b', null, 'c'])).toEqual(['a', 'b', 'c'])
  })

  it('returns [] for non-arrays', () => {
    expect(asStringArray(null)).toEqual([])
    expect(asStringArray(undefined)).toEqual([])
    expect(asStringArray('a')).toEqual([])
    expect(asStringArray({ 0: 'a' })).toEqual([])
  })
})
