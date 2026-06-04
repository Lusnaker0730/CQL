const DEFAULT_BACKEND_CANDIDATES = ['http://localhost:8080', 'http://localhost:8081']
const PROBE_TIMEOUT_MS = 500

export interface VersionProbeResponse {
  startupTime: string
  commitSha: string
  version: string
}

function isVersionProbeResponse(value: unknown): value is VersionProbeResponse {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return typeof candidate.startupTime === 'string'
    && typeof candidate.commitSha === 'string'
    && typeof candidate.version === 'string'
}

async function probeBackendTarget(candidate: string, fetchImpl: typeof fetch): Promise<boolean> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS)

  try {
    const response = await fetchImpl(new URL('/api/version', candidate), {
      method: 'GET',
      signal: controller.signal,
    })

    if (!response.ok) {
      return false
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (!contentType.includes('application/json')) {
      return false
    }

    const body = await response.json().catch(() => null)
    return isVersionProbeResponse(body)
  } catch {
    return false
  } finally {
    clearTimeout(timeoutId)
  }
}

export async function resolveBackendTarget(fetchImpl: typeof fetch = fetch): Promise<string> {
  const explicitTarget = process.env.VITE_BACKEND_PROXY_TARGET?.trim()
  if (explicitTarget) {
    return explicitTarget
  }

  for (const candidate of DEFAULT_BACKEND_CANDIDATES) {
    if (await probeBackendTarget(candidate, fetchImpl)) {
      return candidate
    }
  }

  return 'http://localhost:8080'
}