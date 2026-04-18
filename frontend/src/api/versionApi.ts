import { api } from './client'
import type { AxiosResponse } from 'axios'

export interface VersionInfo {
  startupTime: string
  commitSha: string
  version: string
}

export const versionApi = {
  /**
   * Fetches backend deploy-identity info. Unauthenticated; safe to call before login
   * and during token-refresh races. Response changes on every backend restart (via
   * {@code startupTime}) — the SPA uses this signal to invalidate its React Query
   * cache after a deploy, avoiding stale-schema bugs.
   */
  getVersion: () =>
    api.get<VersionInfo>('/version').then((r: AxiosResponse<VersionInfo>) => r.data),
}
