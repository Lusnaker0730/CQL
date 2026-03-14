import { useQuery } from '@tanstack/react-query'
import { fhirApi } from '../api'

export function useIgPackages() {
  return useQuery({
    queryKey: ['ig-packages'],
    queryFn: () => fhirApi.getIgPackages(),
  })
}

export function useIgProfiles(resourceType?: string, search?: string) {
  return useQuery({
    queryKey: ['ig-profiles', resourceType, search],
    queryFn: () => fhirApi.browseProfiles(resourceType, search),
  })
}

export function useIgValueSets(search?: string) {
  return useQuery({
    queryKey: ['ig-valuesets', search],
    queryFn: () => fhirApi.browseIgValueSets(search),
  })
}

export function useIgCodeSystems(search?: string) {
  return useQuery({
    queryKey: ['ig-codesystems', search],
    queryFn: () => fhirApi.browseIgCodeSystems(search),
  })
}
