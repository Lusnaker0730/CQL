import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useTwcoreCatalog, useTwcoreFullCatalog, useTwcoreCodeSystems } from '../useTwcoreCatalog'

vi.mock('../../api', () => ({
  authoringApi: {
    getTwcoreCatalog: vi.fn(),
    getTwcoreCodeSystems: vi.fn(),
  },
}))

import { authoringApi } from '../../api'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    queryClient,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children),
  }
}

describe('useTwcoreCatalog', () => {
  it('should fetch catalog when resourceType is provided', async () => {
    const catalog = [{ name: 'Patient', resourceType: 'Patient', categories: [] }]
    vi.mocked(authoringApi.getTwcoreCatalog).mockResolvedValue(catalog)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreCatalog('Patient'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(catalog)
    expect(authoringApi.getTwcoreCatalog).toHaveBeenCalledWith('Patient')
  })

  it('should not fetch when resourceType is undefined', () => {
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreCatalog(), { wrapper })

    expect(result.current.isFetching).toBe(false)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.getTwcoreCatalog).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreCatalog('Patient'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useTwcoreFullCatalog', () => {
  it('should fetch full catalog', async () => {
    const catalog = [{ name: 'Observation', resourceType: 'Observation', categories: [] }]
    vi.mocked(authoringApi.getTwcoreCatalog).mockResolvedValue(catalog)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreFullCatalog(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(catalog)
    expect(authoringApi.getTwcoreCatalog).toHaveBeenCalledWith()
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.getTwcoreCatalog).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreFullCatalog(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useTwcoreCodeSystems', () => {
  it('should fetch code systems', async () => {
    const systems = [{ id: 1, name: 'LOINC' }]
    vi.mocked(authoringApi.getTwcoreCodeSystems).mockResolvedValue(systems)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreCodeSystems(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(systems)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.getTwcoreCodeSystems).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTwcoreCodeSystems(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
