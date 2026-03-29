import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cqlApi } from '../api'

const CQL_LIBRARIES_KEY = ['cql-libraries'] as const

// ===== Queries =====

export function useCqlLibraries(search?: string) {
  return useQuery({
    queryKey: [...CQL_LIBRARIES_KEY, search] as const,
    queryFn: () => cqlApi.getLibraries(search),
  })
}

export function useCqlLibrary(id: string | null | undefined) {
  return useQuery({
    queryKey: ['cql-library', id] as const,
    queryFn: () => cqlApi.getLibrary(id!),
    enabled: !!id,
  })
}

export function useCqlLibraryVersions(name: string | null | undefined) {
  return useQuery({
    queryKey: ['cql-library-versions', name] as const,
    queryFn: () => cqlApi.getLibraryVersions(name!),
    enabled: !!name,
  })
}

export function useCqlLibraryHistory(name: string | null | undefined) {
  return useQuery({
    queryKey: ['cql-library-history', name] as const,
    queryFn: () => cqlApi.getLibraryHistory(name!),
    enabled: !!name,
  })
}

// ===== Mutations =====

export function useCreateCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ cql, description }: { cql: string; description?: string }) =>
      cqlApi.createLibrary(cql, description),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
    },
  })
}

export function useUpdateCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cql, description }: { id: string; cql: string; description?: string }) =>
      cqlApi.updateLibrary(id, cql, description),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
      queryClient.invalidateQueries({ queryKey: ['cql-library', variables.id] })
    },
  })
}

export function useDeleteCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => cqlApi.deleteLibrary(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
    },
  })
}

export function useCreateCqlLibraryVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, type }: { name: string; type: string }) =>
      cqlApi.createLibraryVersion(name, type),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
    },
  })
}

export function useShareCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetUsername }: { id: string; targetUsername: string }) =>
      cqlApi.shareLibrary(id, targetUsername),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
      queryClient.invalidateQueries({ queryKey: ['cql-library', variables.id] })
    },
  })
}

export function useUnshareCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetUsername }: { id: string; targetUsername: string }) =>
      cqlApi.unshareLibrary(id, targetUsername),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
      queryClient.invalidateQueries({ queryKey: ['cql-library', variables.id] })
    },
  })
}

export function useTransferCqlLibraryOwnership() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, newOwner }: { id: string; newOwner: string }) =>
      cqlApi.transferOwnership(id, newOwner),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
      queryClient.invalidateQueries({ queryKey: ['cql-library', variables.id] })
    },
  })
}

export function useSetCqlLibraryAccessLevel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, accessLevel }: { id: string; accessLevel: string }) =>
      cqlApi.setAccessLevel(id, accessLevel),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
      queryClient.invalidateQueries({ queryKey: ['cql-library', variables.id] })
    },
  })
}

export function useExportCqlLibrary() {
  return useMutation({
    mutationFn: (id: string) => cqlApi.exportFhirLibrary(id),
  })
}

export function useImportCqlLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fhirLibrary: unknown) => cqlApi.importFhirLibrary(fhirLibrary),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CQL_LIBRARIES_KEY })
    },
  })
}
