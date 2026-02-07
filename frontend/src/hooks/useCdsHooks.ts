import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cdsHooksApi } from '../api'
import type { CdsRequest, CdsServiceConfigRequest } from '../types'

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

export function useCdsServiceConfigs() {
  return useQuery({
    queryKey: ['cds-service-configs'],
    queryFn: () => cdsHooksApi.getAllServices(),
  })
}

export function useCreateCdsService() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CdsServiceConfigRequest) => cdsHooksApi.createService(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cds-services'] })
      queryClient.invalidateQueries({ queryKey: ['cds-service-configs'] })
    },
  })
}

export function useUpdateCdsService() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CdsServiceConfigRequest }) =>
      cdsHooksApi.updateService(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cds-services'] })
      queryClient.invalidateQueries({ queryKey: ['cds-service-configs'] })
    },
  })
}

export function useDeleteCdsService() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => cdsHooksApi.deleteService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cds-services'] })
      queryClient.invalidateQueries({ queryKey: ['cds-service-configs'] })
    },
  })
}
