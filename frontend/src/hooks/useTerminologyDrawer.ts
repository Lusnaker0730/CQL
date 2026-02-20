import { useContext } from 'react'
import { TerminologyDrawerContext } from '../contexts/TerminologyDrawerContext'

export function useTerminologyDrawer() {
  return useContext(TerminologyDrawerContext)
}
