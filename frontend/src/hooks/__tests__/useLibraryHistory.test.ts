import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import { createElement } from 'react'
import { useLibraryHistory } from '../useLibraryHistory'
import { LibraryHistoryContext, type LibraryHistoryContextType } from '../../contexts/LibraryHistoryContext'

function createWrapper(value: LibraryHistoryContextType) {
  return ({ children }: { children: React.ReactNode }) =>
    createElement(LibraryHistoryContext.Provider, { value }, children)
}

describe('useLibraryHistory', () => {
  it('should return context value when used inside provider', () => {
    const mockContext: LibraryHistoryContextType = {
      recent: [],
      favorites: new Set(),
      favoritesList: [],
      recentList: [],
      addToRecent: () => {},
      toggleFavorite: () => {},
      isFavorite: () => false,
      clearRecent: () => {},
    }

    const { result } = renderHook(() => useLibraryHistory(), {
      wrapper: createWrapper(mockContext),
    })

    expect(result.current).toBe(mockContext)
  })

  it('should throw when used outside provider', () => {
    expect(() => {
      renderHook(() => useLibraryHistory())
    }).toThrow('useLibraryHistory must be used within a LibraryHistoryProvider')
  })

  it('should expose context methods', () => {
    const mockContext: LibraryHistoryContextType = {
      recent: [{ id: '1', name: 'Lib', version: '1.0', accessedAt: Date.now() }],
      favorites: new Set(['1']),
      favoritesList: [],
      recentList: [],
      addToRecent: () => {},
      toggleFavorite: () => {},
      isFavorite: (id) => id === '1',
      clearRecent: () => {},
    }

    const { result } = renderHook(() => useLibraryHistory(), {
      wrapper: createWrapper(mockContext),
    })

    expect(result.current.recent).toHaveLength(1)
    expect(result.current.isFavorite('1')).toBe(true)
    expect(result.current.isFavorite('2')).toBe(false)
  })
})
