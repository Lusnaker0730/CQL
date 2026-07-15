import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import ClinicApplicationsAdminPage from '../ClinicApplicationsAdminPage'

vi.mock('../../api/clinicApplicationApi', () => ({
  clinicApplicationApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}))

import { clinicApplicationApi } from '../../api/clinicApplicationApi'

const pendingApp = {
  id: 5,
  clinicName: '仁心診所',
  tenantCode: 'clinic-b',
  adminUsername: 'drwang',
  adminEmail: 'dr@example.com',
  status: 'pending' as const,
  rejectionReason: null,
  reviewedBy: null,
  reviewedAt: null,
  createdTenantId: null,
  createdUserId: null,
  createdAt: '2026-07-15T10:00:00',
}

describe('ClinicApplicationsAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(clinicApplicationApi.list).mockResolvedValue([pendingApp])
  })

  it('lists pending applications with actions', async () => {
    render(<ClinicApplicationsAdminPage />)

    await waitFor(() => expect(screen.getByText('仁心診所')).toBeInTheDocument())
    expect(screen.getByText('clinic-b')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'applications.approve' })).toBeInTheDocument()
  })

  it('approve shows the one-time setup link dialog', async () => {
    const user = userEvent.setup()
    vi.mocked(clinicApplicationApi.approve).mockResolvedValue({
      application: { ...pendingApp, status: 'approved', createdTenantId: 9, createdUserId: 77 },
      setupLink: 'https://twcql.com/reset-password?token=RAWTOKEN',
    })
    render(<ClinicApplicationsAdminPage />)
    await waitFor(() => expect(screen.getByText('仁心診所')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'applications.approve' }))

    await waitFor(() => {
      expect(clinicApplicationApi.approve).toHaveBeenCalledWith(5)
    })
    expect(screen.getByText('applications.approvedTitle')).toBeInTheDocument()
    expect(
      screen.getByDisplayValue('https://twcql.com/reset-password?token=RAWTOKEN')
    ).toBeInTheDocument()
  })

  it('reject sends the reason', async () => {
    const user = userEvent.setup()
    vi.mocked(clinicApplicationApi.reject).mockResolvedValue({
      ...pendingApp,
      status: 'rejected',
      rejectionReason: 'dup',
    })
    render(<ClinicApplicationsAdminPage />)
    await waitFor(() => expect(screen.getByText('仁心診所')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'applications.reject' }))
    await user.type(await screen.findByLabelText('applications.rejectReason'), 'dup')
    const rejectButtons = screen.getAllByRole('button', { name: 'applications.reject' })
    await user.click(rejectButtons[rejectButtons.length - 1])

    await waitFor(() => {
      expect(clinicApplicationApi.reject).toHaveBeenCalledWith(5, 'dup')
    })
  })
})
