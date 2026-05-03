import { createContext, useContext, useReducer, type ReactNode } from 'react'
import type { BundleEntry, BundleBuilderState, BundleBuilderAction } from '../types'
import { FHIR_BUNDLE_TYPE } from '../constants/bundle'
import { generateId } from '../utils/validation'

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
    type: FHIR_BUNDLE_TYPE,
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

/**
 * Parse a FHIR Bundle JSON string to BundleEntry[]. Returns an empty array on
 * malformed JSON, missing/wrong resourceType, or missing entry array — PAT-149,
 * previously the JSON.parse would throw straight to the caller (typically a
 * file-upload handler) and crash the whole tab.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function parseFromBundle(json: string): BundleEntry[] {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch (e) {
    console.warn('parseFromBundle: input is not valid JSON', e)
    return []
  }
  if (
    !parsed
    || typeof parsed !== 'object'
    || (parsed as Record<string, unknown>).resourceType !== 'Bundle'
    || !Array.isArray((parsed as Record<string, unknown>).entry)
  ) {
    return []
  }
  const entries = (parsed as { entry: unknown[] }).entry
  return entries
    .filter((e): e is Record<string, unknown> =>
      typeof e === 'object' && e !== null
      && typeof (e as Record<string, unknown>).resource === 'object'
      && (e as Record<string, unknown>).resource !== null)
    .map((e) => {
      const resource = e.resource as Record<string, unknown>
      const resourceType = (resource.resourceType as string) || 'Unknown'
      const id = (resource.id as string) || generateId()
      const { resourceType: _rt, ...rest } = resource
      return {
        id,
        resourceType,
        resourceData: { id, ...rest },
      } as BundleEntry
    })
}
