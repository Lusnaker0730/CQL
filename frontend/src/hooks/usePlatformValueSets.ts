import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { valueSetApi } from '../api'
import { STALE_30S } from '../constants/queryConstants'
import type { PlatformValueSetInput } from '../types'

const KEY = 'platform-value-sets'

/** PAT-230 — every version of this tenant's own value sets (without codes). */
export function usePlatformValueSets(search?: string) {
  return useQuery({
    queryKey: [KEY, 'list', search ?? ''],
    queryFn: () => valueSetApi.list(search),
    staleTime: STALE_30S,
  })
}

/** One version with its codes; disabled until an id is given. */
export function usePlatformValueSet(id: number | null) {
  return useQuery({
    queryKey: [KEY, 'detail', id],
    queryFn: () => valueSetApi.get(id as number),
    enabled: id != null,
    staleTime: 0,
  })
}

/**
 * All writes. Each one invalidates the list, the details AND the shared value set search
 * (`['valueSets', …]`, used by every picker) — a new or activated value set has to show up there
 * without a reload.
 */
export function usePlatformValueSetMutations() {
  const queryClient = useQueryClient()
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: [KEY] })
    void queryClient.invalidateQueries({ queryKey: ['valueSets'] })
  }
  return {
    create: useMutation({ mutationFn: (body: PlatformValueSetInput) => valueSetApi.create(body), onSuccess: refresh }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: number; body: PlatformValueSetInput }) => valueSetApi.update(id, body),
      onSuccess: refresh,
    }),
    createVersion: useMutation({
      mutationFn: ({ id, version }: { id: number; version: string }) => valueSetApi.createVersion(id, version),
      onSuccess: refresh,
    }),
    activate: useMutation({ mutationFn: (id: number) => valueSetApi.activate(id), onSuccess: refresh }),
    retire: useMutation({ mutationFn: (id: number) => valueSetApi.retire(id), onSuccess: refresh }),
    remove: useMutation({ mutationFn: (id: number) => valueSetApi.remove(id), onSuccess: refresh }),
    importFhir: useMutation({ mutationFn: (resource: unknown) => valueSetApi.importFhir(resource), onSuccess: refresh }),
  }
}
