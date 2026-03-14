import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../api'

export function useFhirResourceTypes() {
  return useQuery({
    queryKey: ['fhir-resource-types'],
    queryFn: fhirApi.getResourceTypes,
    staleTime: Infinity,
  })
}

export function useFhirMetadata(resourceType: string | undefined) {
  return useQuery({
    queryKey: ['fhir-metadata', resourceType],
    queryFn: () => fhirApi.getResourceMetadata(resourceType!),
    enabled: !!resourceType,
    staleTime: Infinity,
  })
}
