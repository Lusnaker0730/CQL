import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useImportCql, useQueryBuilderResources, useQueryBuilderOperators } from '../useCqlImport'

vi.mock('../../api', () => ({
  authoringApi: {
    importCql: vi.fn(),
    getQueryBuilderResources: vi.fn(),
    getQueryBuilderOperators: vi.fn(),
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

describe('useImportCql', () => {
  it('should call importCql with CQL string', async () => {
    vi.mocked(authoringApi.importCql).mockResolvedValue({ id: 1 })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useImportCql(), { wrapper })
    result.current.mutate('library Test version \'1.0\'')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.importCql).toHaveBeenCalledWith('library Test version \'1.0\'')
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.importCql).mockRejectedValue(new Error('parse error'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useImportCql(), { wrapper })
    result.current.mutate('invalid')

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useQueryBuilderResources', () => {
  it('should return resources on success', async () => {
    const resources = ['Patient', 'Observation']
    vi.mocked(authoringApi.getQueryBuilderResources).mockResolvedValue(resources)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useQueryBuilderResources(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(resources)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.getQueryBuilderResources).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useQueryBuilderResources(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useQueryBuilderOperators', () => {
  it('should return operators on success', async () => {
    const ops = [{ name: 'equals', label: '=' }]
    vi.mocked(authoringApi.getQueryBuilderOperators).mockResolvedValue(ops)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useQueryBuilderOperators('string'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(ops)
  })

  it('should pass type parameter to API', async () => {
    vi.mocked(authoringApi.getQueryBuilderOperators).mockResolvedValue([])
    const { wrapper } = createWrapper()

    renderHook(() => useQueryBuilderOperators('integer'), { wrapper })

    await waitFor(() => expect(authoringApi.getQueryBuilderOperators).toHaveBeenCalledWith('integer'))
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.getQueryBuilderOperators).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useQueryBuilderOperators(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
