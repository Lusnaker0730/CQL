import { useState, useCallback } from 'react'
import { generateId, getStoredUsername } from '../utils/validation'

const LEGACY_KEY = 'fhir-query-history'
const MAX_ENTRIES = 50

export interface HistoryEntry {
  id: string
  resourceType: string
  params: string
  // PAT-212: tenant-scoped connection id the query ran against (null = sandbox).
  connectionId: number | null
  timestamp: number
  isFavorite: boolean
}

// PAT-134: per-user scoping prevents PHI in FHIR search params (patient
// identifiers, names, subject references, etc.) from leaking across user
// sessions on shared devices. The storage key is computed per-user:
//   - authenticated user → `fhir-query-history:{username}`
//   - anonymous (no login) → legacy un-scoped key (scoping an anonymous
//     bucket adds no privacy benefit and breaks the migration path for
//     existing un-scoped data on dev machines)
function getStorageKey(): string {
  const username = getStoredUsername()
  return username === 'anonymous' ? LEGACY_KEY : `${LEGACY_KEY}:${username}`
}

function loadHistory(): HistoryEntry[] {
  // Confidentiality: read only from the CURRENT user's scoped key. Do
  // NOT auto-restore the legacy un-scoped bucket during a scoped session —
  // it may hold queries from a previous user on the same machine, and
  // those queries can contain PHI in the params string.
  try {
    const raw = localStorage.getItem(getStorageKey())
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveHistory(entries: HistoryEntry[]) {
  const key = getStorageKey()
  localStorage.setItem(key, JSON.stringify(entries))
  // Migration cleanup: once we've persisted under a scoped key, delete the
  // legacy un-scoped bucket so a later anonymous mount (or another user)
  // cannot replay entries that pre-dated PAT-134.
  if (key !== LEGACY_KEY) {
    localStorage.removeItem(LEGACY_KEY)
  }
}

export default function useFhirQueryHistory() {
  const [history, setHistory] = useState<HistoryEntry[]>(loadHistory)

  const addEntry = useCallback((resourceType: string, params: string, connectionId: number | null) => {
    setHistory(prev => {
      const existing = prev.find(
        e => e.resourceType === resourceType && e.params === params && e.connectionId === connectionId
      )
      if (existing) {
        const updated = prev.map(e =>
          e.id === existing.id ? { ...e, timestamp: Date.now() } : e
        )
        saveHistory(updated)
        return updated
      }

      const entry: HistoryEntry = {
        id: generateId(),
        resourceType,
        params,
        connectionId,
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
