import { createContext, useContext, useMemo, type ReactNode } from 'react'

/**
 * PAT-237 — what an element deep inside a builder tree may refer to by id: the artifact's base
 * elements and parameters, and whether the output library declares a Measurement Period (eCQM).
 * The CDS and eCQM workspaces provide it; the function-call argument editor consumes it. The
 * default (no provider) is an empty scope, so an element card still renders on its own.
 */
export interface ArtifactScope {
  baseElements: Array<{ uniqueId: string; name: string; returnType?: string }>
  parameters: Array<{ uniqueId: string; name: string; type?: string }>
  hasMeasurementPeriod: boolean
}

const EMPTY_SCOPE: ArtifactScope = { baseElements: [], parameters: [], hasMeasurementPeriod: false }

const ArtifactScopeContext = createContext<ArtifactScope>(EMPTY_SCOPE)

interface ProviderProps {
  baseElements?: Array<{ uniqueId: string; name: string; returnType?: string }>
  parameters?: Array<{ uniqueId?: string; name?: string; type?: string }>
  hasMeasurementPeriod: boolean
  children: ReactNode
}

export function ArtifactScopeProvider({ baseElements, parameters, hasMeasurementPeriod, children }: ProviderProps) {
  const value = useMemo<ArtifactScope>(() => ({
    baseElements: (baseElements ?? []).filter((b) => b.uniqueId && b.name),
    parameters: (parameters ?? [])
      .filter((p): p is { uniqueId: string; name: string; type?: string } => !!p.uniqueId && !!p.name),
    hasMeasurementPeriod,
  }), [baseElements, parameters, hasMeasurementPeriod])
  return <ArtifactScopeContext.Provider value={value}>{children}</ArtifactScopeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- context + provider intentionally co-located
export function useArtifactScope(): ArtifactScope {
  return useContext(ArtifactScopeContext)
}
