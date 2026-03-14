import { useEffect, useCallback } from 'react'

/**
 * Hook that warns the user when they try to leave the page with unsaved changes.
 * Uses the browser's beforeunload event.
 *
 * @param isDirty - whether there are unsaved changes
 * @param message - optional custom message (most browsers ignore this and show their own)
 */
export function useUnsavedChangesGuard(isDirty: boolean, message?: string) {
  const handleBeforeUnload = useCallback(
    (e: BeforeUnloadEvent) => {
      if (!isDirty) return
      e.preventDefault()
      // Most modern browsers ignore the custom message
      e.returnValue = message || 'You have unsaved changes. Are you sure you want to leave?'
    },
    [isDirty, message]
  )

  useEffect(() => {
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [handleBeforeUnload])
}
