import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../api'
import type { MeasureReviewCommentRequest } from '../types'

/** PAT-262: the review (reviewers, decisions, comment threads) of one measure. */
export const reviewKey = (measureId: number | undefined) => ['measures', measureId, 'review'] as const

export function useMeasureReview(measureId: number | undefined, enabled = true) {
  return useQuery({
    queryKey: reviewKey(measureId),
    queryFn: () => measureApi.getReview(measureId!),
    enabled: !!measureId && enabled,
    staleTime: 0,
  })
}

/**
 * Every review mutation refreshes the review itself and the measures cache (the approval readiness
 * and the measure's status / sharing follow reviewer decisions).
 */
function useReviewMutation<TVariables, TData>(measureId: number | undefined, mutationFn: (v: TVariables) => Promise<TData>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewKey(measureId) })
      queryClient.invalidateQueries({ queryKey: ['measures'] })
    },
  })
}

export const useSetReviewers = (measureId: number | undefined) =>
  useReviewMutation(measureId, (usernames: string[]) => measureApi.setReviewers(measureId!, usernames))

export const useReviewDecision = (measureId: number | undefined) =>
  useReviewMutation(measureId, ({ decision, comment }: { decision: 'approved' | 'changes-requested'; comment?: string }) =>
    measureApi.recordReviewDecision(measureId!, decision, comment))

export const useAddReviewComment = (measureId: number | undefined) =>
  useReviewMutation(measureId, (request: MeasureReviewCommentRequest) => measureApi.addReviewComment(measureId!, request))

export const useEditReviewComment = (measureId: number | undefined) =>
  useReviewMutation(measureId, ({ commentId, body }: { commentId: number; body: string }) =>
    measureApi.editReviewComment(measureId!, commentId, body))

export const useResolveReviewComment = (measureId: number | undefined) =>
  useReviewMutation(measureId, ({ commentId, resolved }: { commentId: number; resolved: boolean }) =>
    measureApi.resolveReviewComment(measureId!, commentId, resolved))
