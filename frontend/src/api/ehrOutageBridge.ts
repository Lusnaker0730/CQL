/**
 * PAT-110: bridge between the Axios response interceptor (plain JS module
 * scope) and React's EhrOutageContext (state living in the provider tree).
 *
 * The interceptor can't call {@link React.useContext} directly, so the
 * provider registers a listener here at mount; the interceptor calls it on
 * every FHIR_UPSTREAM_UNAVAILABLE envelope. If no listener is registered
 * (tests, headless flows) the envelope is dropped silently.
 */

export interface UpstreamEnvelope {
  connectionId: number | null
  connectionName: string | null
  reason: 'TIMEOUT' | 'CIRCUIT_BREAKER_OPEN' | 'UPSTREAM_5XX' | 'CONNECTION_REFUSED' | 'OTHER'
  retryAfterSeconds: number
}

type Listener = (envelope: UpstreamEnvelope) => void

let listener: Listener | null = null

export function registerEhrOutageListener(l: Listener): () => void {
  listener = l
  return () => {
    if (listener === l) listener = null
  }
}

export function notifyEhrOutage(envelope: UpstreamEnvelope): void {
  if (listener) listener(envelope)
}

// Exported only for tests to isolate listener state between cases.
export function _resetListener(): void {
  listener = null
}
