import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { createElement } from 'react'
import authReducer from '../../store/authSlice'
import { useFavorites, useRecent, useAddFavorite, useRemoveFavorite, useAddRecent, useClearRecent } from '../useLibraryPrefs'

vi.mock('../../api', () => ({
  userPrefsApi: {
    getFavorites: vi.fn(),
    getRecent: vi.fn(),
    addFavorite: vi.fn(),
    removeFavorite: vi.fn(),
    addRecent: vi.fn(),
    clearRecent: vi.fn(),
  },
}))

import { userPrefsApi } from '../../api'

function createWrapper(preloadedState?: Record<string, unknown>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState,
  })
  return {
    queryClient,
    store,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(
        Provider,
        { store },
        createElement(QueryClientProvider, { client: queryClient }, children),
      ),
  }
}

beforeEach(() => { vi.clearAllMocks() })

describe('useFavorites', () => {
  it('should fetch favorites when authenticated', async () => {
    const favorites = [{ id: 1, libraryId: 'lib-1' }]
    vi.mocked(userPrefsApi.getFavorites).mockResolvedValue(favorites)
    const { wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })

    const { result } = renderHook(() => useFavorites(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(favorites)
  })

  it('should not fetch favorites when not authenticated', () => {
    const { wrapper } = createWrapper({
      auth: { user: null, token: null, isAuthenticated: false, loading: false },
    })

    const { result } = renderHook(() => useFavorites(), { wrapper })

    expect(result.current.isFetching).toBe(false)
    expect(userPrefsApi.getFavorites).not.toHaveBeenCalled()
  })
})

describe('useRecent', () => {
  it('should fetch recent when authenticated', async () => {
    const recent = [{ id: 1, libraryId: 'lib-1', accessedAt: '2024-01-01' }]
    vi.mocked(userPrefsApi.getRecent).mockResolvedValue(recent)
    const { wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })

    const { result } = renderHook(() => useRecent(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(recent)
  })

  it('should not fetch recent when not authenticated', () => {
    const { wrapper } = createWrapper({
      auth: { user: null, token: null, isAuthenticated: false, loading: false },
    })

    const { result } = renderHook(() => useRecent(), { wrapper })

    expect(result.current.isFetching).toBe(false)
  })
})

describe('useAddFavorite', () => {
  it('should add favorite and invalidate queries', async () => {
    vi.mocked(userPrefsApi.addFavorite).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useAddFavorite(), { wrapper })
    result.current.mutate('lib-1')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(userPrefsApi.addFavorite).toHaveBeenCalledWith('lib-1')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['favorites'] })
  })
})

describe('useRemoveFavorite', () => {
  it('should remove favorite and invalidate queries', async () => {
    vi.mocked(userPrefsApi.removeFavorite).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useRemoveFavorite(), { wrapper })
    result.current.mutate('lib-2')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(userPrefsApi.removeFavorite).toHaveBeenCalledWith('lib-2')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['favorites'] })
  })
})

describe('useAddRecent', () => {
  it('should add recent and invalidate queries', async () => {
    vi.mocked(userPrefsApi.addRecent).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useAddRecent(), { wrapper })
    result.current.mutate('lib-3')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(userPrefsApi.addRecent).toHaveBeenCalledWith('lib-3')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recent'] })
  })
})

describe('useClearRecent', () => {
  it('should clear recent and invalidate queries', async () => {
    vi.mocked(userPrefsApi.clearRecent).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper({
      auth: { user: { username: 'test', role: 'USER' }, token: 'tok', isAuthenticated: true, loading: false },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useClearRecent(), { wrapper })
    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(userPrefsApi.clearRecent).toHaveBeenCalled()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recent'] })
  })
})
