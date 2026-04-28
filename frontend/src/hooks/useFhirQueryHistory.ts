import { useState, useCallback, useMemo } from 'react'
import { generateId, getStoredUsername } from '../utils/validation'

const STORAGE_KEY_BASE = 'fhir-query-history'
const LEGACY_STORAGE_KEY = 'fhir-query-history'
const MAX_ENTRIES = 50

// PHI containment: query params often include `?subject=Patient/realId` or
// `?identifier=A123456789` (Taiwan IC number). Per-user scoping prevents leak
// between users on a shared diagnostic-room workstation.
function getStorageKey(username: string): string {
  return username && username !== 'anonymous'
    ? `${STORAGE_KEY_BASE}:${username}`
    : STORAGE_KEY_BASE
}

export interface HistoryEntry {
  id: string
  resourceType: string
  params: string
  fhirServer: string
  timestamp: number
  isFavorite: boolean
}

function loadHistory(key: string): HistoryEntry[] {
  // Read the per-user key. The legacy un-scoped key is intentionally NOT
  // restored — its contents may belong to a different user and replaying
  // their queries would be a confidentiality leak. We just remove it on
  // first save below.
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveHistory(key: string, entries: HistoryEntry[]) {
  localStorage.setItem(key, JSON.stringify(entries))
  // Drop the legacy un-scoped key so we don't keep stale cross-user history.
  if (key !== LEGACY_STORAGE_KEY) {
    try {
      localStorage.removeItem(LEGACY_STORAGE_KEY)
    } catch {
      // ignore
    }
  }
}

export default function useFhirQueryHistory() {
  const storageKey = useMemo(() => getStorageKey(getStoredUsername()), [])
  const [history, setHistory] = useState<HistoryEntry[]>(() => loadHistory(storageKey))

  const addEntry = useCallback((resourceType: string, params: string, fhirServer: string) => {
    setHistory(prev => {
      const existing = prev.find(
        e => e.resourceType === resourceType && e.params === params && e.fhirServer === fhirServer
      )
      if (existing) {
        const updated = prev.map(e =>
          e.id === existing.id ? { ...e, timestamp: Date.now() } : e
        )
        saveHistory(storageKey, updated)
        return updated
      }

      const entry: HistoryEntry = {
        id: generateId(),
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
      saveHistory(storageKey, updated)
      return updated
    })
  }, [storageKey])

  const toggleFavorite = useCallback((id: string) => {
    setHistory(prev => {
      const updated = prev.map(e =>
        e.id === id ? { ...e, isFavorite: !e.isFavorite } : e
      )
      saveHistory(storageKey, updated)
      return updated
    })
  }, [storageKey])

  const removeEntry = useCallback((id: string) => {
    setHistory(prev => {
      const updated = prev.filter(e => e.id !== id)
      saveHistory(storageKey, updated)
      return updated
    })
  }, [storageKey])

  const clearHistory = useCallback(() => {
    setHistory(prev => {
      const favorites = prev.filter(e => e.isFavorite)
      saveHistory(storageKey, favorites)
      return favorites
    })
  }, [storageKey])

  const favorites = history.filter(e => e.isFavorite)
  const recent = history
    .filter(e => !e.isFavorite)
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 10)

  return { history, favorites, recent, addEntry, toggleFavorite, removeEntry, clearHistory }
}
