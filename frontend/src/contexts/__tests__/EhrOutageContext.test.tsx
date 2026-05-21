import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { useContext } from 'react'
import { EhrOutageProvider, EhrOutageContext } from '../EhrOutageContext'

// Mock the bridge to avoid registering a real Axios listener in tests
vi.mock('../../api/ehrOutageBridge', () => ({
  registerEhrOutageListener: vi.fn(() => () => {}),
}))

function Probe() {
  const ctx = useContext(EhrOutageContext)
  if (!ctx) return null
  return (
    <div>
      <div data-testid="count">{ctx.outages.length}</div>
      <div data-testid="conn-1-failures">
        {ctx.outages.find((o) => o.connectionId === 1)?.failureCount ?? 0}
      </div>
      <button
        onClick={() =>
          ctx.reportOutage({
            connectionId: 1,
            connectionName: 'Lab EHR',
            reason: 'TIMEOUT',
            retryAfterSeconds: 30,
          })
        }
      >
        report-1
      </button>
      <button
        onClick={() =>
          ctx.reportOutage({
            connectionId: 2,
            connectionName: 'Pharmacy EHR',
            reason: 'CIRCUIT_BREAKER_OPEN',
            retryAfterSeconds: 60,
          })
        }
      >
        report-2
      </button>
      <button onClick={() => ctx.clearOutage('1')}>clear-1</button>
      <button onClick={() => ctx.clearAll()}>clear-all</button>
    </div>
  )
}

describe('EhrOutageContext', () => {
  beforeEach(() => vi.clearAllMocks())

  it('starts with no outages', () => {
    render(
      <EhrOutageProvider>
        <Probe />
      </EhrOutageProvider>
    )
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('aggregates repeat failures from the same connection into one row', () => {
    render(
      <EhrOutageProvider>
        <Probe />
      </EhrOutageProvider>
    )
    act(() => screen.getByText('report-1').click())
    act(() => screen.getByText('report-1').click())
    act(() => screen.getByText('report-1').click())

    expect(screen.getByTestId('count').textContent).toBe('1')
    expect(screen.getByTestId('conn-1-failures').textContent).toBe('3')
  })

  it('keeps separate rows for distinct connections', () => {
    render(
      <EhrOutageProvider>
        <Probe />
      </EhrOutageProvider>
    )
    act(() => screen.getByText('report-1').click())
    act(() => screen.getByText('report-2').click())
    expect(screen.getByTestId('count').textContent).toBe('2')
  })

  it('clearOutage removes one row by key', () => {
    render(
      <EhrOutageProvider>
        <Probe />
      </EhrOutageProvider>
    )
    act(() => screen.getByText('report-1').click())
    act(() => screen.getByText('report-2').click())
    act(() => screen.getByText('clear-1').click())
    expect(screen.getByTestId('count').textContent).toBe('1')
  })

  it('clearAll empties the list', () => {
    render(
      <EhrOutageProvider>
        <Probe />
      </EhrOutageProvider>
    )
    act(() => screen.getByText('report-1').click())
    act(() => screen.getByText('report-2').click())
    act(() => screen.getByText('clear-all').click())
    expect(screen.getByTestId('count').textContent).toBe('0')
  })
})
