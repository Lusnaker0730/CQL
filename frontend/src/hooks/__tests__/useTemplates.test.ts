import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useTemplates } from '../useTemplates'

vi.mock('../../api', () => ({
  authoringApi: {
    getTemplates: vi.fn(),
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

describe('useTemplates', () => {
  it('should return templates on success', async () => {
    const templates = [{ id: 1, name: 'Template A' }]
    vi.mocked(authoringApi.getTemplates).mockResolvedValue(templates)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTemplates(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(templates)
  })

  it('should start in loading state', () => {
    vi.mocked(authoringApi.getTemplates).mockReturnValue(new Promise(() => {}))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTemplates(), { wrapper })

    expect(result.current.isLoading).toBe(true)
  })

  it('should set error on failure', async () => {
    vi.mocked(authoringApi.getTemplates).mockRejectedValue(new Error('Network error'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTemplates(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('should call authoringApi.getTemplates', async () => {
    vi.mocked(authoringApi.getTemplates).mockResolvedValue([])
    const { wrapper } = createWrapper()

    renderHook(() => useTemplates(), { wrapper })

    await waitFor(() => expect(authoringApi.getTemplates).toHaveBeenCalled())
  })
})
