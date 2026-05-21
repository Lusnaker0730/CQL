import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import AdminRoute from '../AdminRoute'

describe('AdminRoute — PAT-147 role parameterization', () => {
  it('redirects to /login when not authenticated', () => {
    render(
      <AdminRoute>
        <div>Admin Content</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: { user: null, token: null, isAuthenticated: false, loading: false },
        },
      }
    )
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument()
  })

  it('renders children for ADMIN users (default allowedRoles)', () => {
    render(
      <AdminRoute>
        <div>Admin Content</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: {
            user: { username: 'alice', role: 'ADMIN' },
            token: 'jwt',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.getByText('Admin Content')).toBeInTheDocument()
  })

  it('redirects USER role away from default ADMIN-only pages', () => {
    render(
      <AdminRoute>
        <div>Admin Content</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: {
            user: { username: 'bob', role: 'USER' },
            token: 'jwt',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument()
  })

  it('PAT-147 regression: DEPARTMENT_ADMIN redirected from default ADMIN-only pages', () => {
    // Default allowedRoles=['ADMIN'] — a department admin must NOT see
    // user-CRUD pages even though backend grants them /api/admin/** generally.
    render(
      <AdminRoute>
        <div>User CRUD</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: {
            user: { username: 'dept', role: 'DEPARTMENT_ADMIN' },
            token: 'jwt',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.queryByText('User CRUD')).not.toBeInTheDocument()
  })

  it('PAT-147 regression: DEPARTMENT_ADMIN allowed when allowedRoles includes it', () => {
    // Audit dashboard route: backend allows DEPARTMENT_ADMIN, frontend must not be stricter.
    render(
      <AdminRoute allowedRoles={['ADMIN', 'DEPARTMENT_ADMIN']}>
        <div>Audit Dashboard</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: {
            user: { username: 'dept', role: 'DEPARTMENT_ADMIN' },
            token: 'jwt',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.getByText('Audit Dashboard')).toBeInTheDocument()
  })

  it('redirects when user object exists but role is missing', () => {
    render(
      <AdminRoute>
        <div>Admin Content</div>
      </AdminRoute>,
      {
        preloadedState: {
          auth: {
            // @ts-expect-error — exercise the no-role guard
            user: { username: 'alice' },
            token: 'jwt',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument()
  })
})
