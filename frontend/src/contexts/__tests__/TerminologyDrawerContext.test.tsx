import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { useContext } from 'react'
import {
  TerminologyDrawerProvider,
  TerminologyDrawerContext,
} from '../TerminologyDrawerContext'

function Probe() {
  const ctx = useContext(TerminologyDrawerContext)
  return (
    <div>
      <div data-testid="open">{String(ctx.isOpen)}</div>
      <div data-testid="system">{ctx.options.system ?? '-'}</div>
      <button onClick={() => ctx.openDrawer({ system: 'http://snomed', tab: 1 })}>open</button>
      <button onClick={() => ctx.closeDrawer()}>close</button>
    </div>
  )
}

describe('TerminologyDrawerContext — PAT-149', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('opens with provided options', () => {
    render(
      <TerminologyDrawerProvider>
        <Probe />
      </TerminologyDrawerProvider>
    )
    act(() => {
      screen.getByText('open').click()
    })
    expect(screen.getByTestId('open').textContent).toBe('true')
    expect(screen.getByTestId('system').textContent).toBe('http://snomed')
  })

  it('closes immediately but keeps options for the transition window', () => {
    render(
      <TerminologyDrawerProvider>
        <Probe />
      </TerminologyDrawerProvider>
    )
    act(() => screen.getByText('open').click())
    act(() => screen.getByText('close').click())
    expect(screen.getByTestId('open').textContent).toBe('false')
    // Options still present right after close — animation hasn't run yet.
    expect(screen.getByTestId('system').textContent).toBe('http://snomed')

    // Advance past the UI transition window.
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(screen.getByTestId('system').textContent).toBe('-')
  })

  it('PAT-149 regression: pending close-timer is cleared on unmount', () => {
    const { unmount } = render(
      <TerminologyDrawerProvider>
        <Probe />
      </TerminologyDrawerProvider>
    )
    act(() => screen.getByText('open').click())
    act(() => screen.getByText('close').click())

    // Unmount before the close-animation timer fires.
    unmount()

    // Advancing timers must not throw "setOptions on unmounted" — the cleanup
    // effect cleared the pending timer.
    expect(() => act(() => vi.advanceTimersByTime(2000))).not.toThrow()
  })

  it('opening again clears the previous pending close timer', () => {
    render(
      <TerminologyDrawerProvider>
        <Probe />
      </TerminologyDrawerProvider>
    )
    act(() => screen.getByText('open').click())
    act(() => screen.getByText('close').click())
    // Re-open before the timer fires
    act(() => screen.getByText('open').click())
    act(() => vi.advanceTimersByTime(2000))
    // Options should still reflect the second open, not have been blanked
    // by the now-cancelled first-close timer.
    expect(screen.getByTestId('system').textContent).toBe('http://snomed')
  })
})
