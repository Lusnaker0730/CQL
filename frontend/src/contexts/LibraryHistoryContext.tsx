import { createContext, useCallback, type ReactNode } from 'react'
import { useFavorites, useRecent, useAddFavorite, useRemoveFavorite, useAddRecent, useClearRecent } from '../hooks/useLibraryPrefs'
import type { UserFavorite, UserRecent } from '../types'

export interface LibraryHistoryItem {
  id: string
  name: string
  version: string
  accessedAt: number
}

export interface LibraryHistoryContextType {
  recent: LibraryHistoryItem[]
  favorites: Set<string>
  favoritesList: UserFavorite[]
  recentList: UserRecent[]
  addToRecent: (item: { id: string; name: string; version: string }) => void
  toggleFavorite: (id: string) => void
  isFavorite: (id: string) => boolean
  clearRecent: () => void
}

export const LibraryHistoryContext = createContext<LibraryHistoryContextType | null>(null)

export function LibraryHistoryProvider({ children }: { children: ReactNode }) {
  const { data: favoritesData = [] } = useFavorites()
  const { data: recentData = [] } = useRecent()
  const addFavoriteMutation = useAddFavorite()
  const removeFavoriteMutation = useRemoveFavorite()
  const addRecentMutation = useAddRecent()
  const clearRecentMutation = useClearRecent()

  // Build a Set of library IDs that are favorited
  const favoriteIds = new Set(favoritesData.map((f) => f.libraryId))

  // Convert API data to the legacy format for backward compatibility
  const recent: LibraryHistoryItem[] = recentData.map((r) => ({
    id: r.libraryId,
    name: r.libraryName,
    version: r.libraryVersion,
    accessedAt: new Date(r.accessedAt).getTime(),
  }))

  const addToRecent = useCallback((item: { id: string; name: string; version: string }) => {
    if (item.id) {
      addRecentMutation.mutate(item.id)
    }
  }, [addRecentMutation])

  const toggleFavorite = useCallback((id: string) => {
    if (!id) return
    if (favoriteIds.has(id)) {
      removeFavoriteMutation.mutate(id)
    } else {
      addFavoriteMutation.mutate(id)
    }
  }, [favoriteIds, addFavoriteMutation, removeFavoriteMutation])

  const isFavorite = useCallback((id: string) => favoriteIds.has(id), [favoriteIds])

  const clearRecentHandler = useCallback(() => {
    clearRecentMutation.mutate()
  }, [clearRecentMutation])

  return (
    <LibraryHistoryContext.Provider
      value={{
        recent,
        favorites: favoriteIds,
        favoritesList: favoritesData,
        recentList: recentData,
        addToRecent,
        toggleFavorite,
        isFavorite,
        clearRecent: clearRecentHandler,
      }}
    >
      {children}
    </LibraryHistoryContext.Provider>
  )
}
