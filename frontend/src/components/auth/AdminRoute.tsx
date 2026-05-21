import { Navigate } from 'react-router-dom'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

/**
 * Roles that the backend's {@code SecurityConfig:191} grants access to
 * {@code /api/admin/**}. Keep this list in sync with the backend rule and with
 * any class-level {@code @PreAuthorize} we add to admin controllers
 * (PAT-145).
 */
export type AdminRouteRole = 'ADMIN' | 'DEPARTMENT_ADMIN'

interface AdminRouteProps {
  children: React.ReactNode
  /**
   * Which authenticated roles may render the wrapped page. Defaults to
   * {@code ['ADMIN']} for the strictest pages (user CRUD); pages that the
   * backend allows {@code DEPARTMENT_ADMIN} on (e.g. audit log dashboard) must
   * pass {@code ['ADMIN', 'DEPARTMENT_ADMIN']} so the frontend doesn't redirect
   * a user the backend would have authorized — PAT-147.
   */
  allowedRoles?: AdminRouteRole[]
}

export default function AdminRoute({ children, allowedRoles = ['ADMIN'] }: AdminRouteProps) {
  const user = useSelector((state: RootState) => state.auth.user)
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (!user?.role || !allowedRoles.includes(user.role as AdminRouteRole)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
