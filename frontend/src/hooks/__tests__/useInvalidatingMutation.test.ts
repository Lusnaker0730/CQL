import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useInvalidatingMutation } from '../useInvalidatingMutation'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return {
    queryClient,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children),
  }
}

describe('useInvalidatingMutation', () => {
  it('should invalidate queries on successful mutation', async () => {
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const mutationFn = vi.fn().mockResolvedValue({ id: 1 })

    const { result } = renderHook(
      () => useInvalidatingMutation(mutationFn, ['libraries']),
      { wrapper },
    )

    result.current.mutate('test-data')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['libraries'] })
  })

  it('should fire onError callback on failure', async () => {
    const { wrapper } = createWrapper()
    const error = new Error('Save failed')
    const mutationFn = vi.fn().mockRejectedValue(error)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useInvalidatingMutation(mutationFn, ['libraries'], { onError }),
      { wrapper },
    )

    result.current.mutate('test-data')

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError.mock.calls[0][0]).toBe(error)
    expect(onError.mock.calls[0][1]).toBe('test-data')
  })

  it('should work without options parameter', async () => {
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const mutationFn = vi.fn().mockResolvedValue('ok')

    const { result } = renderHook(
      () => useInvalidatingMutation(mutationFn, ['items']),
      { wrapper },
    )

    result.current.mutate(undefined)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['items'] })
  })

  it('should pass error and variables to onError callback', async () => {
    const { wrapper } = createWrapper()
    const error = new Error('Network error')
    const mutationFn = vi.fn().mockRejectedValue(error)
    const onError = vi.fn()

    const { result } = renderHook(
      () => useInvalidatingMutation(
        mutationFn,
        ['measures'],
        { onError },
      ),
      { wrapper },
    )

    const variables = { id: 5, name: 'Test' }
    result.current.mutate(variables)

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError.mock.calls[0][0]).toBe(error)
    expect(onError.mock.calls[0][1]).toEqual(variables)
  })
})
