import { describe, it, expect } from 'vitest'
import { bundleToPrefetch, prefetchToBundle } from '../bundlePrefetchConverter'

describe('bundleToPrefetch', () => {
  it('should extract Patient directly', () => {
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [{ resource: { resourceType: 'Patient', id: 'p1', gender: 'male' } }],
    })

    const result = bundleToPrefetch(bundle)
    expect(result.patient).toEqual({ resourceType: 'Patient', id: 'p1', gender: 'male' })
  })

  it('should group Observations into searchset bundle', () => {
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [
        { resource: { resourceType: 'Observation', id: 'o1' } },
        { resource: { resourceType: 'Observation', id: 'o2' } },
      ],
    })

    const result = bundleToPrefetch(bundle)
    expect(result.observations).toEqual({
      resourceType: 'Bundle',
      type: 'searchset',
      entry: [
        { resource: { resourceType: 'Observation', id: 'o1' } },
        { resource: { resourceType: 'Observation', id: 'o2' } },
      ],
    })
  })

  it('should handle mixed Patient and Observations', () => {
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [
        { resource: { resourceType: 'Patient', id: 'p1' } },
        { resource: { resourceType: 'Observation', id: 'o1' } },
        { resource: { resourceType: 'Condition', id: 'c1' } },
      ],
    })

    const result = bundleToPrefetch(bundle)
    expect(result.patient).toBeDefined()
    expect(result.observations).toBeDefined()
    expect(result.conditions).toBeDefined()
  })

  it('should return empty object for empty bundle', () => {
    const bundle = JSON.stringify({ resourceType: 'Bundle', type: 'collection', entry: [] })
    const result = bundleToPrefetch(bundle)
    expect(result).toEqual({})
  })

  it('should skip entries without resourceType', () => {
    const bundle = JSON.stringify({
      resourceType: 'Bundle',
      type: 'collection',
      entry: [{ resource: { id: 'no-type' } }],
    })
    const result = bundleToPrefetch(bundle)
    expect(result).toEqual({})
  })
})

describe('prefetchToBundle', () => {
  it('should convert patient to bundle entry', () => {
    const prefetch = { patient: { resourceType: 'Patient', id: 'p1' } }
    const result = JSON.parse(prefetchToBundle(prefetch))

    expect(result.resourceType).toBe('Bundle')
    expect(result.type).toBe('collection')
    expect(result.entry).toHaveLength(1)
    expect(result.entry[0].resource.resourceType).toBe('Patient')
  })

  it('should extract resources from searchset bundles', () => {
    const prefetch = {
      observations: {
        resourceType: 'Bundle',
        type: 'searchset',
        entry: [
          { resource: { resourceType: 'Observation', id: 'o1' } },
          { resource: { resourceType: 'Observation', id: 'o2' } },
        ],
      },
    }
    const result = JSON.parse(prefetchToBundle(prefetch))
    expect(result.entry).toHaveLength(2)
  })

  it('should skip null/non-object values', () => {
    const prefetch = { patient: null, name: 'not-object' } as Record<string, unknown>
    const result = JSON.parse(prefetchToBundle(prefetch))
    expect(result.entry).toHaveLength(0)
  })
})

describe('round-trip integrity', () => {
  it('should preserve data through bundleToPrefetch → prefetchToBundle', () => {
    const original = {
      resourceType: 'Bundle',
      type: 'collection',
      entry: [
        { resource: { resourceType: 'Patient', id: 'p1', gender: 'female' } },
        { resource: { resourceType: 'Observation', id: 'o1', status: 'final' } },
      ],
    }

    const prefetch = bundleToPrefetch(JSON.stringify(original))
    const roundTripped = JSON.parse(prefetchToBundle(prefetch))

    expect(roundTripped.entry).toHaveLength(2)
    const types = roundTripped.entry.map((e: { resource: { resourceType: string } }) => e.resource.resourceType)
    expect(types).toContain('Patient')
    expect(types).toContain('Observation')
  })
})
