import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '../../../test/test-utils'
import EhrConnectionList from '../EhrConnectionList'
import { ehrApi } from '../../../api'

vi.mock('../../../api', () => ({
  ehrApi: {
    getConnections: vi.fn(),
    getHealthOverview: vi.fn(),
    testConnection: vi.fn(),
    deleteConnection: vi.fn(),
  },
}))

describe('EhrConnectionList — Live Health column (PAT-111)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders a health chip per connection from the overview poll', async () => {
    vi.mocked(ehrApi.getConnections).mockResolvedValue([
      { id: 1, name: 'A', fhirServerUrl: 'http://a', authType: 'none', status: 'success' },
      { id: 2, name: 'B', fhirServerUrl: 'http://b', authType: 'none', status: 'success' },
    ] as never)
    vi.mocked(ehrApi.getHealthOverview).mockResolvedValue([
      {
        connectionId: 1,
        connectionName: 'A',
        fhirServerUrl: 'http://a',
        currentStatus: 'healthy',
        lastResponseTimeMs: 100,
        lastCheckedAt: '2026-04-23T10:00:00',
        avgResponseTimeMs24h: 105,
        availability24h: 99.5,
        totalChecks24h: 96,
        errorCount24h: 0,
      },
      {
        connectionId: 2,
        connectionName: 'B',
        fhirServerUrl: 'http://b',
        currentStatus: 'down',
        lastResponseTimeMs: null,
        lastCheckedAt: '2026-04-23T10:00:00',
        avgResponseTimeMs24h: null,
        availability24h: 0,
        totalChecks24h: 12,
        errorCount24h: 12,
      },
    ])

    render(<EhrConnectionList />)

    await waitFor(() => {
      // One chip per row shows the availability % (100% and 0%).
      expect(screen.getByText(/100%/)).toBeInTheDocument()
      expect(screen.getByText(/0%/)).toBeInTheDocument()
    })
  })

  it('falls back to Unknown chip when no health row exists for a connection', async () => {
    vi.mocked(ehrApi.getConnections).mockResolvedValue([
      { id: 99, name: 'Stale', fhirServerUrl: 'http://x', authType: 'none', status: 'untested' },
    ] as never)
    vi.mocked(ehrApi.getHealthOverview).mockResolvedValue([])

    render(<EhrConnectionList />)

    // When overview is empty, the fallback chip renders (i18n key when untranslated,
    // or "Unknown" text if i18n is active in tests).
    await waitFor(() => {
      // We rely on screen query rather than specific text since i18n keys are
      // returned as-is in the test harness. The chip exists — its label will be
      // the i18n key "ehr.health.unknown".
      expect(screen.getByText(/unknown|ehr\.health\.unknown/i)).toBeInTheDocument()
    })
  })

  it('renders nothing for health column while health overview is still loading (uses Unknown fallback)', async () => {
    vi.mocked(ehrApi.getConnections).mockResolvedValue([
      { id: 1, name: 'A', fhirServerUrl: 'http://a', authType: 'none', status: 'success' },
    ] as never)
    // Never resolves — simulates the 30s poll in flight.
    vi.mocked(ehrApi.getHealthOverview).mockImplementation(() => new Promise(() => {}))

    render(<EhrConnectionList />)

    // Connection loads; health overview hasn't, so fallback Unknown chip appears.
    await waitFor(() => {
      expect(screen.getByText(/unknown|ehr\.health\.unknown/i)).toBeInTheDocument()
    })
  })
})
