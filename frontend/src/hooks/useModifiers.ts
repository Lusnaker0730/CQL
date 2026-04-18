import { useQuery } from '@tanstack/react-query'
import { authoringApi } from '../api'
import { STALE_5M } from '../constants/queryConstants'

/**
 * Modifier catalog is stable within a deploy but changes when the backend ships a new
 * version. {@code staleTime: Infinity} was a footgun — users who kept a tab open
 * across a deploy saw the old schema indefinitely (the #230 refactor changed the wire
 * shape of {@code ModifierDefinition}, exposing this). {@code VersionCheckProvider}
 * invalidates this on backend restart; {@code STALE_5M} is a defense-in-depth fallback
 * for the case where the version poll hasn't fired yet.
 */
export function useModifiers(inputType?: string) {
  return useQuery({
    queryKey: ['authoring', 'modifiers', inputType],
    queryFn: () => authoringApi.getModifiers(inputType),
    staleTime: STALE_5M,
  })
}
