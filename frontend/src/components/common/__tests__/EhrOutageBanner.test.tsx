import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, act } from '../../../test/test-utils'
import EhrOutageBanner from '../EhrOutageBanner'
import { EhrOutageProvider } from '../../../contexts/EhrOutageContext'
import { notifyEhrOutage, _resetListener } from '../../../api/ehrOutageBridge'

function renderWithOutageProvider() {
  return render(
    <EhrOutageProvider>
      <EhrOutageBanner />
    </EhrOutageProvider>
  )
}

describe('EhrOutageBanner (PAT-110)', () => {
  beforeEach(() => {
    _resetListener()
  })

  it('renders nothing when no outages reported', () => {
    renderWithOutageProvider()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('renders warning banner when an FHIR_UPSTREAM_UNAVAILABLE envelope arrives', () => {
    renderWithOutageProvider()
    act(() => {
      notifyEhrOutage({
        connectionId: 42,
        connectionName: '台大 HIS',
        reason: 'TIMEOUT',
        retryAfterSeconds: 30,
      })
    })

    // MUI Alert has role="alert"; severity="warning" drives yellow palette.
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
    expect(alert).toHaveClass(/MuiAlert-colorWarning/)
  })

  it('dedupes repeat failures for the same connection into one row', () => {
    renderWithOutageProvider()
    act(() => {
      notifyEhrOutage({ connectionId: 1, connectionName: 'A', reason: 'TIMEOUT', retryAfterSeconds: 30 })
      notifyEhrOutage({ connectionId: 1, connectionName: 'A', reason: 'TIMEOUT', retryAfterSeconds: 30 })
      notifyEhrOutage({ connectionId: 1, connectionName: 'A', reason: 'TIMEOUT', retryAfterSeconds: 30 })
    })

    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('keeps separate rows for different connections', () => {
    renderWithOutageProvider()
    act(() => {
      notifyEhrOutage({ connectionId: 1, connectionName: 'A', reason: 'TIMEOUT', retryAfterSeconds: 30 })
      notifyEhrOutage({ connectionId: 2, connectionName: 'B', reason: 'CIRCUIT_BREAKER_OPEN', retryAfterSeconds: 60 })
    })

    expect(screen.getAllByRole('alert')).toHaveLength(2)
  })

  it('collapses null connectionId into a single "unknown" bucket', () => {
    renderWithOutageProvider()
    act(() => {
      notifyEhrOutage({ connectionId: null, connectionName: null, reason: 'OTHER', retryAfterSeconds: 30 })
      notifyEhrOutage({ connectionId: null, connectionName: null, reason: 'OTHER', retryAfterSeconds: 30 })
    })

    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })
})
