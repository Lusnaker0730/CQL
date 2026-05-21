import { useCallback, useEffect, useRef, useState } from 'react'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../constants/timing'

/**
 * Provides "copied!" toast-style feedback for inline copy buttons.
 *
 * The terminology drawer panels (CodeSearch / ValueSet / CodeLookup) all
 * implemented the same `copiedCode` + `copyTimerRef` + `setTimeout` pattern
 * separately, and none of them cleared the timer on unmount — closing the
 * drawer mid-feedback would call `setState` on an unmounted component
 * (React 18 warning + closure leak). This hook centralizes the pattern and
 * fixes the leak in one place.
 *
 * @param timeoutMs how long to show the "copied!" indicator (default
 *                  `COPY_FEEDBACK_TIMEOUT_MS`)
 * @returns
 *   - `copiedKey`: the key passed to the most-recent `markCopied`, or `null`
 *   - `markCopied(key)`: writes `key` to localStorage and starts the timer
 *   - `isCopied(key)`: convenience for inline render conditionals
 */
export interface UseCopyFeedbackResult {
  copiedKey: string | null
  markCopied: (key: string) => void
  isCopied: (key: string) => boolean
}

export function useCopyFeedback(timeoutMs: number = COPY_FEEDBACK_TIMEOUT_MS): UseCopyFeedbackResult {
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Always cancel a pending timer when the component unmounts so the deferred
  // `setCopiedKey(null)` doesn't fire on a dead component.
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  const markCopied = useCallback(
    (key: string) => {
      setCopiedKey(key)
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => {
        setCopiedKey(null)
        timerRef.current = null
      }, timeoutMs)
    },
    [timeoutMs],
  )

  const isCopied = useCallback((key: string) => copiedKey === key, [copiedKey])

  return { copiedKey, markCopied, isCopied }
}
