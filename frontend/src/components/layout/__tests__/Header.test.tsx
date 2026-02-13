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
    expect(screen.getByText('TWCORE CQL Platform')).toBeInTheDocument()
  })

  it('should render navigation items', () => {
    render(<Header />, { preloadedState: authenticatedState })
    expect(screen.getByText('Editor')).toBeInTheDocument()
    expect(screen.getByText('CDS Hooks')).toBeInTheDocument()
    expect(screen.getByText('Measures')).toBeInTheDocument()
    expect(screen.getByText('FHIR Browser')).toBeInTheDocument()
  })

  it('should display username when logged in', () => {
    render(<Header />, { preloadedState: authenticatedState })
    expect(screen.getByText('testuser')).toBeInTheDocument()
  })
})
