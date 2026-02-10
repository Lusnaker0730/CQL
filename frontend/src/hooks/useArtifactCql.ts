import { useMutation } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useGenerateArtifactCql() {
  return useMutation({
    mutationFn: (id: number) => authoringApi.generateCql(id),
  })
}

export function useGenerateArtifactElm() {
  return useMutation({
    mutationFn: (id: number) => authoringApi.generateElm(id),
  })
}

export function useValidateArtifactCql() {
  return useMutation({
    mutationFn: (id: number) => authoringApi.validateArtifactCql(id),
  })
}
