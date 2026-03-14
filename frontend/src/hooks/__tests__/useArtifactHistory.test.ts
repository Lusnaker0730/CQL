import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useArtifactHistory } from '../useArtifactHistory'
import type { Artifact } from '../../types/authoring'

function makeArtifact(id: number): Artifact {
  return { id, name: `Artifact ${id}` } as unknown as Artifact
}

describe('useArtifactHistory', () => {
  it('should initialize with empty history', () => {
    const { result } = renderHook(() => useArtifactHistory())

    expect(result.current.canUndo()).toBe(false)
    expect(result.current.canRedo()).toBe(false)
  })

  it('should enable undo after pushState', () => {
    const { result } = renderHook(() => useArtifactHistory())

    act(() => result.current.pushState(makeArtifact(1)))

    expect(result.current.canUndo()).toBe(true)
    expect(result.current.canRedo()).toBe(false)
  })

  it('should undo to previous state', () => {
    const { result } = renderHook(() => useArtifactHistory())
    const a1 = makeArtifact(1)
    const a2 = makeArtifact(2)

    act(() => result.current.pushState(a1))

    let undone: Artifact | null = null
    act(() => { undone = result.current.undo(a2) })

    expect(undone).toEqual(a1)
    expect(result.current.canUndo()).toBe(false)
    expect(result.current.canRedo()).toBe(true)
  })

  it('should redo to next state', () => {
    const { result } = renderHook(() => useArtifactHistory())
    const a1 = makeArtifact(1)
    const a2 = makeArtifact(2)

    act(() => result.current.pushState(a1))
    act(() => { result.current.undo(a2) })

    let redone: Artifact | null = null
    act(() => { redone = result.current.redo(a1) })

    expect(redone).toEqual(a2)
    expect(result.current.canRedo()).toBe(false)
    expect(result.current.canUndo()).toBe(true)
  })

  it('should return null when undoing with empty past', () => {
    const { result } = renderHook(() => useArtifactHistory())

    let undone: Artifact | null = null
    act(() => { undone = result.current.undo(makeArtifact(1)) })

    expect(undone).toBeNull()
  })

  it('should return null when redoing with empty future', () => {
    const { result } = renderHook(() => useArtifactHistory())

    let redone: Artifact | null = null
    act(() => { redone = result.current.redo(makeArtifact(1)) })

    expect(redone).toBeNull()
  })

  it('should clear future on new pushState after undo', () => {
    const { result } = renderHook(() => useArtifactHistory())
    const a1 = makeArtifact(1)
    const a2 = makeArtifact(2)
    const a3 = makeArtifact(3)

    act(() => result.current.pushState(a1))
    act(() => { result.current.undo(a2) })

    // Future should have a2, now push a new state
    act(() => result.current.pushState(a3))

    expect(result.current.canRedo()).toBe(false)
    expect(result.current.canUndo()).toBe(true)
  })

  it('should reset history', () => {
    const { result } = renderHook(() => useArtifactHistory())

    act(() => result.current.pushState(makeArtifact(1)))
    act(() => result.current.pushState(makeArtifact(2)))
    act(() => result.current.reset())

    expect(result.current.canUndo()).toBe(false)
    expect(result.current.canRedo()).toBe(false)
  })

  it('should cap history at MAX_HISTORY (50)', () => {
    const { result } = renderHook(() => useArtifactHistory())

    // Push 55 states
    for (let i = 0; i < 55; i++) {
      act(() => result.current.pushState(makeArtifact(i)))
    }

    // Should only be able to undo 50 times
    let undoCount = 0
    let undone: Artifact | null = makeArtifact(999)
    while (undone !== null) {
      act(() => { undone = result.current.undo(makeArtifact(999)) })
      if (undone !== null) undoCount++
    }

    expect(undoCount).toBe(50)
  })

  it('should handle multiple undo/redo cycles', () => {
    const { result } = renderHook(() => useArtifactHistory())
    const a1 = makeArtifact(1)
    const a2 = makeArtifact(2)
    const a3 = makeArtifact(3)

    act(() => result.current.pushState(a1))
    act(() => result.current.pushState(a2))

    // Undo twice
    let current = a3
    act(() => { current = result.current.undo(current) || current })
    expect(current).toEqual(a2)
    act(() => { current = result.current.undo(current) || current })
    expect(current).toEqual(a1)

    // Redo twice
    act(() => { current = result.current.redo(current) || current })
    expect(current).toEqual(a2)
    act(() => { current = result.current.redo(current) || current })
    expect(current).toEqual(a3)
  })

  it('should track undo/redo availability through operations', () => {
    const { result } = renderHook(() => useArtifactHistory())

    // Initial: no undo/redo
    expect(result.current.canUndo()).toBe(false)
    expect(result.current.canRedo()).toBe(false)

    // After push: can undo, no redo
    act(() => result.current.pushState(makeArtifact(1)))
    expect(result.current.canUndo()).toBe(true)
    expect(result.current.canRedo()).toBe(false)

    // After undo: no undo (only one state), can redo
    act(() => { result.current.undo(makeArtifact(2)) })
    expect(result.current.canUndo()).toBe(false)
    expect(result.current.canRedo()).toBe(true)

    // After redo: can undo, no redo
    act(() => { result.current.redo(makeArtifact(1)) })
    expect(result.current.canUndo()).toBe(true)
    expect(result.current.canRedo()).toBe(false)
  })
})
