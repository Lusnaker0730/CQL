import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authoringApi } from '../api'
import type { ArtifactRequest } from '../types/authoring'
import { useInvalidatingMutation } from './useInvalidatingMutation'

const ARTIFACTS_KEY = ['authoring', 'artifacts'] as const

export function useArtifacts() {
  return useQuery({
    queryKey: ARTIFACTS_KEY,
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

export const useCreateArtifact = () =>
  useInvalidatingMutation(
    (request: ArtifactRequest) => authoringApi.createArtifact(request),
    ARTIFACTS_KEY,
  )

export function useUpdateArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: ArtifactRequest }) =>
      authoringApi.updateArtifact(id, request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ARTIFACTS_KEY })
      queryClient.invalidateQueries({ queryKey: ['authoring', 'artifact', variables.id] })
    },
  })
}

export const useDeleteArtifact = () =>
  useInvalidatingMutation((id: number) => authoringApi.deleteArtifact(id), ARTIFACTS_KEY)

export const useDuplicateArtifact = () =>
  useInvalidatingMutation((id: number) => authoringApi.duplicateArtifact(id), ARTIFACTS_KEY)
