import { api } from './client'
import type { PlatformStatus } from '../types'

// PAT-209: public platform status. Anonymous endpoint (permitAll); reports coarse reachability.
export const statusApi = {
  getStatus: async (): Promise<PlatformStatus> => {
    const { data } = await api.get('/status')
    return data
  },
}
