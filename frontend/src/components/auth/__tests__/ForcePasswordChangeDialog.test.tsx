import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { render } from '../../../test/test-utils'
import ForcePasswordChangeDialog from '../ForcePasswordChangeDialog'

// test-utils does NOT initialize i18next; mock useTranslation so labels and
// button text resolve to their i18n keys (matched by getByLabelText/getByRole).
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

vi.mock('../../../api', () => ({
  authApi: {
    changePassword: vi.fn(),
  },
}))

import { authApi } from '../../../api'

describe('ForcePasswordChangeDialog — PAT-147', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function setup() {
    return render(<ForcePasswordChangeDialog open />, {
      preloadedState: {
        auth: {
          user: { username: 'alice', role: 'USER', forcePasswordChange: true },
          token: 'jwt',
          isAuthenticated: true,
          loading: false,
        },
      },
    })
  }

  it('shows three password fields and a submit button', () => {
    setup()
    expect(screen.getByLabelText(/auth\.currentPassword/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/auth\.newPassword/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/auth\.confirmNewPassword/i)).toBeInTheDocument()
  })

  it('blocks submit when fields are empty (validation)', async () => {
    const user = userEvent.setup()
    setup()

    await user.click(screen.getByRole('button', { name: /auth\.changePassword/i }))
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('blocks submit when new and confirm passwords differ', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(screen.getByLabelText(/auth\.currentPassword/i), 'oldpw')
    await user.type(screen.getByLabelText(/auth\.newPassword/i), 'NewPassword123!')
    await user.type(screen.getByLabelText(/auth\.confirmNewPassword/i), 'Mismatch456!')
    await user.click(screen.getByRole('button', { name: /auth\.changePassword/i }))

    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('calls authApi.changePassword on valid submit', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    setup()

    await user.type(screen.getByLabelText(/auth\.currentPassword/i), 'oldpw')
    await user.type(screen.getByLabelText(/auth\.newPassword/i), 'NewPassword123!')
    await user.type(screen.getByLabelText(/auth\.confirmNewPassword/i), 'NewPassword123!')
    await user.click(screen.getByRole('button', { name: /auth\.changePassword/i }))

    await waitFor(() =>
      expect(authApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'oldpw',
        newPassword: 'NewPassword123!',
      })
    )
  })

  it('PAT-147 regression: password fields are cleared after successful submit', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue(undefined as never)
    const user = userEvent.setup()
    setup()

    const current = screen.getByLabelText(/auth\.currentPassword/i) as HTMLInputElement
    const next = screen.getByLabelText(/auth\.newPassword/i) as HTMLInputElement
    const confirm = screen.getByLabelText(/auth\.confirmNewPassword/i) as HTMLInputElement

    await user.type(current, 'oldpw')
    await user.type(next, 'NewPassword123!')
    await user.type(confirm, 'NewPassword123!')
    await user.click(screen.getByRole('button', { name: /auth\.changePassword/i }))

    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalled())

    // Plain-text passwords must not linger in component state after success.
    expect(current.value).toBe('')
    expect(next.value).toBe('')
    expect(confirm.value).toBe('')
  })

  it('surfaces server error via extractApiError on submit failure', async () => {
    // extractApiError requires a real AxiosError instance (instanceof check).
    const axiosErr = new AxiosError('Request failed')
    axiosErr.response = {
      data: { error: 'Current password is incorrect' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {} as never,
    } as AxiosResponse
    vi.mocked(authApi.changePassword).mockRejectedValueOnce(axiosErr)
    const user = userEvent.setup()
    setup()

    await user.type(screen.getByLabelText(/auth\.currentPassword/i), 'wrongpw')
    await user.type(screen.getByLabelText(/auth\.newPassword/i), 'NewPassword123!')
    await user.type(screen.getByLabelText(/auth\.confirmNewPassword/i), 'NewPassword123!')
    await user.click(screen.getByRole('button', { name: /auth\.changePassword/i }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/current password is incorrect/i)
    )
  })
})
