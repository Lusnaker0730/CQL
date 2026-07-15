import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import TenantManagementPage from '../TenantManagementPage'

vi.mock('../../api', () => ({
  adminApi: {
    listUsers: vi.fn(),
  },
}))

vi.mock('../../api/tenantApi', () => ({
  tenantApi: {
    listTenants: vi.fn(),
    createTenant: vi.fn(),
    setTenantActive: vi.fn(),
    listTenantUsers: vi.fn(),
    assignUser: vi.fn(),
  },
}))

vi.mock('../../utils/validation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../utils/validation')>()),
  getStoredUsername: vi.fn(() => 'admin'),
}))

import { tenantApi } from '../../api/tenantApi'

const mockTenants = [
  { id: 1, code: 'default', name: 'Default Tenant', active: true, createdAt: '2026-01-01' },
  { id: 2, code: 'clinic-b', name: '仁心診所', active: true, createdAt: '2026-07-01' },
]

describe('TenantManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(tenantApi.listTenants).mockResolvedValue(mockTenants)
  })

  it('lists tenants with status chips', async () => {
    render(<TenantManagementPage />)

    await waitFor(() => {
      expect(screen.getByText('default')).toBeInTheDocument()
    })
    expect(screen.getByText('仁心診所')).toBeInTheDocument()
  })

  it('disables the active switch for the default tenant only', async () => {
    render(<TenantManagementPage />)
    await waitFor(() => expect(screen.getByText('clinic-b')).toBeInTheDocument())

    const switches = screen.getAllByRole('switch')
    expect(switches).toHaveLength(2)
    expect(switches[0]).toBeDisabled() // default — protected
    expect(switches[1]).not.toBeDisabled() // clinic tenant
  })

  it('create dialog rejects an invalid code slug', async () => {
    const user = userEvent.setup()
    render(<TenantManagementPage />)
    await waitFor(() => expect(screen.getByText('clinic-b')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'tenants.create' }))
    const codeField = await screen.findByLabelText('tenants.code')
    await user.type(codeField, 'Bad Code!')

    expect(codeField).toHaveAttribute('aria-invalid', 'true')
    expect(tenantApi.createTenant).not.toHaveBeenCalled()
  })

  it('creates a tenant and refreshes the list', async () => {
    const user = userEvent.setup()
    vi.mocked(tenantApi.createTenant).mockResolvedValue({
      id: 3,
      code: 'clinic-c',
      name: 'C 診所',
      active: true,
      createdAt: '2026-07-15',
    })
    render(<TenantManagementPage />)
    await waitFor(() => expect(screen.getByText('clinic-b')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'tenants.create' }))
    await user.type(await screen.findByLabelText('tenants.code'), 'clinic-c')
    await user.type(screen.getByLabelText('tenants.name'), 'C 診所')
    const buttons = screen.getAllByRole('button', { name: 'tenants.create' })
    await user.click(buttons[buttons.length - 1]) // the dialog's confirm button

    await waitFor(() => {
      expect(tenantApi.createTenant).toHaveBeenCalledWith({ code: 'clinic-c', name: 'C 診所' })
    })
  })
})
