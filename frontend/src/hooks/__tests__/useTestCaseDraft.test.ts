import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTestCaseDraft, clearTestCaseDraft, saveEditingState, loadEditingState, clearEditingState } from '../useTestCaseDraft'

describe('useTestCaseDraft', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const defaultProps = {
    measureId: 1,
    testCaseId: 10,
    title: 'Test Case',
    description: 'A test',
    bundleJson: '{}',
    expectedPops: { IPP: true },
    series: 'Series A',
  }

  it('should return null restoredDraft when no draft exists', () => {
    const { result } = renderHook(() => useTestCaseDraft(defaultProps))

    expect(result.current.restoredDraft).toBeNull()
  })

  it('should auto-save draft after debounce', () => {
    renderHook(() => useTestCaseDraft(defaultProps))

    act(() => { vi.advanceTimersByTime(5000) })

    const stored = localStorage.getItem('testcase-draft-1-10')
    expect(stored).toBeTruthy()
    const parsed = JSON.parse(stored!)
    expect(parsed.title).toBe('Test Case')
    expect(parsed.bundleJson).toBe('{}')
  })

  it('should restore existing draft on mount', () => {
    const draft = {
      title: 'Saved Title',
      description: 'Saved Desc',
      bundleJson: '{"entry":[]}',
      expectedPops: { IPP: false },
      series: 'S1',
      savedAt: Date.now(),
    }
    localStorage.setItem('testcase-draft-1-10', JSON.stringify(draft))

    const { result } = renderHook(() => useTestCaseDraft(defaultProps))

    expect(result.current.restoredDraft).toEqual(draft)
  })

  it('should not restore stale draft (>7 days)', () => {
    const draft = {
      title: 'Old',
      description: '',
      bundleJson: '',
      expectedPops: {},
      series: '',
      savedAt: Date.now() - 8 * 24 * 60 * 60 * 1000,
    }
    localStorage.setItem('testcase-draft-1-10', JSON.stringify(draft))

    const { result } = renderHook(() => useTestCaseDraft(defaultProps))

    expect(result.current.restoredDraft).toBeNull()
    expect(localStorage.getItem('testcase-draft-1-10')).toBeNull()
  })

  it('should use "new" key for null testCaseId', () => {
    renderHook(() => useTestCaseDraft({ ...defaultProps, testCaseId: null }))

    act(() => { vi.advanceTimersByTime(5000) })

    expect(localStorage.getItem('testcase-draft-1-new')).toBeTruthy()
  })

  it('should dismiss draft', () => {
    const draft = {
      title: 'T',
      description: 'D',
      bundleJson: '{}',
      expectedPops: {},
      series: '',
      savedAt: Date.now(),
    }
    localStorage.setItem('testcase-draft-1-10', JSON.stringify(draft))

    const { result } = renderHook(() => useTestCaseDraft(defaultProps))
    expect(result.current.restoredDraft).not.toBeNull()

    act(() => result.current.dismissDraft())

    expect(result.current.restoredDraft).toBeNull()
    expect(localStorage.getItem('testcase-draft-1-10')).toBeNull()
  })

  it('should not auto-save before debounce time', () => {
    renderHook(() => useTestCaseDraft(defaultProps))

    act(() => { vi.advanceTimersByTime(1000) })

    expect(localStorage.getItem('testcase-draft-1-10')).toBeNull()
  })

  it('should debounce multiple rapid changes', () => {
    const { rerender } = renderHook(
      (props: typeof defaultProps) => useTestCaseDraft(props),
      { initialProps: defaultProps },
    )

    // Change props rapidly
    rerender({ ...defaultProps, title: 'Changed 1' })
    act(() => { vi.advanceTimersByTime(1000) })
    rerender({ ...defaultProps, title: 'Changed 2' })
    act(() => { vi.advanceTimersByTime(1000) })
    rerender({ ...defaultProps, title: 'Final' })
    act(() => { vi.advanceTimersByTime(5000) })

    const stored = JSON.parse(localStorage.getItem('testcase-draft-1-10')!)
    expect(stored.title).toBe('Final')
  })

  it('should handle corrupt localStorage data gracefully', () => {
    localStorage.setItem('testcase-draft-1-10', 'not-json')

    const { result } = renderHook(() => useTestCaseDraft(defaultProps))

    expect(result.current.restoredDraft).toBeNull()
  })
})

describe('clearTestCaseDraft', () => {
  it('should remove draft from localStorage', () => {
    localStorage.setItem('testcase-draft-1-10', '{}')

    clearTestCaseDraft(1, 10)

    expect(localStorage.getItem('testcase-draft-1-10')).toBeNull()
  })

  it('should handle null testCaseId', () => {
    localStorage.setItem('testcase-draft-1-new', '{}')

    clearTestCaseDraft(1, null)

    expect(localStorage.getItem('testcase-draft-1-new')).toBeNull()
  })
})

describe('sessionStorage editing state', () => {
  it('saveEditingState should store testCaseId', () => {
    saveEditingState(1, 10)

    expect(sessionStorage.getItem('testcase-editing-1')).toBe('10')
  })

  it('saveEditingState should store "new"', () => {
    saveEditingState(1, 'new')

    expect(sessionStorage.getItem('testcase-editing-1')).toBe('new')
  })

  it('loadEditingState should return number id', () => {
    sessionStorage.setItem('testcase-editing-1', '42')

    expect(loadEditingState(1)).toBe(42)
  })

  it('loadEditingState should return "new"', () => {
    sessionStorage.setItem('testcase-editing-1', 'new')

    expect(loadEditingState(1)).toBe('new')
  })

  it('loadEditingState should return null when empty', () => {
    expect(loadEditingState(1)).toBeNull()
  })

  it('loadEditingState should return null for invalid data', () => {
    sessionStorage.setItem('testcase-editing-1', 'abc')

    expect(loadEditingState(1)).toBeNull()
  })

  it('clearEditingState should remove item', () => {
    sessionStorage.setItem('testcase-editing-1', '10')

    clearEditingState(1)

    expect(sessionStorage.getItem('testcase-editing-1')).toBeNull()
  })
})
