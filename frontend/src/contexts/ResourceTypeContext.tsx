import { createContext, useContext } from 'react'

const ResourceTypeContext = createContext<string | undefined>(undefined)

export const ResourceTypeProvider = ResourceTypeContext.Provider

export function useCurrentResourceType() {
  return useContext(ResourceTypeContext)
}
