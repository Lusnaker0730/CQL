import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '../../test/test-utils'
import OktaCallbackPage from '../OktaCallbackPage'

vi.mock('../../api', () => ({
  authApi: {
    oktaCallback: vi.fn(),
  },
}))

import { authApi } from '../../api'

describe('OktaCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('should show error when code is missing', async () => {
    render(<OktaCallbackPage />, { route: '/auth/okta/callback' })

    await waitFor(() => {
      expect(screen.getByText('auth.ssoMissingCode')).toBeInTheDocument()
    })
  })

  it('should show state mismatch error when CSRF check fails', async () => {
    sessionStorage.setItem('okta_state', 'stored-state')
    render(<OktaCallbackPage />, { route: '/auth/okta/callback?code=abc&state=wrong-state' })

    await waitFor(() => {
      expect(screen.getByText('auth.ssoStateMismatch')).toBeInTheDocument()
    })
  })

  it('should show processing spinner during valid callback', () => {
    sessionStorage.setItem('okta_state', 'valid-state')
    vi.mocked(authApi.oktaCallback).mockReturnValue(new Promise(() => {}))
    render(<OktaCallbackPage />, { route: '/auth/okta/callback?code=abc&state=valid-state' })

    expect(screen.getByText('auth.ssoProcessing')).toBeInTheDocument()
  })

  it('should show error on API failure', async () => {
    sessionStorage.setItem('okta_state', 'valid-state')
    vi.mocked(authApi.oktaCallback).mockRejectedValue({
      response: { data: { error: 'Invalid code' } },
    })
    render(<OktaCallbackPage />, { route: '/auth/okta/callback?code=abc&state=valid-state' })

    await waitFor(() => {
      expect(screen.getByText('Invalid code')).toBeInTheDocument()
    })
  })

  it('should show back to sign in button on error', async () => {
    render(<OktaCallbackPage />, { route: '/auth/okta/callback' })

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /auth.backToSignIn/i })).toBeInTheDocument()
    })
  })

  it('should clean up sessionStorage after processing', async () => {
    sessionStorage.setItem('okta_state', 'valid-state')
    sessionStorage.setItem('okta_nonce', 'nonce-123')
    sessionStorage.setItem('okta_redirect_uri', 'http://localhost/callback')

    vi.mocked(authApi.oktaCallback).mockResolvedValue({
      token: 'jwt-token',
      username: 'user1',
      role: 'USER',
      forcePasswordChange: false,
    })

    render(<OktaCallbackPage />, { route: '/auth/okta/callback?code=abc&state=valid-state' })

    await waitFor(() => {
      expect(sessionStorage.getItem('okta_state')).toBeNull()
      expect(sessionStorage.getItem('okta_nonce')).toBeNull()
      expect(sessionStorage.getItem('okta_redirect_uri')).toBeNull()
    })
  })

  it('should call API with correct parameters', async () => {
    sessionStorage.setItem('okta_state', 'valid-state')
    sessionStorage.setItem('okta_nonce', 'nonce-123')
    sessionStorage.setItem('okta_redirect_uri', 'http://localhost/callback')

    vi.mocked(authApi.oktaCallback).mockResolvedValue({
      token: 'jwt-token',
      username: 'user1',
      role: 'USER',
      forcePasswordChange: false,
    })

    render(<OktaCallbackPage />, { route: '/auth/okta/callback?code=abc&state=valid-state' })

    await waitFor(() => {
      expect(authApi.oktaCallback).toHaveBeenCalledWith({
        code: 'abc',
        redirectUri: 'http://localhost/callback',
        nonce: 'nonce-123',
      })
    })
  })
})
