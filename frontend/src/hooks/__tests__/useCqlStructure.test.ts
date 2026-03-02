import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { createElement } from 'react'
import editorReducer, { setCqlContent } from '../../store/editorSlice'
import { useCqlStructure } from '../useCqlStructure'

vi.mock('../../api', () => ({
  cqlApi: {
    translate: vi.fn(),
  },
}))

vi.mock('../../utils/errorUtils', () => ({
  extractApiError: (err: unknown) => (err instanceof Error ? err.message : 'Unknown error'),
}))

import { cqlApi } from '../../api'

function createWrapper(cql = '') {
  const store = configureStore({
    reducer: {
      editor: editorReducer,
      execution: () => ({}),
      auth: () => ({ user: null, token: null, isAuthenticated: false, loading: false }),
    },
  })
  if (cql) store.dispatch(setCqlContent(cql))
  return {
    store,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(Provider, { store }, children),
  }
}

describe('useCqlStructure', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should return empty structure initially', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    expect(result.current.structure.libraryId).toBe('')
    expect(result.current.isParsing).toBe(false)
    expect(result.current.parseError).toBeNull()
  })

  it('should parse CQL after debounce', async () => {
    vi.mocked(cqlApi.translate).mockResolvedValue({
      success: true,
      metadata: {
        libraryId: 'TestLib',
        libraryVersion: '1.0',
        usings: ['FHIR'],
        includes: [],
        valueSets: [],
        codes: [],
        concepts: [],
        parameters: [],
        expressions: [],
      },
    })

    const { store, wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('library TestLib version \'1.0\'')) })

    // Before debounce
    expect(result.current.isParsing).toBe(false)

    // After debounce
    await act(async () => { vi.advanceTimersByTime(2000) })
    // Wait for the async parsing
    vi.useRealTimers()
    await waitFor(() => expect(result.current.isParsing).toBe(false))

    expect(result.current.structure.libraryId).toBe('TestLib')
  })

  it('should set parseError on translation errors', async () => {
    vi.mocked(cqlApi.translate).mockResolvedValue({
      success: false,
      errors: [{ message: 'Syntax error', startLine: 1 }],
      metadata: {
        libraryId: '',
        libraryVersion: '',
        usings: [],
        includes: [],
        valueSets: [],
        codes: [],
        concepts: [],
        parameters: [],
        expressions: [],
      },
    })

    const { store, wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('invalid cql')) })

    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(result.current.parseError).toBeTruthy())
    expect(result.current.parseError).toContain('Line 1')
  })

  it('should set parseError on API failure', async () => {
    vi.mocked(cqlApi.translate).mockRejectedValue(new Error('Network error'))

    const { store, wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('library Test')) })

    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(result.current.parseError).toBe('Network error'))
  })

  it('should not parse empty CQL content', () => {
    const { store, wrapper } = createWrapper()
    renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('')) })
    act(() => { vi.advanceTimersByTime(2000) })

    expect(cqlApi.translate).not.toHaveBeenCalled()
  })

  it('should not re-parse unchanged content', async () => {
    vi.mocked(cqlApi.translate).mockResolvedValue({
      success: true,
      metadata: {
        libraryId: 'Test',
        libraryVersion: '1.0',
        usings: [],
        includes: [],
        valueSets: [],
        codes: [],
        concepts: [],
        parameters: [],
        expressions: [],
      },
    })

    const { store, wrapper } = createWrapper()
    renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('library Test')) })
    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(cqlApi.translate).toHaveBeenCalledTimes(1))

    // Dispatch same content again
    vi.useFakeTimers()
    act(() => { store.dispatch(setCqlContent('library Test')) })
    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()

    // Should not have been called again
    expect(cqlApi.translate).toHaveBeenCalledTimes(1)
  })

  it('should expose a manual parse function', async () => {
    vi.mocked(cqlApi.translate).mockResolvedValue({
      success: true,
      metadata: {
        libraryId: 'Manual',
        libraryVersion: '1.0',
        usings: [],
        includes: [],
        valueSets: [],
        codes: [],
        concepts: [],
        parameters: [],
        expressions: [],
      },
    })

    const { store, wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('library Manual')) })

    // Call parse directly without waiting for debounce
    act(() => { result.current.parse() })

    vi.useRealTimers()
    await waitFor(() => expect(result.current.structure.libraryId).toBe('Manual'))
  })

  it('should separate expressions and functions', async () => {
    vi.mocked(cqlApi.translate).mockResolvedValue({
      success: true,
      metadata: {
        libraryId: 'Test',
        libraryVersion: '1.0',
        usings: [],
        includes: [],
        valueSets: [],
        codes: [],
        concepts: [],
        parameters: [],
        expressions: [
          { name: 'MyDef', context: 'Patient', resultType: 'Boolean', accessLevel: 'Public' },
          { name: 'MyFunc', context: 'Patient', resultType: 'String -> Boolean', accessLevel: 'Public' },
          { name: '__private', context: 'Patient', resultType: 'Boolean', accessLevel: 'Private' },
        ],
      },
    })

    const { store, wrapper } = createWrapper()
    const { result } = renderHook(() => useCqlStructure(), { wrapper })

    act(() => { store.dispatch(setCqlContent('library Test version \'1.0\'')) })
    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(result.current.structure.libraryId).toBe('Test'))

    expect(result.current.structure.expressions).toEqual([
      { name: 'MyDef', context: 'Patient', resultType: 'Boolean' },
    ])
    expect(result.current.structure.functions).toEqual([
      { name: 'MyFunc', context: 'Patient', resultType: 'String -> Boolean' },
    ])
  })
})
