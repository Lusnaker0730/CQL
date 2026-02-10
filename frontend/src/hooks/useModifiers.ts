import { useQuery } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useModifiers(inputType?: string) {
  return useQuery({
    queryKey: ['authoring', 'modifiers', inputType],
    queryFn: () => authoringApi.getModifiers(inputType),
    staleTime: Infinity,
  })
}
