import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query'

export interface InvalidatingMutationOptions<TVariables> {
  onError?: (error: Error, variables: TVariables) => void
}

/**
 * Creates a mutation hook that automatically invalidates queries on success.
 * Eliminates the repetitive pattern of useQueryClient + useMutation + invalidateQueries.
 *
 * @example
 * export const useShareLibrary = () =>
 *   useInvalidatingMutation(
 *     ({ id, targetUsername }: { id: string; targetUsername: string }) =>
 *       cqlApi.shareLibrary(id, targetUsername),
 *     ['libraries']
 *   )
 */
export function useInvalidatingMutation<TVariables, TData = unknown>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  invalidateKey: QueryKey,
  options?: InvalidatingMutationOptions<TVariables>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: invalidateKey })
    },
    onError: options?.onError,
  })
}
