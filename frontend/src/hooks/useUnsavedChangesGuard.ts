import { useEffect, useCallback } from 'react'

/**
 * Hook that warns the user when they try to leave the page with unsaved changes.
 *
 * Two layers of protection:
 *  1. **`beforeunload`** — covers full-page navigations (typing a new URL,
 *     closing the tab, refreshing). The browser shows its own dialog; the
 *     custom `message` argument is ignored on modern browsers but kept for
 *     completeness.
 *  2. **`popstate`** (PAT-129) — covers the SPA browser-back / forward case.
 *     `BrowserRouter` (used at the app root) doesn't expose `useBlocker`,
 *     which is data-router only. We listen for `popstate` and, when dirty,
 *     ask for confirmation before letting the navigation proceed; if the
 *     user cancels we push the previous URL back onto the stack so the
 *     view stays put. SPA links inside the app should still rely on the
 *     calling component's explicit "Save & Leave" dialog (see
 *     `EcqmArtifactWorkspace`).
 *
 * @param isDirty - whether there are unsaved changes
 * @param message - optional custom message (used in the popstate confirm)
 */
export function useUnsavedChangesGuard(isDirty: boolean, message?: string) {
  const text = message || 'You have unsaved changes. Are you sure you want to leave?'

  const handleBeforeUnload = useCallback(
    (e: BeforeUnloadEvent) => {
      if (!isDirty) return
      e.preventDefault()
      e.returnValue = text
    },
    [isDirty, text]
  )

  useEffect(() => {
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [handleBeforeUnload])

  useEffect(() => {
    if (!isDirty) return
    const handlePopState = () => {
      // Browser already navigated by the time popstate fires; ask the user
      // and undo if they want to stay.
      const stay = !window.confirm(text)
      if (stay) {
        // Re-push the current entry so back / forward keeps the user here.
        window.history.pushState(null, '', window.location.href)
      }
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [isDirty, text])
}
