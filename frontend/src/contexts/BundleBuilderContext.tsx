import { createContext, useContext, useReducer, type ReactNode } from 'react'
import type { BundleEntry, BundleBuilderState, BundleBuilderAction } from '../types'

const initialState: BundleBuilderState = {
  entries: [],
  activeEntryId: null,
}

function bundleReducer(state: BundleBuilderState, action: BundleBuilderAction): BundleBuilderState {
  switch (action.type) {
    case 'ADD_ENTRY':
      return {
        ...state,
        entries: [...state.entries, action.payload],
        activeEntryId: action.payload.id,
      }
    case 'REMOVE_ENTRY':
      return {
        ...state,
        entries: state.entries.filter((e) => e.id !== action.payload),
        activeEntryId: state.activeEntryId === action.payload ? null : state.activeEntryId,
      }
    case 'UPDATE_ENTRY':
      return {
        ...state,
        entries: state.entries.map((e) =>
          e.id === action.payload.id ? { ...e, resourceData: action.payload.resourceData } : e
        ),
      }
    case 'SET_ACTIVE_ENTRY':
      return { ...state, activeEntryId: action.payload }
    case 'LOAD_FROM_JSON':
      return {
        entries: action.payload,
        activeEntryId: action.payload.length > 0 ? action.payload[0].id : null,
      }
    default:
      return state
  }
}

interface BundleBuilderContextValue {
  state: BundleBuilderState
  dispatch: React.Dispatch<BundleBuilderAction>
}

const BundleBuilderContext = createContext<BundleBuilderContextValue | null>(null)

export function BundleBuilderProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(bundleReducer, initialState)
  return (
    <BundleBuilderContext.Provider value={{ state, dispatch }}>
      {children}
    </BundleBuilderContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useBundleBuilder() {
  const ctx = useContext(BundleBuilderContext)
  if (!ctx) throw new Error('useBundleBuilder must be used within BundleBuilderProvider')
  return ctx
}

/** Serialize entries to a FHIR Bundle JSON string */
// eslint-disable-next-line react-refresh/only-export-components
export function serializeToBundle(entries: BundleEntry[]): string {
  const bundle = {
    resourceType: 'Bundle',
    type: 'collection',
    entry: entries.map((e) => ({
      resource: {
        resourceType: e.resourceType,
        id: e.resourceData.id || e.id,
        ...Object.fromEntries(
          Object.entries(e.resourceData).filter(([k]) => k !== 'id')
        ),
      },
    })),
  }
  return JSON.stringify(bundle, null, 2)
}

/** Parse a FHIR Bundle JSON string to BundleEntry[] */
// eslint-disable-next-line react-refresh/only-export-components
export function parseFromBundle(json: string): BundleEntry[] {
  const parsed = JSON.parse(json)
  if (parsed.resourceType !== 'Bundle' || !Array.isArray(parsed.entry)) {
    return []
  }
  return parsed.entry
    .filter((e: Record<string, unknown>) => e.resource && typeof e.resource === 'object')
    .map((e: Record<string, unknown>) => {
      const resource = e.resource as Record<string, unknown>
      const resourceType = (resource.resourceType as string) || 'Unknown'
      const id = (resource.id as string) || crypto.randomUUID()
      const { resourceType: _rt, ...rest } = resource
      return {
        id,
        resourceType,
        resourceData: { id, ...rest },
      } as BundleEntry
    })
}
