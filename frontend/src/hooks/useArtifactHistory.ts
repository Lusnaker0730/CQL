import { useCallback, useRef } from 'react'
import type { Artifact } from '../types/authoring'

const MAX_HISTORY = 50

interface ArtifactHistory {
  past: Artifact[]
  future: Artifact[]
}

/**
 * Provides undo/redo for artifact state changes.
 * Call `pushState` before each mutation to record the previous state.
 * Call `undo`/`redo` to navigate the history stack.
 */
export function useArtifactHistory() {
  const historyRef = useRef<ArtifactHistory>({ past: [], future: [] })

  const pushState = useCallback((snapshot: Artifact) => {
    const h = historyRef.current
    h.past = [...h.past.slice(-(MAX_HISTORY - 1)), snapshot]
    h.future = []
  }, [])

  const undo = useCallback((current: Artifact): Artifact | null => {
    const h = historyRef.current
    if (h.past.length === 0) return null
    const previous = h.past[h.past.length - 1]
    h.past = h.past.slice(0, -1)
    h.future = [current, ...h.future]
    return previous
  }, [])

  const redo = useCallback((current: Artifact): Artifact | null => {
    const h = historyRef.current
    if (h.future.length === 0) return null
    const next = h.future[0]
    h.future = h.future.slice(1)
    h.past = [...h.past, current]
    return next
  }, [])

  const reset = useCallback(() => {
    historyRef.current = { past: [], future: [] }
  }, [])

  const canUndo = useCallback(() => historyRef.current.past.length > 0, [])
  const canRedo = useCallback(() => historyRef.current.future.length > 0, [])

  return { pushState, undo, redo, reset, canUndo, canRedo }
}
