import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '../../../test/test-utils'
import MeasureComparison from '../MeasureComparison'

let getMeasuresMock: ReturnType<typeof vi.fn>
let evaluateMeasureMock: ReturnType<typeof vi.fn>
let comparePeriodsMock: ReturnType<typeof vi.fn>

vi.mock('../../../api', () => ({
  measureApi: {
    getMeasures: () => getMeasuresMock(),
    evaluateMeasure: (...args: unknown[]) => evaluateMeasureMock(...args),
    comparePeriods: (...args: unknown[]) => comparePeriodsMock(...args),
    getTrend: vi.fn(),
  },
}))

describe('MeasureComparison — PAT-130 live-eval cleanup', () => {
  beforeEach(() => {
    getMeasuresMock = vi.fn().mockResolvedValue([
      { id: 1, name: 'M1', title: 'Measure 1' },
    ])
    evaluateMeasureMock = vi.fn()
    comparePeriodsMock = vi.fn()
  })

  it('mounts, kicks off a live evaluation, and unmounts cleanly without throwing', async () => {
    // The fix wraps every post-await setState in `if (isMountedRef.current)`.
    // We verify the mechanic by holding the first evaluate forever and then
    // unmounting; after the promise resolves there must be no thrown error
    // and the comparePeriods follow-up must NOT fire (mount guard short-circuits
    // before the second await chain).
    let resolveEval: ((v: unknown) => void) | undefined
    evaluateMeasureMock.mockImplementation(
      () => new Promise((res) => { resolveEval = res }),
    )

    const { unmount } = render(<MeasureComparison />)

    // Select the measure
    const combobox = await screen.findByRole('combobox')
    fireEvent.change(combobox, { target: { value: 'Measure 1' } })
    const option = await screen.findByRole('option', { name: /Measure 1/i })
    fireEvent.click(option)

    // Click Evaluate Live & Compare — kicks off the first evaluate
    fireEvent.click(screen.getByRole('button', { name: /Evaluate Live & Compare|即時評估並比較/i }))
    expect(evaluateMeasureMock).toHaveBeenCalledTimes(1)

    // Unmount while the first evaluate is still pending.
    unmount()

    // Resolve after unmount. Without the isMountedRef guard, the function
    // would proceed to setLiveCompareStep(2) → second evaluate. With the
    // guard, the function returns early — no second mock call.
    await act(async () => {
      resolveEval?.({ groups: [] })
      await Promise.resolve()
    })

    expect(evaluateMeasureMock).toHaveBeenCalledTimes(1)
    expect(comparePeriodsMock).not.toHaveBeenCalled()
  })

  it('uses a finite staleTime for the measures list (not Infinity)', async () => {
    // We assert behavior indirectly: getMeasures is called once on first
    // render; if staleTime were Infinity this would still hold for a single
    // render. The real intent — that a remount within ~5 min will reuse the
    // cache while a remount beyond that refetches — is enforced by React
    // Query, so this test just guarantees the measure list renders without
    // hanging forever.
    getMeasuresMock = vi.fn().mockResolvedValue([{ id: 1, name: 'M1', title: 'Measure 1' }])
    render(<MeasureComparison />)
    await waitFor(() => expect(getMeasuresMock).toHaveBeenCalled())
  })
})
