import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fhirApi, TERMINOLOGY_DEGRADED_HEADER } from '../fhirApi'

// The axios instance is replaced so no interceptor / network is involved; `get` is looked up lazily.
const get = vi.fn()
vi.mock('../client', () => ({ api: { get: (...args: unknown[]) => get(...args) } }))

const PLATFORM = {
  id: '7',
  url: 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders',
  name: 'HbA1cOrders',
  title: 'HbA1c order codes',
  source: 'platform' as const,
  version: '1.0.0',
  status: 'active',
}

describe('fhirApi.searchValueSets — PAT-275 reads the BUG-154 degradation header', () => {
  beforeEach(() => get.mockReset())

  it('reports terminologyDegraded when the backend answered without the remote terminology server', async () => {
    get.mockResolvedValue({ data: [PLATFORM], headers: { [TERMINOLOGY_DEGRADED_HEADER]: 'true' } })

    const result = await fhirApi.searchValueSets('HbA1c')

    expect(get).toHaveBeenCalledWith('/fhir/ValueSet?title=HbA1c')
    expect(result).toEqual({ valueSets: [PLATFORM], terminologyDegraded: true })
  })

  it('is not degraded when the header is absent', async () => {
    get.mockResolvedValue({ data: [PLATFORM], headers: {} })

    expect(await fhirApi.searchValueSets('HbA1c')).toEqual({ valueSets: [PLATFORM], terminologyDegraded: false })
  })

  it('treats anything but "true" as not degraded', async () => {
    get.mockResolvedValue({ data: [], headers: { [TERMINOLOGY_DEGRADED_HEADER]: 'false' } })

    expect((await fhirApi.searchValueSets('HbA1c')).terminologyDegraded).toBe(false)
  })

  it('encodes the title and omits the query string when there is no title', async () => {
    get.mockResolvedValue({ data: [], headers: {} })

    await fhirApi.searchValueSets('血糖 A1c')
    expect(get).toHaveBeenLastCalledWith(`/fhir/ValueSet?title=${encodeURIComponent('血糖 A1c')}`)

    await fhirApi.searchValueSets()
    expect(get).toHaveBeenLastCalledWith('/fhir/ValueSet')
  })
})
