import { createContext, useState, useCallback, type ReactNode } from 'react'
import type { PaletteMode } from '@mui/material'
import { DEFAULT_FHIR_SERVER_URL } from '../config/env'

export interface Preferences {
  editorFontSize: number
  editorTabSize: number
  editorWordWrap: 'on' | 'off' | 'wordWrapColumn'
  editorMinimap: boolean
  themeMode: PaletteMode
  defaultFhirServerUrl: string
}

const DEFAULT_PREFERENCES: Preferences = {
  editorFontSize: 14,
  editorTabSize: 2,
  editorWordWrap: 'on',
  editorMinimap: false,
  themeMode: 'light',
  defaultFhirServerUrl: DEFAULT_FHIR_SERVER_URL,
}

const STORAGE_KEY = 'cql-platform-preferences'

function loadPreferences(): Preferences {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      return { ...DEFAULT_PREFERENCES, ...JSON.parse(stored) }
    }
  } catch {
    // ignore parse errors
  }
  return DEFAULT_PREFERENCES
}

export interface PreferencesContextType {
  preferences: Preferences
  updatePreferences: (updates: Partial<Preferences>) => void
  resetPreferences: () => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const PreferencesContext = createContext<PreferencesContextType | null>(null)

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, setPreferences] = useState<Preferences>(loadPreferences)

  const updatePreferences = useCallback((updates: Partial<Preferences>) => {
    setPreferences((prev) => {
      const next = { ...prev, ...updates }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  const resetPreferences = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setPreferences(DEFAULT_PREFERENCES)
  }, [])

  return (
    <PreferencesContext.Provider value={{ preferences, updatePreferences, resetPreferences }}>
      {children}
    </PreferencesContext.Provider>
  )
}
