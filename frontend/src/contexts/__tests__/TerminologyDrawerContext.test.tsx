import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useContext } from 'react'
import {
  TerminologyDrawerContext,
  TerminologyDrawerProvider,
} from '../TerminologyDrawerContext'

function useDrawer() {
  return useContext(TerminologyDrawerContext)
}

function wrapper({ children }: { children: React.ReactNode }) {
  return <TerminologyDrawerProvider>{children}</TerminologyDrawerProvider>
}

describe('TerminologyDrawerContext', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts closed with empty options', () => {
    const { result } = renderHook(() => useDrawer(), { wrapper })
    expect(result.current.isOpen).toBe(false)
    expect(result.current.options).toEqual({})
  })

  it('openDrawer without args sets isOpen=true and empty options', () => {
    const { result } = renderHook(() => useDrawer(), { wrapper })
    act(() => result.current.openDrawer())
    expect(result.current.isOpen).toBe(true)
    expect(result.current.options).toEqual({})
  })

  it('openDrawer stores the passed options', () => {
    const { result } = renderHook(() => useDrawer(), { wrapper })
    const onSelect = vi.fn()
    act(() => result.current.openDrawer({ tab: 1, system: 'http://loinc.org', searchText: 'a1c', onSelect }))
    expect(result.current.isOpen).toBe(true)
    expect(result.current.options.tab).toBe(1)
    expect(result.current.options.system).toBe('http://loinc.org')
    expect(result.current.options.searchText).toBe('a1c')
    expect(result.current.options.onSelect).toBe(onSelect)
  })

  it('closeDrawer sets isOpen=false immediately but defers options clearing', () => {
    const { result } = renderHook(() => useDrawer(), { wrapper })
    act(() => result.current.openDrawer({ tab: 2, system: 'X' }))
    expect(result.current.isOpen).toBe(true)
    expect(result.current.options.system).toBe('X')

    act(() => result.current.closeDrawer())
    expect(result.current.isOpen).toBe(false)
    // Options still present while close animation plays
    expect(result.current.options.system).toBe('X')

    // After the UI transition delay fires, options get wiped
    act(() => vi.runAllTimers())
    expect(result.current.options).toEqual({})
  })

  it('opening after close replaces previous options', () => {
    const { result } = renderHook(() => useDrawer(), { wrapper })
    act(() => result.current.openDrawer({ searchText: 'first' }))
    act(() => result.current.closeDrawer())
    act(() => vi.runAllTimers())

    act(() => result.current.openDrawer({ searchText: 'second' }))
    expect(result.current.options.searchText).toBe('second')
  })
})
