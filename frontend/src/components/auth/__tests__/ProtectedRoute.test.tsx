import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import ProtectedRoute from '../ProtectedRoute'

describe('ProtectedRoute', () => {
  it('should redirect to login when not authenticated', () => {
    render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>,
      {
        preloadedState: {
          auth: { user: null, token: null, isAuthenticated: false, loading: false },
        },
      }
    )
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('should render children when authenticated', () => {
    render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>,
      {
        preloadedState: {
          auth: {
            user: { username: 'test', role: 'USER' },
            token: 'jwt-token',
            isAuthenticated: true,
            loading: false,
          },
        },
      }
    )
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })
})
