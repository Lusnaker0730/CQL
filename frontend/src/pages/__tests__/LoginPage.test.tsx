import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import LoginPage from '../LoginPage'

describe('LoginPage', () => {
  it('should render login form', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByText('TWCORE CQL Platform')).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
  })

  it('should render sign in button', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('should have register toggle link', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByText(/don't have an account/i)).toBeInTheDocument()
  })

  it('should switch to register mode', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { route: '/login' })

    await user.click(screen.getByText(/don't have an account/i))

    expect(screen.getByRole('button', { name: /register/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
  })

  it('should switch back to login mode', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { route: '/login' })

    await user.click(screen.getByText(/don't have an account/i))
    await user.click(screen.getByText(/already have an account/i))

    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })
})
