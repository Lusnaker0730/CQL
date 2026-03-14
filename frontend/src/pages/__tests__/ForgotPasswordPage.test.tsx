import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import ForgotPasswordPage from '../ForgotPasswordPage'

vi.mock('../../api', () => ({
  authApi: {
    forgotPassword: vi.fn(),
  },
}))

import { authApi } from '../../api'

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render the form', () => {
    render(<ForgotPasswordPage />, { route: '/forgot-password' })
    expect(screen.getByText('auth.resetPassword')).toBeInTheDocument()
    expect(screen.getByText('auth.resetPasswordSubtitle')).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.emailAddress/i)).toBeInTheDocument()
  })

  it('should render send reset link button', () => {
    render(<ForgotPasswordPage />, { route: '/forgot-password' })
    expect(screen.getByRole('button', { name: /auth.sendResetLink/i })).toBeInTheDocument()
  })

  it('should render back to sign in link', () => {
    render(<ForgotPasswordPage />, { route: '/forgot-password' })
    expect(screen.getByText('auth.backToSignIn')).toBeInTheDocument()
  })

  it('should show success after valid submit', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ForgotPasswordPage />, { route: '/forgot-password' })

    await user.type(screen.getByLabelText(/auth.emailAddress/i), 'test@example.com')
    await user.click(screen.getByRole('button', { name: /auth.sendResetLink/i }))

    await waitFor(() => {
      expect(screen.getByText('auth.resetEmailSent')).toBeInTheDocument()
    })
  })

  it('should show success even when API fails (anti-enumeration)', async () => {
    vi.mocked(authApi.forgotPassword).mockRejectedValue(new Error('not found'))
    const user = userEvent.setup()
    render(<ForgotPasswordPage />, { route: '/forgot-password' })

    await user.type(screen.getByLabelText(/auth.emailAddress/i), 'bad@example.com')
    await user.click(screen.getByRole('button', { name: /auth.sendResetLink/i }))

    await waitFor(() => {
      expect(screen.getByText('auth.resetEmailSent')).toBeInTheDocument()
    })
  })

  it('should show back to sign in link after success', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ForgotPasswordPage />, { route: '/forgot-password' })

    await user.type(screen.getByLabelText(/auth.emailAddress/i), 'test@example.com')
    await user.click(screen.getByRole('button', { name: /auth.sendResetLink/i }))

    await waitFor(() => {
      expect(screen.getByText('auth.backToSignIn')).toBeInTheDocument()
    })
  })

  it('should call authApi.forgotPassword with email', async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ForgotPasswordPage />, { route: '/forgot-password' })

    await user.type(screen.getByLabelText(/auth.emailAddress/i), 'test@example.com')
    await user.click(screen.getByRole('button', { name: /auth.sendResetLink/i }))

    await waitFor(() => {
      expect(authApi.forgotPassword).toHaveBeenCalledWith({ email: 'test@example.com' })
    })
  })
})
