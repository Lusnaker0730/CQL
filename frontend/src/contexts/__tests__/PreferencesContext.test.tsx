import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useContext } from 'react'
import { PreferencesContext, PreferencesProvider } from '../PreferencesContext'

const STORAGE_KEY = 'cql-platform-preferences'

function usePrefs() {
  const ctx = useContext(PreferencesContext)
  if (!ctx) throw new Error('PreferencesContext not available')
  return ctx
}

function wrapper({ children }: { children: React.ReactNode }) {
  return <PreferencesProvider>{children}</PreferencesProvider>
}

describe('PreferencesContext', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('exposes default preferences when localStorage is empty', () => {
    const { result } = renderHook(() => usePrefs(), { wrapper })
    const p = result.current.preferences
    expect(p.editorFontSize).toBe(14)
    expect(p.editorTabSize).toBe(2)
    expect(p.editorWordWrap).toBe('on')
    expect(p.editorMinimap).toBe(false)
    expect(p.themeMode).toBe('light')
    expect(typeof p.defaultFhirServerUrl).toBe('string')
  })

  it('merges stored preferences over defaults', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ editorFontSize: 20, themeMode: 'dark' }))
    const { result } = renderHook(() => usePrefs(), { wrapper })
    expect(result.current.preferences.editorFontSize).toBe(20)
    expect(result.current.preferences.themeMode).toBe('dark')
    // Unspecified field keeps default
    expect(result.current.preferences.editorTabSize).toBe(2)
  })

  it('falls back to defaults on malformed JSON', () => {
    localStorage.setItem(STORAGE_KEY, '{not valid json')
    const { result } = renderHook(() => usePrefs(), { wrapper })
    expect(result.current.preferences.editorFontSize).toBe(14)
  })

  it('updatePreferences partially merges + persists to localStorage', () => {
    const { result } = renderHook(() => usePrefs(), { wrapper })
    act(() => result.current.updatePreferences({ editorFontSize: 18 }))

    expect(result.current.preferences.editorFontSize).toBe(18)
    expect(result.current.preferences.editorTabSize).toBe(2)

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
    expect(stored.editorFontSize).toBe(18)
  })

  it('resetPreferences clears localStorage and reverts to defaults', () => {
    const { result } = renderHook(() => usePrefs(), { wrapper })
    act(() => result.current.updatePreferences({ editorFontSize: 22, themeMode: 'dark' }))
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()

    act(() => result.current.resetPreferences())
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(result.current.preferences.editorFontSize).toBe(14)
    expect(result.current.preferences.themeMode).toBe('light')
  })
})
