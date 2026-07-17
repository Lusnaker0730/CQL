import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import TenantUsersPage from '../TenantUsersPage'

vi.mock('../../api', () => ({
  tenantUserApi: {
    listUsers: vi.fn(),
    createUser: vi.fn(),
    resetUserPassword: vi.fn(),
    updateUserRole: vi.fn(),
    updateUserEnabled: vi.fn(),
  },
}))

vi.mock('../../utils/validation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../utils/validation')>()),
  getStoredUsername: vi.fn(() => 'clinicadmin'),
}))

import { tenantUserApi } from '../../api'

const mockUsers = [
  {
    id: 1,
    username: 'clinicadmin',
    email: 'admin@clinic.tw',
    role: 'ADMIN',
    enabled: true,
    forcePasswordChange: false,
    authProvider: 'LOCAL',
    createdAt: '2026-01-01',
  },
  {
    id: 2,
    username: 'nurse1',
    email: 'nurse1@clinic.tw',
    role: 'DEPARTMENT_ADMIN',
    enabled: true,
    forcePasswordChange: false,
    authProvider: 'LOCAL',
    createdAt: '2026-02-01',
  },
]

describe('TenantUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(tenantUserApi.listUsers).mockResolvedValue(mockUsers)
  })

  it('renders the staff-management title and its own tenant users', async () => {
    render(<TenantUsersPage />)
    await waitFor(() => {
      expect(screen.getByText('tenantUsers.title')).toBeInTheDocument()
      expect(screen.getByText('clinicadmin')).toBeInTheDocument()
      expect(screen.getByText('nurse1')).toBeInTheDocument()
    })
  })

  it('marks the current admin with the "you" chip', async () => {
    render(<TenantUsersPage />)
    await waitFor(() => {
      expect(screen.getByText('tenantUsers.youChip')).toBeInTheDocument()
    })
  })

  it('supports the DEPARTMENT_ADMIN role (all three roles, unlike the platform admin page)', async () => {
    render(<TenantUsersPage />)
    // nurse1 is a DEPARTMENT_ADMIN — the role select renders its selected label, proving
    // the tenant surface handles the third role the platform createUser cannot.
    await waitFor(() => {
      expect(screen.getByText('tenantUsers.roles.departmentAdmin')).toBeInTheDocument()
    })
  })

  it('shows a setup-link dialog after resetting a password', async () => {
    vi.mocked(tenantUserApi.resetUserPassword).mockResolvedValue({
      setupLink: 'https://twcql.com/reset-password?token=abc',
    })
    const user = userEvent.setup()
    render(<TenantUsersPage />)

    await waitFor(() => expect(screen.getAllByText('tenantUsers.resetPasswordButton').length).toBeGreaterThan(0))
    await user.click(screen.getAllByText('tenantUsers.resetPasswordButton')[0])

    await waitFor(() => {
      expect(screen.getByText('tenantUsers.resetDialog.title')).toBeInTheDocument()
      expect(screen.getByDisplayValue('https://twcql.com/reset-password?token=abc')).toBeInTheDocument()
    })
  })

  it('shows the empty state when the tenant has no staff', async () => {
    vi.mocked(tenantUserApi.listUsers).mockResolvedValue([])
    render(<TenantUsersPage />)
    await waitFor(() => {
      expect(screen.getByText('tenantUsers.noUsersFound')).toBeInTheDocument()
    })
  })

  const openCreateAndFill = async (user: ReturnType<typeof userEvent.setup>, password: string) => {
    await waitFor(() => expect(screen.getByText('tenantUsers.createUser')).toBeInTheDocument())
    await user.click(screen.getByText('tenantUsers.createUser'))
    await user.type(screen.getByLabelText(/usernameLabel/i), 'newstaff')
    await user.type(screen.getByLabelText(/passwordLabel/i), password)
  }

  it('keeps the create button disabled for a password that lacks upper/lower/digit', async () => {
    const user = userEvent.setup()
    render(<TenantUsersPage />)
    // 9 chars but all lowercase+digit, no uppercase — long enough to have previously
    // passed the button, but the server would 400.
    await openCreateAndFill(user, 'weakpass1')
    expect(screen.getByText('tenantUsers.createDialog.createButton').closest('button')).toBeDisabled()
  })

  it('shows the validation reason (not "username taken") on a 400', async () => {
    vi.mocked(tenantUserApi.createUser).mockRejectedValue({
      response: { status: 400, data: { details: ['password: must contain at least one lowercase letter, one uppercase letter, and one digit'] } },
    })
    const user = userEvent.setup()
    render(<TenantUsersPage />)
    await openCreateAndFill(user, 'Passw0rd')
    await user.click(screen.getByText('tenantUsers.createDialog.createButton'))
    await waitFor(() => {
      expect(screen.getByText(/must contain at least one lowercase/)).toBeInTheDocument()
    })
    expect(screen.queryByText('tenantUsers.errors.usernameExists')).not.toBeInTheDocument()
  })

  it('shows "username taken" only on a 409 conflict', async () => {
    vi.mocked(tenantUserApi.createUser).mockRejectedValue({
      response: { status: 409, data: { message: 'User with username already exists' } },
    })
    const user = userEvent.setup()
    render(<TenantUsersPage />)
    await openCreateAndFill(user, 'Passw0rd')
    await user.click(screen.getByText('tenantUsers.createDialog.createButton'))
    await waitFor(() => {
      expect(screen.getByText('tenantUsers.errors.usernameExists')).toBeInTheDocument()
    })
  })
})
