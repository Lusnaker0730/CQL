import { Navigate, useLocation } from 'react-router-dom'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

interface ProtectedRouteProps {
  children: React.ReactNode
  /**
   * PAT-251: what a signed-out visitor sees at exactly `/` — the public home page — instead of
   * being bounced to the login form. Every other protected path still redirects to /login.
   */
  publicHome?: React.ReactNode
}

export default function ProtectedRoute({ children, publicHome }: ProtectedRouteProps) {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)
  const location = useLocation()

  if (!isAuthenticated) {
    if (publicHome && location.pathname === '/') {
      return <>{publicHome}</>
    }
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
