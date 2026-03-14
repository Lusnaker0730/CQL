import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import ResetPasswordPage from '../ResetPasswordPage'

vi.mock('../../api', () => ({
  authApi: {
    resetPassword: vi.fn(),
  },
}))

import { authApi } from '../../api'

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should show invalid link error when no token', () => {
    render(<ResetPasswordPage />, { route: '/reset-password' })
    expect(screen.getByText('auth.invalidResetLink')).toBeInTheDocument()
  })

  it('should show request new link when no token', () => {
    render(<ResetPasswordPage />, { route: '/reset-password' })
    expect(screen.getByText('auth.requestNewResetLink')).toBeInTheDocument()
  })

  it('should render form when token is present', () => {
    render(<ResetPasswordPage />, { route: '/reset-password?token=abc123' })
    expect(screen.getByText('auth.setNewPassword')).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.newPassword/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/auth.confirmPassword/i)).toBeInTheDocument()
  })

  it('should render reset button', () => {
    render(<ResetPasswordPage />, { route: '/reset-password?token=abc123' })
    expect(screen.getByRole('button', { name: /auth.resetPasswordButton/i })).toBeInTheDocument()
  })

  it('should render back to sign in link', () => {
    render(<ResetPasswordPage />, { route: '/reset-password?token=abc123' })
    expect(screen.getByText('auth.backToSignIn')).toBeInTheDocument()
  })

  it('should show success message on successful reset', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ResetPasswordPage />, { route: '/reset-password?token=abc123' })

    await user.type(screen.getByLabelText(/auth.newPassword/i), 'NewPass1234')
    await user.type(screen.getByLabelText(/auth.confirmPassword/i), 'NewPass1234')
    await user.click(screen.getByRole('button', { name: /auth.resetPasswordButton/i }))

    await waitFor(() => {
      expect(screen.getByText('auth.passwordResetSuccess')).toBeInTheDocument()
    })
  })

  it('should show go to sign in button after success', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ResetPasswordPage />, { route: '/reset-password?token=abc123' })

    await user.type(screen.getByLabelText(/auth.newPassword/i), 'NewPass1234')
    await user.type(screen.getByLabelText(/auth.confirmPassword/i), 'NewPass1234')
    await user.click(screen.getByRole('button', { name: /auth.resetPasswordButton/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /auth.goToSignIn/i })).toBeInTheDocument()
    })
  })

  it('should show error on API failure', async () => {
    vi.mocked(authApi.resetPassword).mockRejectedValue({
      response: { data: { error: 'Token expired' } },
    })
    const user = userEvent.setup()
    render(<ResetPasswordPage />, { route: '/reset-password?token=expired' })

    await user.type(screen.getByLabelText(/auth.newPassword/i), 'NewPass1234')
    await user.type(screen.getByLabelText(/auth.confirmPassword/i), 'NewPass1234')
    await user.click(screen.getByRole('button', { name: /auth.resetPasswordButton/i }))

    await waitFor(() => {
      expect(screen.getByText('Token expired')).toBeInTheDocument()
    })
  })

  it('should call API with token and password', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ResetPasswordPage />, { route: '/reset-password?token=mytoken' })

    await user.type(screen.getByLabelText(/auth.newPassword/i), 'NewPass1234')
    await user.type(screen.getByLabelText(/auth.confirmPassword/i), 'NewPass1234')
    await user.click(screen.getByRole('button', { name: /auth.resetPasswordButton/i }))

    await waitFor(() => {
      expect(authApi.resetPassword).toHaveBeenCalledWith({ token: 'mytoken', newPassword: 'NewPass1234' })
    })
  })
})
