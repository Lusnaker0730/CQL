import { createContext, useState, useCallback, useMemo, useEffect, type ReactNode } from 'react'
import { registerEhrOutageListener } from '../api/ehrOutageBridge'

/**
 * PAT-110: a single active EHR outage "incident" surfaced to the user.
 * Multiple failing EHRs collapse into separate entries keyed by connectionId
 * (or "unknown" when the backend didn't carry connection identity).
 */
export interface EhrOutageEntry {
  /** Numeric id from backend upstream.connectionId, or string "unknown" when null. */
  key: string
  connectionId: number | null
  connectionName: string | null
  /** Matches FhirServerUnavailableException.Reason on the server. */
  reason: 'TIMEOUT' | 'CIRCUIT_BREAKER_OPEN' | 'UPSTREAM_5XX' | 'CONNECTION_REFUSED' | 'OTHER'
  /** Count of consecutive failures since the banner first appeared. */
  failureCount: number
  /** When the most recent failure was recorded (ms epoch). */
  lastFailureAt: number
  /** Server hint; FE respects but also lets the user retry manually. */
  retryAfterSeconds: number
}

export interface EhrOutageContextType {
  outages: EhrOutageEntry[]
  reportOutage: (incident: Omit<EhrOutageEntry, 'failureCount' | 'lastFailureAt' | 'key'>) => void
  clearOutage: (key: string) => void
  clearAll: () => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const EhrOutageContext = createContext<EhrOutageContextType | null>(null)

function keyFor(connectionId: number | null): string {
  return connectionId == null ? 'unknown' : String(connectionId)
}

export function EhrOutageProvider({ children }: { children: ReactNode }) {
  const [outages, setOutages] = useState<EhrOutageEntry[]>([])

  const reportOutage = useCallback<EhrOutageContextType['reportOutage']>((incident) => {
    const key = keyFor(incident.connectionId)
    setOutages((prev) => {
      const idx = prev.findIndex((o) => o.key === key)
      const now = Date.now()
      if (idx === -1) {
        return [
          ...prev,
          { ...incident, key, failureCount: 1, lastFailureAt: now },
        ]
      }
      // Aggregate repeat failures into the same banner row rather than stacking.
      const next = [...prev]
      next[idx] = {
        ...next[idx],
        reason: incident.reason,
        connectionName: incident.connectionName ?? next[idx].connectionName,
        retryAfterSeconds: incident.retryAfterSeconds,
        failureCount: next[idx].failureCount + 1,
        lastFailureAt: now,
      }
      return next
    })
  }, [])

  const clearOutage = useCallback((key: string) => {
    setOutages((prev) => prev.filter((o) => o.key !== key))
  }, [])

  const clearAll = useCallback(() => setOutages([]), [])

  // Bridge Axios interceptor → this provider. Unregister on unmount so StrictMode's
  // double-mount and HMR don't leak a stale listener.
  useEffect(() => {
    return registerEhrOutageListener((envelope) => {
      reportOutage({
        connectionId: envelope.connectionId,
        connectionName: envelope.connectionName,
        reason: envelope.reason,
        retryAfterSeconds: envelope.retryAfterSeconds,
      })
    })
  }, [reportOutage])

  const value = useMemo(
    () => ({ outages, reportOutage, clearOutage, clearAll }),
    [outages, reportOutage, clearOutage, clearAll]
  )

  return <EhrOutageContext.Provider value={value}>{children}</EhrOutageContext.Provider>
}
