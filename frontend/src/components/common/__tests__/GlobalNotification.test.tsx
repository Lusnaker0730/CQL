import { describe, it, expect, vi } from 'vitest'
import { act, render, screen } from '../../../test/test-utils'
import GlobalNotification from '../GlobalNotification'
import { useNotification } from '../../../hooks/useNotification'

// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel
// mock needed (and the Proxy version no longer works under vitest 4).

function Trigger({ messages }: { messages: string[] }) {
  const { showNotification } = useNotification()
  return (
    <button
      onClick={() => {
        messages.forEach((m) => showNotification(m, 'info', 0)) // 0 = no auto-hide
      }}
    >
      fire
    </button>
  )
}

describe('GlobalNotification — PAT-133 stack reflow (P1)', () => {
  it('renders all active notifications inside a single fixed-position stack', () => {
    render(
      <>
        <GlobalNotification />
        <Trigger messages={['first', 'second', 'third']} />
      </>,
    )

    act(() => {
      screen.getByText('fire').click()
    })

    expect(screen.getByText('first')).toBeInTheDocument()
    expect(screen.getByText('second')).toBeInTheDocument()
    expect(screen.getByText('third')).toBeInTheDocument()

    // None of the alerts should have an inline `top: ...px` style (the old
    // implementation positioned each Snackbar individually with index-based
    // pixel offsets, which was the source of the reflow jump bug).
    document.querySelectorAll('[role="alert"]').forEach((el) => {
      const top = (el as HTMLElement).style.top
      expect(top).toBe('') // empty inline style means flow-laid-out
    })
  })
})
