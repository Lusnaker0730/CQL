import { useQuery } from '@tanstack/react-query'
import { authoringApi } from '../api'
import type { TwcoreCatalogEntry, TwcoreCodeSystem } from '../types/authoring'

export function useTwcoreCatalog(resourceType?: string) {
  return useQuery<TwcoreCatalogEntry[]>({
    queryKey: ['twcore-catalog', resourceType],
    queryFn: () => authoringApi.getTwcoreCatalog(resourceType),
    staleTime: Infinity,
    enabled: !!resourceType,
  })
}

export function useTwcoreFullCatalog() {
  return useQuery<TwcoreCatalogEntry[]>({
    queryKey: ['twcore-catalog', '__all__'],
    queryFn: () => authoringApi.getTwcoreCatalog(),
    staleTime: Infinity,
  })
}

export function useTwcoreCodeSystems() {
  return useQuery<TwcoreCodeSystem[]>({
    queryKey: ['twcore-code-systems'],
    queryFn: () => authoringApi.getTwcoreCodeSystems(),
    staleTime: Infinity,
  })
}
