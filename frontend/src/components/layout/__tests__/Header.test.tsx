import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import Header from '../Header'

const authenticatedState = {
  auth: {
    user: { username: 'testuser', role: 'USER' },
    token: 'jwt-token',
    isAuthenticated: true,
    loading: false,
  },
}

describe('Header', () => {
  it('should render CQL Platform title', () => {
    render(<Header />, { preloadedState: authenticatedState })
    expect(screen.getByText('app.title')).toBeInTheDocument()
  })

  it('should render navigation items', () => {
    render(<Header />, { preloadedState: authenticatedState })
    expect(screen.getByText('nav.editor')).toBeInTheDocument()
    expect(screen.getByText('nav.cdsHooks')).toBeInTheDocument()
    expect(screen.getByText('nav.measures')).toBeInTheDocument()
    expect(screen.getByText('nav.fhirBrowser')).toBeInTheDocument()
  })

  it('should display username when logged in', () => {
    render(<Header />, { preloadedState: authenticatedState })
    expect(screen.getByText('testuser')).toBeInTheDocument()
  })

  // BUG-131: platform-level nav (users / tenants / clinic applications) must be
  // hidden from a clinic-tenant ADMIN; audit is tenant-scoped so it stays visible.
  const clinicAdminState = {
    auth: {
      user: { username: 'clinicadmin', role: 'ADMIN', platformOperator: false },
      token: 'jwt',
      isAuthenticated: true,
      loading: false,
    },
  }
  const platformAdminState = {
    auth: {
      user: { username: 'admin', role: 'ADMIN', platformOperator: true },
      token: 'jwt',
      isAuthenticated: true,
      loading: false,
    },
  }

  it('hides platform-level nav from a clinic-tenant admin', () => {
    render(<Header />, { preloadedState: clinicAdminState })
    expect(screen.queryByText('nav.users')).not.toBeInTheDocument()
    expect(screen.queryByText('nav.tenants')).not.toBeInTheDocument()
    expect(screen.queryByText('nav.clinicApplications')).not.toBeInTheDocument()
    // Audit is tenant-scoped, so an ADMIN still gets it
    expect(screen.getByText('nav.auditLog')).toBeInTheDocument()
  })

  it('shows platform-level nav to the platform operator', () => {
    render(<Header />, { preloadedState: platformAdminState })
    expect(screen.getByText('nav.users')).toBeInTheDocument()
    expect(screen.getByText('nav.tenants')).toBeInTheDocument()
    expect(screen.getByText('nav.clinicApplications')).toBeInTheDocument()
    expect(screen.getByText('nav.auditLog')).toBeInTheDocument()
  })
})
