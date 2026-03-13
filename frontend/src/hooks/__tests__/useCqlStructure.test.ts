import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
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

describe('useCqlStructure', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should return empty structure initially', () => {
    const { result } = renderHook(() => useCqlStructure())

    expect(result.current.structure.libraryId).toBe('')
    expect(result.current.isParsing).toBe(false)
    expect(result.current.parseError).toBeNull()
  })

  it('should parse CQL after debounce via notifyContentChanged', async () => {
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

    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('library TestLib version \'1.0\'') })

    // Before debounce
    expect(result.current.isParsing).toBe(false)

    // After debounce
    await act(async () => { vi.advanceTimersByTime(2000) })
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

    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('invalid cql') })

    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(result.current.parseError).toBeTruthy())
    expect(result.current.parseError).toContain('Line 1')
  })

  it('should set parseError on API failure', async () => {
    vi.mocked(cqlApi.translate).mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('library Test') })

    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(result.current.parseError).toBe('Network error'))
  })

  it('should not parse empty CQL content', () => {
    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('') })
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

    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('library Test') })
    await act(async () => { vi.advanceTimersByTime(2000) })
    vi.useRealTimers()
    await waitFor(() => expect(cqlApi.translate).toHaveBeenCalledTimes(1))

    // Notify same content again
    vi.useFakeTimers()
    act(() => { result.current.notifyContentChanged('library Test') })
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

    const { result } = renderHook(() => useCqlStructure())

    // Notify content first so parse() has something to work with
    act(() => { result.current.notifyContentChanged('library Manual') })

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

    const { result } = renderHook(() => useCqlStructure())

    act(() => { result.current.notifyContentChanged('library Test version \'1.0\'') })
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
