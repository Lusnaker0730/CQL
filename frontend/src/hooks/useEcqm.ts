import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ecqmApi } from '../api'
import type { EcqmArtifactRequest } from '../types/ecqm'

const ECQM_KEY = ['ecqm', 'artifacts'] as const

export function useEcqmArtifacts() {
  return useQuery({
    queryKey: ECQM_KEY,
    queryFn: () => ecqmApi.listArtifacts(),
  })
}

export function useEcqmArtifact(id: number | undefined) {
  return useQuery({
    queryKey: ['ecqm', 'artifact', id],
    queryFn: () => ecqmApi.getArtifact(id!),
    enabled: !!id,
  })
}

export function useCreateEcqmArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: EcqmArtifactRequest) => ecqmApi.createArtifact(request),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ECQM_KEY }) },
  })
}

export function useUpdateEcqmArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: EcqmArtifactRequest }) =>
      ecqmApi.updateArtifact(id, request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ECQM_KEY })
      queryClient.invalidateQueries({ queryKey: ['ecqm', 'artifact', variables.id] })
    },
  })
}

export function useDeleteEcqmArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => ecqmApi.deleteArtifact(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ECQM_KEY }) },
  })
}

export function useDuplicateEcqmArtifact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => ecqmApi.duplicateArtifact(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ECQM_KEY }) },
  })
}

export function useGenerateEcqmCql() {
  return useMutation({ mutationFn: (id: number) => ecqmApi.generateCql(id) })
}

export function useValidateEcqmCql() {
  return useMutation({ mutationFn: (id: number) => ecqmApi.validateCql(id) })
}

export function usePublishEcqm() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => ecqmApi.publish(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ECQM_KEY }) },
  })
}

export function useEcqmTemplates() {
  return useQuery({
    queryKey: ['ecqm', 'templates'],
    queryFn: () => ecqmApi.getTemplates(),
    staleTime: 60_000,
  })
}

export function useEcqmModifiers() {
  return useQuery({
    queryKey: ['ecqm', 'modifiers'],
    queryFn: () => ecqmApi.getModifiers(),
    staleTime: 60_000,
  })
}

export function useEcqmScoringTypes() {
  return useQuery({
    queryKey: ['ecqm', 'scoring-types'],
    queryFn: () => ecqmApi.getScoringTypes(),
    staleTime: 60_000,
  })
}

// ===== External CQL Libraries =====

export function useEcqmExternalCqlList(artifactId: number) {
  return useQuery({
    queryKey: ['ecqm-external-cql', artifactId],
    queryFn: () => ecqmApi.listExternalCql(artifactId),
  })
}

export function useUploadEcqmExternalCql(artifactId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => ecqmApi.uploadExternalCql(artifactId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ecqm-external-cql', artifactId] })
    },
  })
}

export function useDeleteEcqmExternalCql(artifactId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (libId: number) => ecqmApi.deleteExternalCql(artifactId, libId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ecqm-external-cql', artifactId] })
    },
  })
}
