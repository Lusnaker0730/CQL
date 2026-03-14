import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useArtifacts, useArtifact, useCreateArtifact, useUpdateArtifact, useDeleteArtifact, useDuplicateArtifact } from '../useAuthoring'

vi.mock('../../api', () => ({
  authoringApi: {
    listArtifacts: vi.fn(),
    getArtifact: vi.fn(),
    createArtifact: vi.fn(),
    updateArtifact: vi.fn(),
    deleteArtifact: vi.fn(),
    duplicateArtifact: vi.fn(),
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

describe('useArtifacts', () => {
  it('should return artifacts list on success', async () => {
    const artifacts = [{ id: 1, name: 'Artifact A' }]
    vi.mocked(authoringApi.listArtifacts).mockResolvedValue(artifacts)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useArtifacts(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(artifacts)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.listArtifacts).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useArtifacts(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useArtifact', () => {
  it('should fetch single artifact when id is provided', async () => {
    const artifact = { id: 5, name: 'Test' }
    vi.mocked(authoringApi.getArtifact).mockResolvedValue(artifact)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useArtifact(5), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(artifact)
    expect(authoringApi.getArtifact).toHaveBeenCalledWith(5)
  })

  it('should not fetch when id is undefined', () => {
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useArtifact(undefined), { wrapper })

    expect(result.current.isFetching).toBe(false)
  })
})

describe('useCreateArtifact', () => {
  it('should create artifact and invalidate queries', async () => {
    const created = { id: 1, name: 'New' }
    vi.mocked(authoringApi.createArtifact).mockResolvedValue(created)
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateArtifact(), { wrapper })
    result.current.mutate({ name: 'New', templateId: 1 } as never)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(created)
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.createArtifact).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useCreateArtifact(), { wrapper })
    result.current.mutate({ name: 'New' } as never)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useUpdateArtifact', () => {
  it('should update artifact and invalidate queries', async () => {
    vi.mocked(authoringApi.updateArtifact).mockResolvedValue({ id: 1, name: 'Updated' })
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateArtifact(), { wrapper })
    result.current.mutate({ id: 1, request: { name: 'Updated' } as never })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.updateArtifact).toHaveBeenCalledWith(1, { name: 'Updated' })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['authoring', 'artifacts'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['authoring', 'artifact', 1] })
  })
})

describe('useDeleteArtifact', () => {
  it('should delete artifact and invalidate queries', async () => {
    vi.mocked(authoringApi.deleteArtifact).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDeleteArtifact(), { wrapper })
    result.current.mutate(5)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.deleteArtifact).toHaveBeenCalledWith(5)
    expect(invalidateSpy).toHaveBeenCalled()
  })
})

describe('useDuplicateArtifact', () => {
  it('should duplicate artifact and invalidate queries', async () => {
    vi.mocked(authoringApi.duplicateArtifact).mockResolvedValue({ id: 2 })
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDuplicateArtifact(), { wrapper })
    result.current.mutate(3)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.duplicateArtifact).toHaveBeenCalledWith(3)
    expect(invalidateSpy).toHaveBeenCalled()
  })
})
