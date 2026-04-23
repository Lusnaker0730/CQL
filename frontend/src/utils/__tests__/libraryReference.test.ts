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
