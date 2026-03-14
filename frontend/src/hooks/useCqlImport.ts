import { useMutation, useQuery } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useImportCql() {
  return useMutation({
    mutationFn: (cql: string) => authoringApi.importCql(cql),
  })
}

export function useQueryBuilderResources() {
  return useQuery({
    queryKey: ['query-builder-resources'],
    queryFn: () => authoringApi.getQueryBuilderResources(),
    staleTime: Infinity,
  })
}

export function useQueryBuilderOperators(type?: string) {
  return useQuery({
    queryKey: ['query-builder-operators', type],
    queryFn: () => authoringApi.getQueryBuilderOperators(type),
    staleTime: Infinity,
  })
}
