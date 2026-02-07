import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cdsHooksApi } from '../api'
import type { CdsRequest, CdsServiceConfigRequest, CdsFeedbackRequest, CdsSandboxRequest } from '../types'

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

export function useSubmitCdsFeedback() {
  return useMutation({
    mutationFn: ({ serviceId, feedback }: { serviceId: string; feedback: CdsFeedbackRequest }) =>
      cdsHooksApi.submitFeedback(serviceId, feedback),
  })
}

export function useCdsServiceVersions(serviceName: string | null) {
  return useQuery({
    queryKey: ['cds-service-versions', serviceName],
    queryFn: () => cdsHooksApi.getServiceVersions(serviceName!),
    enabled: !!serviceName,
  })
}

export function useRollbackCdsService() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ serviceName, version }: { serviceName: string; version: number }) =>
      cdsHooksApi.rollbackService(serviceName, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cds-services'] })
      queryClient.invalidateQueries({ queryKey: ['cds-service-configs'] })
      queryClient.invalidateQueries({ queryKey: ['cds-service-versions'] })
    },
  })
}

export function useCdsAnalytics() {
  return useQuery({
    queryKey: ['cds-analytics'],
    queryFn: () => cdsHooksApi.getAllAnalytics(),
    refetchInterval: 30000,
  })
}

export function useSandboxInvoke() {
  return useMutation({
    mutationFn: ({ serviceId, request }: { serviceId: string; request: CdsSandboxRequest }) =>
      cdsHooksApi.sandboxInvoke(serviceId, request),
  })
}
