import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authoringApi } from '../api'
import type { ArtifactRequest } from '../types/authoring'

export function useArtifacts() {
  return useQuery({
    queryKey: ['authoring', 'artifacts'],
    queryFn: () => authoringApi.listArtifacts(),
  })
}

export function useArtifact(id: number | undefined) {
  return useQuery({
    queryKey: ['authoring', 'artifact', id],
    queryFn: () => authoringApi.getArtifact(id!),
    enabled: !!id,
  })
}

export function useCreateArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ArtifactRequest) => authoringApi.createArtifact(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['authoring', 'artifacts'] }),
  })
}

export function useUpdateArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: ArtifactRequest }) =>
      authoringApi.updateArtifact(id, request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'artifacts'] })
      queryClient.invalidateQueries({ queryKey: ['authoring', 'artifact', variables.id] })
    },
  })
}

export function useDeleteArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => authoringApi.deleteArtifact(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['authoring', 'artifacts'] }),
  })
}

export function useDuplicateArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => authoringApi.duplicateArtifact(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['authoring', 'artifacts'] }),
  })
}
