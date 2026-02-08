import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { userPrefsApi } from '../api'

export function useFavorites() {
  return useQuery({
    queryKey: ['favorites'],
    queryFn: () => userPrefsApi.getFavorites(),
    retry: false,
  })
}

export function useRecent() {
  return useQuery({
    queryKey: ['recent'],
    queryFn: () => userPrefsApi.getRecent(),
    retry: false,
  })
}

export function useAddFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: number) => userPrefsApi.addFavorite(libraryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
    },
  })
}

export function useRemoveFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: number) => userPrefsApi.removeFavorite(libraryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
    },
  })
}

export function useAddRecent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: number) => userPrefsApi.addRecent(libraryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recent'] })
    },
  })
}

export function useClearRecent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => userPrefsApi.clearRecent(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recent'] })
    },
  })
}
