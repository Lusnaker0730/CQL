import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useExternalCqlList, useUploadExternalCql, useDeleteExternalCql } from '../useExternalCql'

vi.mock('../../api', () => ({
  authoringApi: {
    listExternalCql: vi.fn(),
    uploadExternalCql: vi.fn(),
    deleteExternalCql: vi.fn(),
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

describe('useExternalCqlList', () => {
  it('should fetch external CQL list for artifact', async () => {
    const list = [{ id: 1, name: 'FHIRHelpers' }]
    vi.mocked(authoringApi.listExternalCql).mockResolvedValue(list)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useExternalCqlList(42), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(list)
    expect(authoringApi.listExternalCql).toHaveBeenCalledWith(42)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.listExternalCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useExternalCqlList(1), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useUploadExternalCql', () => {
  it('should upload and invalidate queries on success', async () => {
    vi.mocked(authoringApi.uploadExternalCql).mockResolvedValue({ id: 2 })
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUploadExternalCql(10), { wrapper })
    const file = new File(['content'], 'test.cql')
    result.current.mutate(file)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.uploadExternalCql).toHaveBeenCalledWith(10, file)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['external-cql', 10] })
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.uploadExternalCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUploadExternalCql(10), { wrapper })
    result.current.mutate(new File([''], 'test.cql'))

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useDeleteExternalCql', () => {
  it('should delete and invalidate queries on success', async () => {
    vi.mocked(authoringApi.deleteExternalCql).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDeleteExternalCql(10), { wrapper })
    result.current.mutate(5)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.deleteExternalCql).toHaveBeenCalledWith(10, 5)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['external-cql', 10] })
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.deleteExternalCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useDeleteExternalCql(10), { wrapper })
    result.current.mutate(5)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
