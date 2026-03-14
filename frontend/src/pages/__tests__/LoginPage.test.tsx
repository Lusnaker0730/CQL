import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import LoginPage from '../LoginPage'

describe('LoginPage', () => {
  it('should render login form', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByText('app.title')).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.password/i)).toBeInTheDocument()
  })

  it('should render sign in button', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByRole('button', { name: /auth.signIn/i })).toBeInTheDocument()
  })

  it('should have register toggle link', () => {
    render(<LoginPage />, { route: '/login' })
    expect(screen.getByText('auth.noAccount')).toBeInTheDocument()
  })

  it('should switch to register mode', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { route: '/login' })

    await user.click(screen.getByText('auth.noAccount'))

    expect(screen.getByRole('button', { name: /auth.register/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.email/i)).toBeInTheDocument()
  })

  it('should switch back to login mode', async () => {
    const user = userEvent.setup()
    render(<LoginPage />, { route: '/login' })

    await user.click(screen.getByText('auth.noAccount'))
    await user.click(screen.getByText('auth.alreadyHaveAccount'))

    expect(screen.getByRole('button', { name: /auth.signIn/i })).toBeInTheDocument()
  })
})
