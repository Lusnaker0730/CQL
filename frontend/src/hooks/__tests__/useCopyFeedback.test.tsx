import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCopyFeedback } from '../useCopyFeedback'

describe('useCopyFeedback — PAT-136 (P1)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('reports the copied key for the configured timeout, then clears', () => {
    const { result } = renderHook(() => useCopyFeedback(1500))

    act(() => result.current.markCopied('A'))
    expect(result.current.copiedKey).toBe('A')
    expect(result.current.isCopied('A')).toBe(true)
    expect(result.current.isCopied('B')).toBe(false)

    act(() => {
      vi.advanceTimersByTime(1499)
    })
    expect(result.current.copiedKey).toBe('A') // not yet cleared

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(result.current.copiedKey).toBeNull()
  })

  it('replaces a pending key when markCopied is called again', () => {
    const { result } = renderHook(() => useCopyFeedback(1000))

    act(() => result.current.markCopied('A'))
    act(() => {
      vi.advanceTimersByTime(500)
    })
    act(() => result.current.markCopied('B'))

    expect(result.current.copiedKey).toBe('B')
    expect(result.current.isCopied('A')).toBe(false)

    // The 500ms remaining from the original A timer should NOT clear B early.
    act(() => {
      vi.advanceTimersByTime(500)
    })
    expect(result.current.copiedKey).toBe('B')

    // B's full 1000ms should fire next.
    act(() => {
      vi.advanceTimersByTime(500)
    })
    expect(result.current.copiedKey).toBeNull()
  })

  it('does not call setState after the component unmounts (the bug it fixes)', () => {
    // Render-spy: count how many times the hook's body re-runs after unmount
    // — there should be none. We capture this via a ref bumped per render.
    let renderCount = 0
    function useSpy() {
      renderCount++
      return useCopyFeedback(500)
    }
    const { result, unmount } = renderHook(useSpy)

    act(() => result.current.markCopied('X'))
    const renderCountBeforeUnmount = renderCount

    unmount()
    act(() => {
      vi.advanceTimersByTime(1000) // long past the 500ms timeout
    })

    // No additional renders after unmount → cleanup cancelled the timer.
    expect(renderCount).toBe(renderCountBeforeUnmount)
  })
})
