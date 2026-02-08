import { createContext, useState, useCallback, type ReactNode } from 'react'

export interface LibraryHistoryItem {
  id: string
  name: string
  version: string
  accessedAt: number
}

export interface LibraryHistoryContextType {
  recent: LibraryHistoryItem[]
  favorites: Set<string>
  addToRecent: (item: { id: string; name: string; version: string }) => void
  toggleFavorite: (id: string) => void
  isFavorite: (id: string) => boolean
  clearRecent: () => void
}

export const LibraryHistoryContext = createContext<LibraryHistoryContextType | null>(null)

const RECENT_KEY = 'cql-recent-libraries'
const FAVORITES_KEY = 'cql-favorite-libraries'
const MAX_RECENT = 10

function loadRecent(): LibraryHistoryItem[] {
  try {
    const stored = localStorage.getItem(RECENT_KEY)
    return stored ? JSON.parse(stored) : []
  } catch {
    return []
  }
}

function loadFavorites(): Set<string> {
  try {
    const stored = localStorage.getItem(FAVORITES_KEY)
    return stored ? new Set(JSON.parse(stored)) : new Set()
  } catch {
    return new Set()
  }
}

export function LibraryHistoryProvider({ children }: { children: ReactNode }) {
  const [recent, setRecent] = useState<LibraryHistoryItem[]>(loadRecent)
  const [favorites, setFavorites] = useState<Set<string>>(loadFavorites)

  const addToRecent = useCallback((item: { id: string; name: string; version: string }) => {
    setRecent((prev) => {
      const filtered = prev.filter((r) => r.id !== item.id)
      const next = [{ ...item, accessedAt: Date.now() }, ...filtered].slice(0, MAX_RECENT)
      localStorage.setItem(RECENT_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const toggleFavorite = useCallback((id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      localStorage.setItem(FAVORITES_KEY, JSON.stringify([...next]))
      return next
    })
  }, [])

  const isFavorite = useCallback((id: string) => favorites.has(id), [favorites])

  const clearRecent = useCallback(() => {
    setRecent([])
    localStorage.removeItem(RECENT_KEY)
  }, [])

  return (
    <LibraryHistoryContext.Provider
      value={{ recent, favorites, addToRecent, toggleFavorite, isFavorite, clearRecent }}
    >
      {children}
    </LibraryHistoryContext.Provider>
  )
}
