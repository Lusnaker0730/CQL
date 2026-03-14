import { useMutation, useQuery } from '@tanstack/react-query'
import { measureApi } from '../api'
import { useInvalidatingMutation } from './useInvalidatingMutation'
import { STALE_30S } from '../constants/queryConstants'

const MEASURES_KEY = ['measures'] as const

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

export const useShareMeasure = () =>
  useInvalidatingMutation(
    ({ id, targetUsername }: { id: number; targetUsername: string }) =>
      measureApi.shareMeasure(id, targetUsername),
    MEASURES_KEY,
  )

export const useUnshareMeasure = () =>
  useInvalidatingMutation(
    ({ id, targetUsername }: { id: number; targetUsername: string }) =>
      measureApi.unshareMeasure(id, targetUsername),
    MEASURES_KEY,
  )

export const useTransferMeasureOwnership = () =>
  useInvalidatingMutation(
    ({ id, newOwner }: { id: number; newOwner: string }) =>
      measureApi.transferMeasureOwnership(id, newOwner),
    MEASURES_KEY,
  )

export const useSetMeasureAccessLevel = () =>
  useInvalidatingMutation(
    ({ id, accessLevel }: { id: number; accessLevel: string }) =>
      measureApi.setMeasureAccessLevel(id, accessLevel),
    MEASURES_KEY,
  )

export const useLockMeasure = () =>
  useInvalidatingMutation((id: number) => measureApi.lockMeasure(id), MEASURES_KEY)

export const useUnlockMeasure = () =>
  useInvalidatingMutation((id: number) => measureApi.unlockMeasure(id), MEASURES_KEY)

export const useSubmitForReview = () =>
  useInvalidatingMutation((id: number) => measureApi.submitForReview(id), MEASURES_KEY)

export const useApproveMeasure = () =>
  useInvalidatingMutation((id: number) => measureApi.approveMeasure(id), MEASURES_KEY)

export const useRejectMeasure = () =>
  useInvalidatingMutation(({ id, reason }: { id: number; reason?: string }) => measureApi.rejectMeasure(id, reason), MEASURES_KEY)

export const useRetireMeasure = () =>
  useInvalidatingMutation((id: number) => measureApi.retireMeasure(id), MEASURES_KEY)

export function useValidateMeasure() {
  return useMutation({
    mutationFn: (id: number) => measureApi.validateMeasure(id),
  })
}

export function useQuickValidateMeasure() {
  return useMutation({
    mutationFn: (id: number) => measureApi.quickValidateMeasure(id),
  })
}

export function useMeasureAuditTrail(measureId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['measure-audit', measureId],
    queryFn: () => measureApi.getAuditTrail(measureId!),
    enabled: enabled && !!measureId,
  })
}

export function useDashboard() {
  return useQuery({
    queryKey: ['measure-dashboard'],
    queryFn: () => measureApi.getDashboard(),
    staleTime: STALE_30S,
  })
}

export function useBatchEvaluate() {
  return useMutation({
    mutationFn: (request: { measureIds: number[]; fhirServerUrl?: string; periodStart?: string; periodEnd?: string }) =>
      measureApi.batchEvaluate(request),
  })
}
