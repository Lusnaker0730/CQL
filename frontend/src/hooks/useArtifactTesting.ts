import { useMutation } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useTestArtifact() {
  return useMutation({
    mutationFn: ({ id, patientIds, fhirServerUrl }: { id: number; patientIds: string[]; fhirServerUrl: string }) =>
      authoringApi.testArtifact(id, patientIds, fhirServerUrl),
  })
}

export function useDeployCdsService() {
  return useMutation({
    mutationFn: ({ id, serviceId, hook }: { id: number; serviceId: string; hook: string }) =>
      authoringApi.deployCdsService(id, serviceId, hook),
  })
}

export function useSaveAsLibrary() {
  return useMutation({
    mutationFn: (id: number) => authoringApi.saveAsLibrary(id),
  })
}
