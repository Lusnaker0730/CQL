import { useEffect, useRef, useState, type ReactNode } from 'react'
import { ResponsiveContainer } from 'recharts'

/**
 * PAT-165 follow-up: Recharts `<ResponsiveContainer>` logs
 *   "The width(-1) and height(-1) of chart should be greater than 0"
 * on its first render pass — before its ResizeObserver has reported parent
 * dimensions. The warning is cosmetic (the chart paints correctly once a
 * resize fires), but it's noisy in production console.
 *
 * This thin wrapper measures the parent itself and only mounts the underlying
 * ResponsiveContainer once positive dimensions are seen. The render hierarchy
 * stays compatible — recharts still does its own ResizeObserver work; we just
 * skip the no-dimension first frame.
 */
export default function SafeResponsiveContainer({ children }: { children: ReactNode }) {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const el = hostRef.current
    if (!el) return
    // Synchronous fast path: parent already laid out
    const rect = el.getBoundingClientRect()
    if (rect.width > 0 && rect.height > 0) {
      setReady(true)
      return
    }
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect
        if (width > 0 && height > 0) {
          setReady(true)
          return
        }
      }
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  return (
    <div ref={hostRef} style={{ width: '100%', height: '100%' }}>
      {ready && (
        <ResponsiveContainer width="100%" height="100%">
          {children as React.ReactElement}
        </ResponsiveContainer>
      )}
    </div>
  )
}
