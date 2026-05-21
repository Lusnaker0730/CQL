import { describe, it, expect } from 'vitest'
import {
  buildChoiceFieldName,
  detectChoiceType,
  listChoiceFieldNames,
} from '../fhirChoice'

describe('buildChoiceFieldName', () => {
  it('capitalizes the first char of the type and concatenates', () => {
    expect(buildChoiceFieldName('value', 'Quantity')).toBe('valueQuantity')
    expect(buildChoiceFieldName('value', 'CodeableConcept')).toBe('valueCodeableConcept')
    expect(buildChoiceFieldName('value', 'string')).toBe('valueString')
    expect(buildChoiceFieldName('onset', 'dateTime')).toBe('onsetDateTime')
  })

  it('returns the base name unchanged when type is empty', () => {
    expect(buildChoiceFieldName('value', '')).toBe('value')
  })
})

describe('detectChoiceType', () => {
  it('returns the type whose flattened key is present in data', () => {
    const data = { valueQuantity: { value: 5, unit: 'mg' } }
    expect(detectChoiceType('value', ['Quantity', 'string', 'CodeableConcept'], data)).toBe('Quantity')
  })

  it('returns undefined when no variant is present', () => {
    expect(detectChoiceType('value', ['Quantity', 'string'], { other: 1 })).toBeUndefined()
  })

  it('returns the first matching variant in iteration order', () => {
    const data = { valueQuantity: 1, valueString: 'x' }
    expect(detectChoiceType('value', ['Quantity', 'string'], data)).toBe('Quantity')
    expect(detectChoiceType('value', ['string', 'Quantity'], data)).toBe('string')
  })
})

describe('listChoiceFieldNames', () => {
  it('returns the set of all flattened keys for the choice element', () => {
    const names = listChoiceFieldNames('value', ['Quantity', 'string', 'CodeableConcept'])
    expect(names).toEqual(new Set(['valueQuantity', 'valueString', 'valueCodeableConcept']))
  })

  it('returns an empty set when no choice types are given', () => {
    expect(listChoiceFieldNames('value', [])).toEqual(new Set())
  })

  it('does NOT include sibling keys that merely share the prefix', () => {
    // Regression: previous prefix+caps logic would have flagged "valueSet"
    // as a choice variant of "value" — listChoiceFieldNames must not.
    const names = listChoiceFieldNames('value', ['Quantity', 'string'])
    expect(names.has('valueSet')).toBe(false)
    expect(names.has('valueBoolean')).toBe(false)
  })
})

describe('choice-type cleanup pattern (regression for ResourceForm)', () => {
  // ResourceForm.handleFieldChange uses listChoiceFieldNames + delete to wipe
  // stale choice variants when the user swaps types. The previous prefix+caps
  // implementation also wiped sibling keys like `valueSet` that share the
  // prefix but aren't choice variants. This test pins the new, exact behavior.
  function applyChoiceCleanup(
    data: Record<string, unknown>,
    baseName: string,
    choiceTypes: readonly string[],
    newVariantKey: string,
    newValue: unknown,
  ): Record<string, unknown> {
    const next = { ...data }
    for (const variant of listChoiceFieldNames(baseName, choiceTypes)) {
      delete next[variant]
    }
    delete next[baseName]
    next[newVariantKey] = newValue
    return next
  }

  it('wipes the previous variant when swapping Quantity → CodeableConcept', () => {
    const before = {
      valueQuantity: { value: 5, unit: 'mg' },
      note: 'unchanged',
    }
    const after = applyChoiceCleanup(
      before,
      'value',
      ['Quantity', 'CodeableConcept', 'string'],
      'valueCodeableConcept',
      { coding: [{ code: 'x' }] },
    )
    expect(after.valueQuantity).toBeUndefined()
    expect(after.valueCodeableConcept).toEqual({ coding: [{ code: 'x' }] })
    expect(after.note).toBe('unchanged')
  })

  it('preserves prefix-sharing sibling keys (the high-severity bug)', () => {
    // valueSet is a separate, valid sibling field — it must survive a value[x] swap.
    const before = {
      valueQuantity: { value: 5 },
      valueSet: 'http://example.org/vs',
    }
    const after = applyChoiceCleanup(
      before,
      'value',
      ['Quantity', 'string'],
      'valueString',
      'hello',
    )
    expect(after.valueSet).toBe('http://example.org/vs')
    expect(after.valueQuantity).toBeUndefined()
    expect(after.valueString).toBe('hello')
  })
})
