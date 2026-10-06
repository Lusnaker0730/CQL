import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import { render } from '../../../test/test-utils'
import ProtectedRoute from '../ProtectedRoute'

const signedOut = { auth: { user: null, token: null, isAuthenticated: false, loading: false } }
const signedIn = { auth: { user: { username: 'test', role: 'USER' }, token: 'jwt-token', isAuthenticated: true, loading: false } }

describe('ProtectedRoute', () => {
  it('should redirect to login when not authenticated', () => {
    render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>,
      { preloadedState: signedOut }
    )
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('should render children when authenticated', () => {
    render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>,
      { preloadedState: signedIn }
    )
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  // PAT-251 — a signed-out visitor sees the public home page at exactly `/`; every other protected
  // path still goes to /login; a signed-in user at `/` gets the app.
  function App() {
    return (
      <Routes>
        <Route path="/login" element={<div>login form</div>} />
        <Route
          path="/*"
          element={
            <ProtectedRoute publicHome={<div>public home</div>}>
              <div>app shell</div>
            </ProtectedRoute>
          }
        />
      </Routes>
    )
  }

  it('PAT-251: shows the public home page to a signed-out visitor at /', () => {
    render(<App />, { route: '/', preloadedState: signedOut })
    expect(screen.getByText('public home')).toBeInTheDocument()
    expect(screen.queryByText('login form')).not.toBeInTheDocument()
  })

  it('PAT-251: still redirects a signed-out visitor on any other protected path to /login', () => {
    render(<App />, { route: '/measures', preloadedState: signedOut })
    expect(screen.getByText('login form')).toBeInTheDocument()
    expect(screen.queryByText('public home')).not.toBeInTheDocument()
  })

  it('PAT-251: renders the app for a signed-in user at /', () => {
    render(<App />, { route: '/', preloadedState: signedIn })
    expect(screen.getByText('app shell')).toBeInTheDocument()
  })
})
