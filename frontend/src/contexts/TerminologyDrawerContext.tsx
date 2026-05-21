import { createContext, useEffect, useRef, useState, useCallback, type ReactNode } from 'react'
import { UI_TRANSITION_DELAY_MS } from '../constants/timing'

export interface SelectedCoding {
  system: string
  code: string
  display: string
}

export interface OpenDrawerOptions {
  tab?: 0 | 1 | 2
  system?: string
  searchText?: string
  onSelect?: (coding: SelectedCoding) => void
}

export interface TerminologyDrawerContextValue {
  isOpen: boolean
  options: OpenDrawerOptions
  openDrawer: (opts?: OpenDrawerOptions) => void
  closeDrawer: () => void
}

// eslint-disable-next-line react-refresh/only-export-components -- context + provider intentionally co-located
export const TerminologyDrawerContext = createContext<TerminologyDrawerContextValue>({
  isOpen: false,
  options: {},
  openDrawer: () => {},
  closeDrawer: () => {},
})

export function TerminologyDrawerProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false)
  const [options, setOptions] = useState<OpenDrawerOptions>({})
  // PAT-149: track the pending close-animation timer so unmount (e.g. SPA route
  // change while the drawer is closing) can clear it instead of letting the
  // setTimeout fire setOptions on a torn-down provider.
  const clearOptionsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const openDrawer = useCallback((opts?: OpenDrawerOptions) => {
    if (clearOptionsTimerRef.current !== null) {
      clearTimeout(clearOptionsTimerRef.current)
      clearOptionsTimerRef.current = null
    }
    setOptions(opts || {})
    setIsOpen(true)
  }, [])

  const closeDrawer = useCallback(() => {
    setIsOpen(false)
    if (clearOptionsTimerRef.current !== null) {
      clearTimeout(clearOptionsTimerRef.current)
    }
    // Keep options around briefly so the closing animation doesn't blank the content.
    clearOptionsTimerRef.current = setTimeout(() => {
      clearOptionsTimerRef.current = null
      setOptions({})
    }, UI_TRANSITION_DELAY_MS)
  }, [])

  useEffect(() => {
    return () => {
      if (clearOptionsTimerRef.current !== null) {
        clearTimeout(clearOptionsTimerRef.current)
        clearOptionsTimerRef.current = null
      }
    }
  }, [])

  return (
    <TerminologyDrawerContext.Provider value={{ isOpen, options, openDrawer, closeDrawer }}>
      {children}
    </TerminologyDrawerContext.Provider>
  )
}
