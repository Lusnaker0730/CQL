import { AxiosError } from 'axios'

/**
 * PAT-238 — the backend refused a re-publish because the measure was edited on the measure page
 * since the last publish (409, error label "Publish Conflict"); the builder asks the author and
 * retries with force.
 */
export function isPublishConflict(error: unknown): boolean {
  if (!(error instanceof AxiosError) || error.response?.status !== 409) return false
  const data = error.response.data as { error?: unknown } | undefined
  return data?.error === 'Publish Conflict'
}
