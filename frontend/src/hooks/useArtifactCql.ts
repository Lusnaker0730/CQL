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

export function useExportArtifactZip() {
  return useMutation({
    mutationFn: (id: number) => authoringApi.exportZip(id),
  })
}

export function useFormatCql() {
  return useMutation({
    mutationFn: (cql: string) => authoringApi.formatCql(cql),
  })
}
