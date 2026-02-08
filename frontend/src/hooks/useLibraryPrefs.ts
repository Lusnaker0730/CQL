import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSelector } from 'react-redux'
import { userPrefsApi } from '../api'
import type { RootState } from '../store'

export function useFavorites() {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)
  return useQuery({
    queryKey: ['favorites'],
    queryFn: () => userPrefsApi.getFavorites(),
    enabled: isAuthenticated,
    retry: false,
  })
}

export function useRecent() {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)
  return useQuery({
    queryKey: ['recent'],
    queryFn: () => userPrefsApi.getRecent(),
    enabled: isAuthenticated,
    retry: false,
  })
}

export function useAddFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: string) => userPrefsApi.addFavorite(libraryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
    },
  })
}

export function useRemoveFavorite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: string) => userPrefsApi.removeFavorite(libraryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] })
    },
  })
}

export function useAddRecent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libraryId: string) => userPrefsApi.addRecent(libraryId),
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
