import { describe, it, expect, vi } from 'vitest'
import { serializeToBundle, parseFromBundle } from '../BundleBuilderContext'
import type { BundleEntry } from '../../types'

describe('serializeToBundle', () => {
  it('produces a FHIR Bundle with id-stripped entries', () => {
    const entries: BundleEntry[] = [
      { id: 'entry-1', resourceType: 'Patient', resourceData: { id: 'pat-1', gender: 'male' } },
    ]
    const json = serializeToBundle(entries)
    const parsed = JSON.parse(json)
    expect(parsed.resourceType).toBe('Bundle')
    expect(parsed.entry).toHaveLength(1)
    expect(parsed.entry[0].resource).toEqual({
      resourceType: 'Patient',
      id: 'pat-1',
      gender: 'male',
    })
  })

  it('falls back to entry.id when resourceData has no id', () => {
    const entries: BundleEntry[] = [
      { id: 'entry-fallback', resourceType: 'Observation', resourceData: { status: 'final' } },
    ]
    const parsed = JSON.parse(serializeToBundle(entries))
    expect(parsed.entry[0].resource.id).toBe('entry-fallback')
  })
})

describe('parseFromBundle', () => {
  it('round-trips a serialized bundle', () => {
    const entries: BundleEntry[] = [
      { id: 'p1', resourceType: 'Patient', resourceData: { id: 'p1', gender: 'female' } },
    ]
    const json = serializeToBundle(entries)
    const restored = parseFromBundle(json)
    expect(restored).toHaveLength(1)
    expect(restored[0].resourceType).toBe('Patient')
    expect(restored[0].resourceData.gender).toBe('female')
  })

  it('PAT-149 regression: malformed JSON returns [] instead of throwing', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    expect(parseFromBundle('not json at all {[}')).toEqual([])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('returns [] when resourceType is not "Bundle"', () => {
    expect(parseFromBundle(JSON.stringify({ resourceType: 'Patient' }))).toEqual([])
  })

  it('returns [] when entry is not an array', () => {
    expect(parseFromBundle(JSON.stringify({ resourceType: 'Bundle', entry: 'oops' }))).toEqual([])
  })

  it('skips bundle entries that have no resource', () => {
    const json = JSON.stringify({
      resourceType: 'Bundle',
      entry: [
        { request: { method: 'GET' } },
        { resource: { resourceType: 'Condition', id: 'c1', code: { text: 'x' } } },
      ],
    })
    const result = parseFromBundle(json)
    expect(result).toHaveLength(1)
    expect(result[0].resourceType).toBe('Condition')
  })

  it('generates an id when resource lacks one', () => {
    const json = JSON.stringify({
      resourceType: 'Bundle',
      entry: [{ resource: { resourceType: 'Encounter', status: 'finished' } }],
    })
    const result = parseFromBundle(json)
    expect(result[0].id).toMatch(/.+/)
  })
})
