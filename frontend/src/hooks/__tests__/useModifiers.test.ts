import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useModifiers } from '../useModifiers'

vi.mock('../../api', () => ({
  authoringApi: {
    getModifiers: vi.fn(),
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

describe('useModifiers', () => {
  it('should return modifiers on success', async () => {
    const modifiers = [{ id: 1, name: 'Modifier A' }]
    vi.mocked(authoringApi.getModifiers).mockResolvedValue(modifiers)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useModifiers(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(modifiers)
  })

  it('should pass inputType parameter to API', async () => {
    vi.mocked(authoringApi.getModifiers).mockResolvedValue([])
    const { wrapper } = createWrapper()

    renderHook(() => useModifiers('boolean'), { wrapper })

    await waitFor(() => expect(authoringApi.getModifiers).toHaveBeenCalledWith('boolean'))
  })

  it('should call API without inputType when not provided', async () => {
    vi.mocked(authoringApi.getModifiers).mockResolvedValue([])
    const { wrapper } = createWrapper()

    renderHook(() => useModifiers(), { wrapper })

    await waitFor(() => expect(authoringApi.getModifiers).toHaveBeenCalledWith(undefined))
  })

  it('should set error on failure', async () => {
    vi.mocked(authoringApi.getModifiers).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useModifiers(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('should start in loading state', () => {
    vi.mocked(authoringApi.getModifiers).mockReturnValue(new Promise(() => {}))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useModifiers(), { wrapper })

    expect(result.current.isLoading).toBe(true)
  })
})
