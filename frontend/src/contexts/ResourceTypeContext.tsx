import { createContext, useContext } from 'react'

const ResourceTypeContext = createContext<string | undefined>(undefined)

export const ResourceTypeProvider = ResourceTypeContext.Provider

// eslint-disable-next-line react-refresh/only-export-components
export function useCurrentResourceType() {
  return useContext(ResourceTypeContext)
}
