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
})
