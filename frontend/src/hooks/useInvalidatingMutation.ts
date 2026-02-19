import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query'

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
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: invalidateKey })
    },
  })
}
