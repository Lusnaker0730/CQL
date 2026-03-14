import { createContext, useState, useCallback, type ReactNode } from 'react'
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

  const openDrawer = useCallback((opts?: OpenDrawerOptions) => {
    setOptions(opts || {})
    setIsOpen(true)
  }, [])

  const closeDrawer = useCallback(() => {
    setIsOpen(false)
    // Keep options around briefly so closing animation doesn't blank the content
    setTimeout(() => setOptions({}), UI_TRANSITION_DELAY_MS)
  }, [])

  return (
    <TerminologyDrawerContext.Provider value={{ isOpen, options, openDrawer, closeDrawer }}>
      {children}
    </TerminologyDrawerContext.Provider>
  )
}
