import { useContext } from 'react'
import { EhrOutageContext, type EhrOutageContextType } from '../contexts/EhrOutageContext'

export function useEhrOutage(): EhrOutageContextType {
  const ctx = useContext(EhrOutageContext)
  if (!ctx) {
    throw new Error('useEhrOutage must be used within an EhrOutageProvider')
  }
  return ctx
}
