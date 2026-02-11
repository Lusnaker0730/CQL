import { useState, useCallback } from 'react'

const STORAGE_KEY = 'fhir-query-history'
const MAX_ENTRIES = 50

export interface HistoryEntry {
  id: string
  resourceType: string
  params: string
  fhirServer: string
  timestamp: number
  isFavorite: boolean
}

function loadHistory(): HistoryEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveHistory(entries: HistoryEntry[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries))
}

export default function useFhirQueryHistory() {
  const [history, setHistory] = useState<HistoryEntry[]>(loadHistory)

  const addEntry = useCallback((resourceType: string, params: string, fhirServer: string) => {
    setHistory(prev => {
      const existing = prev.find(
        e => e.resourceType === resourceType && e.params === params && e.fhirServer === fhirServer
      )
      if (existing) {
        const updated = prev.map(e =>
          e.id === existing.id ? { ...e, timestamp: Date.now() } : e
        )
        saveHistory(updated)
        return updated
      }

      const entry: HistoryEntry = {
        id: crypto.randomUUID(),
        resourceType,
        params,
        fhirServer,
        timestamp: Date.now(),
        isFavorite: false,
      }
      let updated = [entry, ...prev]
      // Evict non-favorite entries beyond limit
      const favorites = updated.filter(e => e.isFavorite)
      const nonFavorites = updated.filter(e => !e.isFavorite)
      if (nonFavorites.length > MAX_ENTRIES) {
        updated = [...favorites, ...nonFavorites.slice(0, MAX_ENTRIES)]
      }
      saveHistory(updated)
      return updated
    })
  }, [])

  const toggleFavorite = useCallback((id: string) => {
    setHistory(prev => {
      const updated = prev.map(e =>
        e.id === id ? { ...e, isFavorite: !e.isFavorite } : e
      )
      saveHistory(updated)
      return updated
    })
  }, [])

  const removeEntry = useCallback((id: string) => {
    setHistory(prev => {
      const updated = prev.filter(e => e.id !== id)
      saveHistory(updated)
      return updated
    })
  }, [])

  const clearHistory = useCallback(() => {
    setHistory(prev => {
      const favorites = prev.filter(e => e.isFavorite)
      saveHistory(favorites)
      return favorites
    })
  }, [])

  const favorites = history.filter(e => e.isFavorite)
  const recent = history
    .filter(e => !e.isFavorite)
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 10)

  return { history, favorites, recent, addEntry, toggleFavorite, removeEntry, clearHistory }
}
