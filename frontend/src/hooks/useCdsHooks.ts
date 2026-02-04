import { useQuery, useMutation } from '@tanstack/react-query'
import { cdsHooksApi } from '../api'
import type { CdsRequest } from '../types'

export function useCdsServices() {
  return useQuery({
    queryKey: ['cds-services'],
    queryFn: () => cdsHooksApi.discover(),
  })
}

export function useInvokeCdsService() {
  return useMutation({
    mutationFn: ({ serviceId, request }: { serviceId: string; request: CdsRequest }) =>
      cdsHooksApi.invoke(serviceId, request),
  })
}
