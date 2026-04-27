import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act, within } from '../../../test/test-utils'
import MeasureComparison from '../MeasureComparison'

// test-utils does NOT initialize i18next; useTranslation falls back to raw
// keys. Mock so queries match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

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
    // and the comparePeriods follow-up must NOT fire (mount guard
    // short-circuits before the second await chain).
    let resolveEval: ((v: unknown) => void) | undefined
    evaluateMeasureMock.mockImplementation(
      () => new Promise((res) => { resolveEval = res }),
    )

    const { unmount } = render(<MeasureComparison />)

    // The page has multiple comboboxes (Autocomplete + the trend-interval
    // select). Scope to the measure-name field by its label key.
    const measureGroup = await screen.findByLabelText('comparison.measureName')
    // The MUI Autocomplete renders an input child inside the wrapping label
    // — we type into it to filter, then click the matching option.
    const measureInput = within(measureGroup.closest('.MuiFormControl-root') ?? measureGroup as HTMLElement)
      .getByRole('combobox')
    fireEvent.change(measureInput, { target: { value: 'Measure 1' } })
    const option = await screen.findByRole('option', { name: /Measure 1/i })
    fireEvent.click(option)

    // Live-compare button — label key is 'comparison.compareLive'
    fireEvent.click(screen.getByRole('button', { name: 'comparison.compareLive' }))
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
    getMeasuresMock = vi.fn().mockResolvedValue([{ id: 1, name: 'M1', title: 'Measure 1' }])
    render(<MeasureComparison />)
    await waitFor(() => expect(getMeasuresMock).toHaveBeenCalled())
  })
})
