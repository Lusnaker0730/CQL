import { describe, it, expect } from 'vitest'
import { extractCqlName, parseCqlDefineBlocks, findQuotedReferences } from '../cqlNames'

describe('cqlNames', () => {
  describe('extractCqlName', () => {
    it('extracts name from a standard quoted CQL identifier', () => {
      expect(extractCqlName('"Diabetes": \'http://example.org/vs/diabetes\'')).toBe('Diabetes')
    })

    it('returns raw input when no quoted prefix present', () => {
      expect(extractCqlName('UnquotedName')).toBe('UnquotedName')
    })

    it('handles empty quoted name', () => {
      // Regex requires at least one char between quotes; empty returns the raw input
      expect(extractCqlName('"": 1')).toBe('"": 1')
    })

    it('stops at first closing quote (ignores later quotes)', () => {
      expect(extractCqlName('"First" more "Second"')).toBe('First')
    })

    it('handles name with spaces', () => {
      expect(extractCqlName('"Hemoglobin A1c": \'17856-6\'')).toBe('Hemoglobin A1c')
    })
  })

  describe('parseCqlDefineBlocks', () => {
    it('parses single define block', () => {
      const cql = 'define "Sum": 2 + 3\n'
      const blocks = parseCqlDefineBlocks(cql)
      expect(blocks).toHaveLength(1)
      expect(blocks[0].name).toBe('Sum')
      expect(blocks[0].body.trim()).toBe('2 + 3')
    })

    it('parses multiple define blocks', () => {
      const cql = [
        'define "First": 1',
        'define "Second": 2',
        'define "Third": 3',
      ].join('\n')
      const blocks = parseCqlDefineBlocks(cql)
      expect(blocks.map((b) => b.name)).toEqual(['First', 'Second', 'Third'])
    })

    it('handles multi-line bodies', () => {
      const cql = [
        'define "Multi":',
        '  [Observation] O',
        '    where O.status = \'final\'',
        'define "Next": 1',
      ].join('\n')
      const blocks = parseCqlDefineBlocks(cql)
      expect(blocks).toHaveLength(2)
      expect(blocks[0].name).toBe('Multi')
      expect(blocks[0].body).toContain('[Observation]')
      expect(blocks[0].body).toContain("O.status = 'final'")
    })

    it('returns empty array when no defines', () => {
      expect(parseCqlDefineBlocks('library Foo version \'1.0\'\n')).toEqual([])
    })

    it('is safe when called repeatedly (regex state reset)', () => {
      const cql = 'define "A": 1\ndefine "B": 2\n'
      const first = parseCqlDefineBlocks(cql)
      const second = parseCqlDefineBlocks(cql)
      expect(first.map((b) => b.name)).toEqual(['A', 'B'])
      expect(second.map((b) => b.name)).toEqual(['A', 'B'])
    })
  })

  describe('findQuotedReferences', () => {
    const known = new Set(['Known1', 'Known2', 'Known3'])

    it('finds references that match known names', () => {
      const body = '"Known1" and "Known2"'
      expect(findQuotedReferences(body, known)).toEqual(['Known1', 'Known2'])
    })

    it('filters out unknown names', () => {
      const body = '"Known1" and "NotKnown" and "Known2"'
      expect(findQuotedReferences(body, known)).toEqual(['Known1', 'Known2'])
    })

    it('deduplicates repeated references', () => {
      const body = '"Known1" and "Known1" or "Known1"'
      expect(findQuotedReferences(body, known)).toEqual(['Known1'])
    })

    it('excludes self-reference when excludeName given', () => {
      const body = '"Known1" references "Known2"'
      expect(findQuotedReferences(body, known, 'Known1')).toEqual(['Known2'])
    })

    it('returns empty array when body has no refs', () => {
      expect(findQuotedReferences('no quoted refs here', known)).toEqual([])
    })

    it('resets regex state between calls', () => {
      const body = '"Known1" "Known2"'
      const first = findQuotedReferences(body, known)
      const second = findQuotedReferences(body, known)
      expect(first).toEqual(second)
    })
  })
})
