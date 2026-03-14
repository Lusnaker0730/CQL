import { useQuery } from '@tanstack/react-query'
import { authoringApi } from '../api'

export function useTemplates() {
  return useQuery({
    queryKey: ['authoring', 'templates'],
    queryFn: () => authoringApi.getTemplates(),
    staleTime: Infinity,
  })
}
