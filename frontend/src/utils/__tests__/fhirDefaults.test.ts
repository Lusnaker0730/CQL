import { describe, it, expect } from 'vitest'
import { getDefaultValue } from '../fhirDefaults'
import type { ElementMetadata } from '../../types'

function makeElement(type: string, overrides: Partial<ElementMetadata> = {}): ElementMetadata {
  return {
    name: 'x',
    path: 'X.x',
    type,
    isArray: false,
    isRequired: false,
    min: 0,
    max: '1',
    isChoiceType: false,
    choiceTypes: [],
    bindingStrength: null,
    bindingValueSetUrl: null,
    bindingCodeSystemUrl: null,
    boundCodes: [],
    children: [],
    description: null,
    referenceTargets: [],
    ...overrides,
  }
}

describe('getDefaultValue', () => {
  it('returns false for boolean', () => {
    expect(getDefaultValue(makeElement('boolean'))).toBe(false)
  })

  it('returns a coding-shaped default for CodeableConcept', () => {
    expect(getDefaultValue(makeElement('CodeableConcept'))).toEqual({
      coding: [{ system: '', code: '', display: '' }],
    })
  })

  it('returns a flat coding default for Coding', () => {
    expect(getDefaultValue(makeElement('Coding'))).toEqual({ system: '', code: '', display: '' })
  })

  it('returns a Period default with empty start/end', () => {
    expect(getDefaultValue(makeElement('Period'))).toEqual({ start: '', end: '' })
  })

  it.each(['Quantity', 'SimpleQuantity', 'Age', 'Duration', 'Distance', 'Count'])(
    'returns a Quantity-shaped default for %s',
    (type) => {
      expect(getDefaultValue(makeElement(type))).toEqual({ value: 0, unit: '' })
    },
  )

  it('returns a Reference default', () => {
    expect(getDefaultValue(makeElement('Reference'))).toEqual({ reference: '' })
  })

  it('returns an Identifier default', () => {
    expect(getDefaultValue(makeElement('Identifier'))).toEqual({ system: '', value: '' })
  })

  it('returns a HumanName default with empty given array', () => {
    expect(getDefaultValue(makeElement('HumanName'))).toEqual({ family: '', given: [] })
  })

  it('returns an empty object for complex types with children', () => {
    const el = makeElement('BackboneElement', {
      children: [makeElement('string', { name: 'note' })],
    })
    expect(getDefaultValue(el)).toEqual({})
  })

  it('returns an empty string for unknown primitive types', () => {
    expect(getDefaultValue(makeElement('string'))).toBe('')
    expect(getDefaultValue(makeElement('uri'))).toBe('')
    expect(getDefaultValue(makeElement('totallyUnknownType'))).toBe('')
  })

  it('does not throw when children is undefined or empty', () => {
    // Regression: previous `element.children?.length > 0` would silently
    // evaluate to false (undefined > 0 === false) — this verifies the
    // safer `?? 0` fallback still routes to the primitive branch.
    expect(getDefaultValue(makeElement('weird', { children: [] }))).toBe('')
    const el = makeElement('weird')
    // @ts-expect-error -- intentionally drop children to mimic stale metadata
    delete el.children
    expect(getDefaultValue(el)).toBe('')
  })
})
