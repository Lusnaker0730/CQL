import { describe, it, expect } from 'vitest'
import { libraryReferenceToElement } from '../libraryReference'
import type { LibraryDefinitionReference } from '../../components/cql-libraries/LibraryDefinitionPicker'

describe('libraryReferenceToElement', () => {
  const sampleRef: LibraryDefinitionReference = {
    libraryName: 'SharedLogic',
    libraryVersion: '1.2.0',
    definitionName: 'HasDiabetes',
    alias: 'shared',
  }

  it('produces externalCqlElement type with boolean return type', () => {
    const element = libraryReferenceToElement(sampleRef)
    expect(element.type).toBe('externalCqlElement')
    expect(element.returnType).toBe('boolean')
  })

  it('names the element using alias.definitionName format (matches backend reference shape)', () => {
    const element = libraryReferenceToElement(sampleRef)
    // Backend ExpressionCqlEngine emits `"alias"."defName"` when resolving this node;
    // UI shows the same form so authors can spot it in the tree.
    expect(element.name).toBe('shared."HasDiabetes"')
  })

  it('emits four field entries with the exact ids backend expects', () => {
    const element = libraryReferenceToElement(sampleRef)
    const ids = element.fields.map((f) => f.id).sort()
    // These ids are consumed by ExpressionCqlEngine.collectDeclarations — renaming
    // any of them silently breaks CQL include emission on the backend.
    expect(ids).toEqual(['alias', 'definitionName', 'libraryName', 'libraryVersion'])
  })

  it('marks every field as static so end users cannot overwrite library metadata', () => {
    const element = libraryReferenceToElement(sampleRef)
    expect(element.fields.every((f) => f.static === true)).toBe(true)
  })

  // PAT-237 — a function pick becomes a call element: one argument slot per operand, the
  // alias as qualifier, scalar operands starting as typed literals and an Interval<DateTime>
  // as the Measurement Period when the artifact has one.
  it('turns a function reference into an externalCqlFunctionCall with argument slots', () => {
    const element = libraryReferenceToElement({
      ...sampleRef,
      definitionName: 'Controlled',
      kind: 'function',
      resultType: 'Boolean',
      operands: [
        { name: 'observations', type: 'List<FHIR.Observation>' },
        { name: 'period', type: 'Interval<DateTime>' },
        { name: 'threshold', type: 'Decimal' },
      ],
    }, true)

    expect(element.type).toBe('externalCqlFunctionCall')
    expect(element.returnType).toBe('boolean')
    const byId = Object.fromEntries(element.fields.map((f) => [f.id, f]))
    expect(byId.library_name.value).toBe('SharedLogic')
    expect(byId.library_version.value).toBe('1.2.0')
    expect(byId.alias.value).toBe('shared')
    expect(byId.function_name.value).toBe('Controlled')
    expect(byId.function_name.static).toBe(true)
    expect(byId.arguments.type).toBe('functionArguments')
    expect(byId.arguments.value).toEqual([
      { name: 'observations', type: 'List<FHIR.Observation>', mode: 'element' },
      { name: 'period', type: 'Interval<DateTime>', mode: 'measurementPeriod' },
      { name: 'threshold', type: 'Decimal', mode: 'literal', literal_type: 'Decimal', literal_value: '' },
    ])
  })

  it('without a Measurement Period an Interval<DateTime> operand starts as a base-element reference', () => {
    const element = libraryReferenceToElement({
      ...sampleRef, kind: 'function', operands: [{ name: 'period', type: 'Interval<DateTime>' }],
    })
    const args = element.fields.find((f) => f.id === 'arguments')!.value as Array<{ mode: string }>
    expect(args[0].mode).toBe('element')
  })

  it('preserves reference values verbatim on field values', () => {
    const element = libraryReferenceToElement(sampleRef)
    const byId = Object.fromEntries(element.fields.map((f) => [f.id, f.value]))
    expect(byId.libraryName).toBe('SharedLogic')
    expect(byId.libraryVersion).toBe('1.2.0')
    expect(byId.definitionName).toBe('HasDiabetes')
    expect(byId.alias).toBe('shared')
  })

  it('generates a unique uniqueId per invocation', () => {
    const a = libraryReferenceToElement(sampleRef)
    const b = libraryReferenceToElement(sampleRef)
    expect(a.uniqueId).not.toBe(b.uniqueId)
    // uniqueId must start with "libref-" so UI debug surfaces can recognize these
    expect(a.uniqueId).toMatch(/^libref-/)
  })

  it('initializes empty modifiers array', () => {
    const element = libraryReferenceToElement(sampleRef)
    // Modifiers are optional on ElementInstance, but the builder treats missing
    // vs empty identically; we emit [] for predictability in tree serialization.
    expect(element.modifiers).toEqual([])
  })
})
