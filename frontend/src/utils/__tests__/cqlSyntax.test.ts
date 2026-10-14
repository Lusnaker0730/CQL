import { describe, it, expect } from 'vitest'
import { parseIncludes, provideCqlCompletions, resolveIncludedLibrary, updateLibraryCatalog } from '../cqlSyntax'
import type { LibraryInfo } from '../cqlSyntax'

// PAT-258 — Monaco completion of an included library's defines / functions after `Alias.`, and the library
// catalog as module state (the provider is registered once; the libraries must not be captured by value).

const monaco = {
  languages: {
    CompletionItemKind: { Property: 9, Module: 8, Variable: 4, Function: 1, Field: 3, Enum: 12, EnumMember: 16, Constant: 14 },
  },
} as unknown as typeof import('monaco-editor')

/** A model whose cursor sits at the end of the last line of `text`; the word range mimics Monaco's `\w+` word. */
function modelAt(text: string) {
  const lines = text.split('\n')
  const lineNumber = lines.length
  const line = lines[lineNumber - 1]
  const column = line.length + 1
  let start = column
  while (start > 1 && /\w/.test(line[start - 2])) start--
  const model = {
    getValue: () => text,
    getLineContent: (n: number) => lines[n - 1],
    getWordUntilPosition: () => ({ word: line.slice(start - 1, column - 1), startColumn: start, endColumn: column }),
  } as unknown as import('monaco-editor').editor.ITextModel
  return { model, position: { lineNumber, column } as import('monaco-editor').Position }
}

const catalog: LibraryInfo[] = [
  { name: 'Common', version: '1.0.0', expressions: ['Has Diabetes', 'Encounters', 'Age At'], functions: ['Age At'] },
  { name: 'Common', version: '2.0.0', expressions: ['Has Diabetes', 'Has Hypertension'], functions: [] },
  { name: 'Common', version: '10.0.0', expressions: ['Ten'], functions: [] },
  { name: 'Other Lib', version: '1.0.0', expressions: ['Other Define'], functions: [] },
]

const complete = (text: string, libs?: LibraryInfo[]) => {
  const { model, position } = modelAt(text)
  return provideCqlCompletions(monaco, model, position, libs).suggestions
}
const labels = (items: Array<{ label: unknown }>) => items.map((i) => (typeof i.label === 'string' ? i.label : String(i.label)))

const HEADER = "library Test version '1.0.0'\nusing FHIR version '4.0.1'\n"

describe('parseIncludes', () => {
  it('reads bare, quoted, versioned and alias-less includes', () => {
    const refs = parseIncludes(
      HEADER +
        "include Common version '1.0.0' called C\n" +
        "include \"Other Lib\" version '2.0' called \"O\"\n" +
        "include FHIRHelpers version '4.0.1'\n" +
        'include Plain\n',
    )
    expect(refs).toEqual([
      { name: 'Common', version: '1.0.0', alias: 'C' },
      { name: 'Other Lib', version: '2.0', alias: 'O' },
      { name: 'FHIRHelpers', version: '4.0.1', alias: 'FHIRHelpers' },
      { name: 'Plain', version: undefined, alias: 'Plain' },
    ])
  })
})

describe('resolveIncludedLibrary', () => {
  it('takes the pinned version when the catalog has it, otherwise the newest (numeric order)', () => {
    expect(resolveIncludedLibrary({ name: 'Common', version: '1.0.0', alias: 'C' }, catalog)?.version).toBe('1.0.0')
    expect(resolveIncludedLibrary({ name: 'Common', version: '3.0.0', alias: 'C' }, catalog)?.version).toBe('10.0.0')
    expect(resolveIncludedLibrary({ name: 'Common', alias: 'C' }, catalog)?.version).toBe('10.0.0')
    expect(resolveIncludedLibrary({ name: 'Nope', alias: 'N' }, catalog)).toBeUndefined()
  })
})

describe('provideCqlCompletions — include alias members', () => {
  it('after `Alias.` offers only that library version\'s defines and functions', () => {
    const items = complete(HEADER + "include Common version '1.0.0' called C\ndefine X: C.", catalog)
    expect(labels(items)).toEqual(['"Has Diabetes"', '"Encounters"', '"Age At"()'])
    const fn = items.find((i) => i.label === '"Age At"()')
    expect(fn?.insertText).toBe('"Age At"(${1})')
    expect(fn?.detail).toBe('Common v1.0.0')
    expect(items[0].insertText).toBe('"Has Diabetes"')
  })

  it('accepts a quoted alias and an alias-less include (library name as alias)', () => {
    expect(labels(complete(HEADER + "include \"Other Lib\" version '1.0.0' called \"O\"\ndefine X: \"O\".", catalog)))
      .toEqual(['"Other Define"'])
    expect(labels(complete(HEADER + "include \"Other Lib\" version '1.0.0'\ndefine X: \"Other Lib\".", catalog)))
      .toEqual(['"Other Define"'])
  })

  it('an unpinned include resolves to the newest version', () => {
    expect(labels(complete(HEADER + 'include Common called C\ndefine X: C.', catalog))).toEqual(['"Ten"'])
  })

  it('a partial member with an opening quote keeps the members and widens the range over the quote', () => {
    const items = complete(HEADER + "include Common version '1.0.0' called C\ndefine X: C.\"Ha", catalog)
    expect(labels(items)).toContain('"Has Diabetes"')
    // the line is `define X: C."Ha` — the quote sits one column before the word `Ha`
    const line = 'define X: C."Ha'
    expect(items[0].range.startColumn).toBe(line.indexOf('"') + 1)
  })

  it('a FHIR resource type still gets its properties, and an unknown alias falls back to the general list', () => {
    expect(labels(complete(HEADER + 'define X: Patient.', catalog))).toContain('birthDate')
    const general = labels(complete(HEADER + "include Common version '1.0.0' called C\ndefine X: Nope.", catalog))
    expect(general).toContain('define')
    expect(general).not.toContain('"Has Diabetes"')
  })
})

describe('library catalog module state', () => {
  it('updateLibraryCatalog feeds both the `include ` list and the alias members without re-registering', () => {
    updateLibraryCatalog([{ name: 'First', version: '1.0.0', expressions: ['A'] }])
    expect(labels(complete(HEADER + 'include '))).toEqual(['First'])

    updateLibraryCatalog([{ name: 'Second', version: '2.0.0', expressions: ['B'] }])
    expect(labels(complete(HEADER + 'include '))).toEqual(['Second'])
    expect(labels(complete(HEADER + "include Second version '2.0.0' called S\ndefine X: S."))).toEqual(['"B"'])
    updateLibraryCatalog([])
  })
})
