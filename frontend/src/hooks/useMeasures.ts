import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../api'

export function useMeasuresByOwner(username: string | undefined) {
  return useQuery({
    queryKey: ['measures', 'owner', username],
    queryFn: () => measureApi.getMeasuresByOwner(username!),
    enabled: !!username,
  })
}

export function useSharedMeasures(username: string | undefined) {
  return useQuery({
    queryKey: ['measures', 'shared', username],
    queryFn: () => measureApi.getSharedMeasures(username!),
    enabled: !!username,
  })
}

export function useShareMeasure() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetUsername }: { id: number; targetUsername: string }) =>
      measureApi.shareMeasure(id, targetUsername),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useUnshareMeasure() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetUsername }: { id: number; targetUsername: string }) =>
      measureApi.unshareMeasure(id, targetUsername),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useTransferMeasureOwnership() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, newOwner }: { id: number; newOwner: string }) =>
      measureApi.transferMeasureOwnership(id, newOwner),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useSetMeasureAccessLevel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, accessLevel }: { id: number; accessLevel: string }) =>
      measureApi.setMeasureAccessLevel(id, accessLevel),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useSubmitForReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => measureApi.submitForReview(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useApproveMeasure() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => measureApi.approveMeasure(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useRejectMeasure() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => measureApi.rejectMeasure(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useRetireMeasure() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => measureApi.retireMeasure(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measures'] }),
  })
}

export function useMeasureAuditTrail(measureId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['measure-audit', measureId],
    queryFn: () => measureApi.getAuditTrail(measureId!),
    enabled: enabled && !!measureId,
  })
}
