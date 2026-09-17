import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import ClinicApplyPage from '../ClinicApplyPage'

vi.mock('../../api/clinicApplicationApi', () => ({
  clinicApplicationApi: {
    submit: vi.fn(),
  },
}))

import { clinicApplicationApi } from '../../api/clinicApplicationApi'

describe('ClinicApplyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const fill = async (user: ReturnType<typeof userEvent.setup>, code = 'clinic-c') => {
    await user.type(screen.getByLabelText('apply.clinicName'), '仁心診所')
    await user.type(screen.getByLabelText('apply.tenantCode'), code)
    await user.type(screen.getByLabelText('apply.adminUsername'), 'drwang')
    await user.type(screen.getByLabelText('apply.adminEmail'), 'dr@example.com')
  }

  it('submits a valid application and shows the uniform success message', async () => {
    const user = userEvent.setup()
    vi.mocked(clinicApplicationApi.submit).mockResolvedValue({ message: 'ok' })
    render(<ClinicApplyPage />)

    await fill(user)
    await user.click(screen.getByRole('button', { name: 'apply.submit' }))

    await waitFor(() => {
      expect(clinicApplicationApi.submit).toHaveBeenCalledWith({
        clinicName: '仁心診所',
        tenantCode: 'clinic-c',
        adminUsername: 'drwang',
        adminEmail: 'dr@example.com',
      })
    })
    expect(screen.getByText('apply.successMessage')).toBeInTheDocument()
  })

  it('blocks submission while the clinic code is an invalid slug', async () => {
    const user = userEvent.setup()
    render(<ClinicApplyPage />)

    await fill(user, 'Bad Code!')

    const codeField = screen.getByLabelText('apply.tenantCode')
    expect(codeField).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('button', { name: 'apply.submit' })).toBeDisabled()
    expect(clinicApplicationApi.submit).not.toHaveBeenCalled()
  })
})
