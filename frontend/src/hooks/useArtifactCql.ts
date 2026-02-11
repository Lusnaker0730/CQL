import { useMutation } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useGenerateArtifactCql() {
  return useMutation({
    mutationFn: ({ id, fhirVersion }: { id: number; fhirVersion?: string }) =>
      authoringApi.generateCql(id, fhirVersion),
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
