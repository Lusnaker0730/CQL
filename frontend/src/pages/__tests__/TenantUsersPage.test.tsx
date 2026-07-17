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
})
