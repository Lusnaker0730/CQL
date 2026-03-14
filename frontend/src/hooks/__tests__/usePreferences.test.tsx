import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { usePreferences } from '../usePreferences'
import { PreferencesProvider } from '../../contexts/PreferencesContext'
import type { ReactNode } from 'react'

function wrapper({ children }: { children: ReactNode }) {
  return <PreferencesProvider>{children}</PreferencesProvider>
}

describe('usePreferences', () => {
  it('should throw when used outside provider', () => {
    expect(() => {
      renderHook(() => usePreferences())
    }).toThrow('usePreferences must be used within a PreferencesProvider')
  })

  it('should return default theme mode as light', () => {
    const { result } = renderHook(() => usePreferences(), { wrapper })
    expect(result.current.preferences.themeMode).toBe('light')
  })

  it('should toggle theme to dark', () => {
    const { result } = renderHook(() => usePreferences(), { wrapper })

    act(() => {
      result.current.updatePreferences({ themeMode: 'dark' })
    })

    expect(result.current.preferences.themeMode).toBe('dark')
  })

  it('should toggle theme back to light', () => {
    const { result } = renderHook(() => usePreferences(), { wrapper })

    act(() => {
      result.current.updatePreferences({ themeMode: 'dark' })
    })

    act(() => {
      result.current.updatePreferences({ themeMode: 'light' })
    })

    expect(result.current.preferences.themeMode).toBe('light')
  })

  it('should update editor font size', () => {
    const { result } = renderHook(() => usePreferences(), { wrapper })

    act(() => {
      result.current.updatePreferences({ editorFontSize: 18 })
    })

    expect(result.current.preferences.editorFontSize).toBe(18)
  })

  it('should reset preferences to defaults', () => {
    const { result } = renderHook(() => usePreferences(), { wrapper })

    act(() => {
      result.current.updatePreferences({ themeMode: 'dark', editorFontSize: 20 })
    })

    act(() => {
      result.current.resetPreferences()
    })

    expect(result.current.preferences.themeMode).toBe('light')
    expect(result.current.preferences.editorFontSize).toBe(14)
  })
})
