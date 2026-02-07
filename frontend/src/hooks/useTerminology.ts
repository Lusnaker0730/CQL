import { useQuery, useMutation } from '@tanstack/react-query'
import { fhirApi } from '../api'

export function useSearchValueSets(title?: string) {
  return useQuery({
    queryKey: ['valueSets', title],
    queryFn: () => fhirApi.searchValueSets(title),
    enabled: !!title && title.length >= 2,
  })
}

export function useExpandValueSet() {
  return useMutation({
    mutationFn: ({ url, filter }: { url: string; filter?: string }) =>
      fhirApi.expandValueSet(url, filter),
  })
}

export function useLookupCode() {
  return useMutation({
    mutationFn: ({ system, code }: { system: string; code: string }) =>
      fhirApi.lookupCode(system, code),
  })
}

export function useValidateCode() {
  return useMutation({
    mutationFn: ({ system, code, valueSetUrl }: { system: string; code: string; valueSetUrl: string }) =>
      fhirApi.validateCode(system, code, valueSetUrl),
  })
}
