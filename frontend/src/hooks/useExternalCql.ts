import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useExternalCqlList(artifactId: number) {
  return useQuery({
    queryKey: ['external-cql', artifactId],
    queryFn: () => authoringApi.listExternalCql(artifactId),
  })
}

export function useUploadExternalCql(artifactId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => authoringApi.uploadExternalCql(artifactId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['external-cql', artifactId] })
    },
  })
}

export function useDeleteExternalCql(artifactId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libId: number) => authoringApi.deleteExternalCql(artifactId, libId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['external-cql', artifactId] })
    },
  })
}
