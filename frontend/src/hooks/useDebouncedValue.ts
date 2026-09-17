import { useState, useEffect, useRef } from 'react'

/**
 * Returns a debounced version of the provided value.
 * Updates the debounced value after `delay` ms of inactivity.
 */
export function useDebouncedValue<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    timerRef.current = setTimeout(() => setDebouncedValue(value), delay)
    return () => clearTimeout(timerRef.current)
  }, [value, delay])

  return debouncedValue
}
