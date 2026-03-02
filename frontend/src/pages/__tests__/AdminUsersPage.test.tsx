import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import AdminUsersPage from '../AdminUsersPage'

vi.mock('../../api', () => ({
  adminApi: {
    listUsers: vi.fn(),
    createUser: vi.fn(),
    resetUserPassword: vi.fn(),
    updateUserRole: vi.fn(),
    updateUserEnabled: vi.fn(),
  },
}))

vi.mock('../../utils/validation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../utils/validation')>()),
  getStoredUsername: vi.fn(() => 'admin'),
}))

import { adminApi } from '../../api'

const mockUsers = [
  {
    id: 1,
    username: 'admin',
    email: 'admin@example.com',
    role: 'ADMIN',
    enabled: true,
    forcePasswordChange: false,
    authProvider: 'LOCAL',
    createdAt: '2024-01-01',
  },
  {
    id: 2,
    username: 'user1',
    email: 'user1@example.com',
    role: 'USER',
    enabled: true,
    forcePasswordChange: false,
    authProvider: 'LOCAL',
    createdAt: '2024-02-01',
  },
  {
    id: 3,
    username: 'okta-user',
    email: 'okta@example.com',
    role: 'USER',
    enabled: true,
    forcePasswordChange: false,
    authProvider: 'OKTA',
    createdAt: '2024-03-01',
  },
]

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(adminApi.listUsers).mockResolvedValue(mockUsers)
  })

  it('should render page title', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('users.title')).toBeInTheDocument()
    })
  })

  it('should render create user button', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('users.createUser')).toBeInTheDocument()
    })
  })

  it('should render table headers', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('users.columns.username')).toBeInTheDocument()
      expect(screen.getByText('users.columns.email')).toBeInTheDocument()
      expect(screen.getByText('users.columns.role')).toBeInTheDocument()
      expect(screen.getByText('users.columns.status')).toBeInTheDocument()
    })
  })

  it('should render users after loading', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('admin')).toBeInTheDocument()
      expect(screen.getByText('user1')).toBeInTheDocument()
      expect(screen.getByText('okta-user')).toBeInTheDocument()
    })
  })

  it('should show "you" chip for current user', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('users.youChip')).toBeInTheDocument()
    })
  })

  it('should show OKTA chip for SSO users', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('auth.authProviderOkta')).toBeInTheDocument()
    })
  })

  it('should open create user dialog', async () => {
    const user = userEvent.setup()
    render(<AdminUsersPage />)

    await waitFor(() => expect(screen.getByText('users.createUser')).toBeInTheDocument())
    await user.click(screen.getByText('users.createUser'))

    expect(screen.getByText('users.createDialog.title')).toBeInTheDocument()
  })

  it('should show create dialog form fields', async () => {
    const user = userEvent.setup()
    render(<AdminUsersPage />)

    await waitFor(() => expect(screen.getByText('users.createUser')).toBeInTheDocument())
    await user.click(screen.getByText('users.createUser'))

    expect(screen.getByLabelText(/users.createDialog.usernameLabel/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/users.createDialog.passwordLabel/i)).toBeInTheDocument()
  })

  it('should show no users message when list is empty', async () => {
    vi.mocked(adminApi.listUsers).mockResolvedValue([])
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('users.noUsersFound')).toBeInTheDocument()
    })
  })

  it('should show user email', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
      expect(screen.getByText('user1@example.com')).toBeInTheDocument()
    })
  })

  it('should show loading state', () => {
    vi.mocked(adminApi.listUsers).mockReturnValue(new Promise(() => {}))
    render(<AdminUsersPage />)

    // Should show the page title immediately but not users
    expect(screen.getByText('users.title')).toBeInTheDocument()
  })

  it('should not show reset password for OKTA users', async () => {
    render(<AdminUsersPage />)

    await waitFor(() => {
      // There should be reset buttons for admin and user1 but not okta-user
      const resetButtons = screen.getAllByText('users.resetPasswordButton')
      expect(resetButtons).toHaveLength(2) // admin and user1 only
    })
  })
})
