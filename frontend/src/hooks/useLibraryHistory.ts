import { useContext } from 'react'
import { LibraryHistoryContext } from '../contexts/LibraryHistoryContext'

export function useLibraryHistory() {
  const context = useContext(LibraryHistoryContext)
  if (!context) {
    throw new Error('useLibraryHistory must be used within a LibraryHistoryProvider')
  }
  return context
}
