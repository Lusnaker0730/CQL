import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '../../test/test-utils'
import StatusPage from '../StatusPage'
import { statusApi } from '../../api/statusApi'

vi.mock('../../api/statusApi', () => ({
  statusApi: { getStatus: vi.fn() },
}))

describe('StatusPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows operational when all components are up', async () => {
    vi.mocked(statusApi.getStatus).mockResolvedValue({
      status: 'operational',
      timestamp: '2026-07-16T00:00:00Z',
      components: [
        { name: 'api', ok: true },
        { name: 'database', ok: true },
      ],
    })

    render(<StatusPage />)

    // i18n returns the key in tests
    await waitFor(() =>
      expect(screen.getByText('status.overall.operational')).toBeInTheDocument(),
    )
  })

  it('shows an outage when the status request fails', async () => {
    vi.mocked(statusApi.getStatus).mockRejectedValue(new Error('unreachable'))

    render(<StatusPage />)

    await waitFor(() =>
      expect(screen.getByText('status.overall.outage')).toBeInTheDocument(),
    )
  })
})
