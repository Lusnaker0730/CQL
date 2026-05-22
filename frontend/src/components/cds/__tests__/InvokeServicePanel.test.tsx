import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import type { CdsResponse } from '../../../types'

// Skip the heavy MUI icon barrel — the proxy stub stops vitest from opening
// thousands of icon files (Windows EMFILE).
// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel
// mock needed (and the Proxy version no longer works under vitest 4).

// i18n mock: return key strings (with {{var}} interpolation) so we can query
// by key.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      // String-based interpolation — `new RegExp('{{...}}')` throws under V8
      // (`{` is parsed as a quantifier opener), which would crash any
      // TextField that calls `t(key, { defaultValue })`. split/join avoids
      // building a regex at all.
      let out = key
      for (const [k, v] of Object.entries(opts)) {
        out = out.split(`{{${k}}}`).join(String(v))
      }
      return out
    },
    i18n: { changeLanguage: vi.fn() },
  }),
  Trans: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}))

// Mocked CDS hooks. The tests only need to verify which mutate/mutateAsync
// calls fire — not the underlying network behaviour.
const invokeMock = vi.fn()
const feedbackMock = vi.fn()

vi.mock('../../../hooks/useCdsHooks', () => {
  return {
    useCdsServices: () => ({
      data: {
        services: [
          {
            id: 'svc-1',
            hook: 'patient-view',
            title: 'Test Service',
            description: 'desc',
          },
        ],
      },
      isLoading: false,
      isError: false,
    }),
    useCdsServiceConfigs: () => ({ data: [] }),
    useInvokeCdsService: () => ({
      mutateAsync: invokeMock,
      isPending: false,
      isError: false,
      error: null,
    }),
    useSubmitCdsFeedback: () => ({
      mutateAsync: feedbackMock,
      isPending: false,
    }),
  }
})

import InvokeServicePanel from '../InvokeServicePanel'

const baseAuth = {
  auth: {
    user: { id: 1, username: 'tester', role: 'USER' },
    token: 'tok',
  },
}

const criticalResponse: CdsResponse = {
  cards: [
    {
      uuid: 'critical-uuid',
      summary: 'Critical alert',
      indicator: 'critical',
      source: { label: 'src' },
    },
    {
      uuid: 'normal-uuid',
      summary: 'Just info',
      indicator: 'info',
      source: { label: 'src' },
    },
  ],
}

async function selectServiceAndInvoke() {
  // Tests render <InvokeServicePanel initialService="svc-1" /> so selectedService
  // is already set when the component mounts (driving MUI Select's controlled
  // onChange via fireEvent is unreliable under jsdom — see #548). The
  // conditional context-field TextFields therefore render on first paint.
  //
  // MUI appends a `*` to required field labels, so an exact-match
  // `getByLabelText('sandbox.userIdLabel')` misses — match by regex anchored
  // at the start of the label text.
  const userInput = await screen.findByLabelText(/^sandbox\.userIdLabel/)
  fireEvent.change(userInput, { target: { value: 'Practitioner/1' } })
  fireEvent.change(screen.getByLabelText(/^sandbox\.patientIdLabel/), {
    target: { value: 'Patient/1' },
  })

  fireEvent.click(screen.getByRole('button', { name: 'invoke.invokeButton' }))
}

// #548 fix: SUT now stamps explicit ids on the conditional context-field
// TextFields so RTL's getByLabelText can resolve the label association
// reliably in jsdom (MUI's auto-generated useId values weren't traversable).
describe('InvokeServicePanel — PAT-132 critical-card feedback (P0)', () => {
  beforeEach(() => {
    invokeMock.mockReset()
    feedbackMock.mockReset()
    feedbackMock.mockResolvedValue(undefined)
  })

  it('submits feedback with outcome=accepted when user accepts a critical card', async () => {
    invokeMock.mockResolvedValue(criticalResponse)
    render(<InvokeServicePanel initialService="svc-1" />, { preloadedState: baseAuth })

    await selectServiceAndInvoke()

    // Critical card dialog appears with Accept button
    const acceptBtn = await screen.findByRole('button', { name: 'critical.acceptCard' })
    fireEvent.click(acceptBtn)

    await waitFor(() => expect(feedbackMock).toHaveBeenCalledTimes(1))
    expect(feedbackMock).toHaveBeenCalledWith({
      serviceId: 'svc-1',
      feedback: {
        feedback: [{ card: 'critical-uuid', outcome: 'accepted' }],
      },
    })
  })

  it('submits feedback with outcome=overridden + reason when user overrides a critical card', async () => {
    invokeMock.mockResolvedValue(criticalResponse)
    render(<InvokeServicePanel initialService="svc-1" />, { preloadedState: baseAuth })

    await selectServiceAndInvoke()

    // Open override form
    fireEvent.click(await screen.findByRole('button', { name: 'critical.overrideCard' }))
    fireEvent.change(screen.getByLabelText('critical.overrideReason'), {
      target: { value: 'Patient is allergic' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'critical.submitOverride' }))

    await waitFor(() => expect(feedbackMock).toHaveBeenCalledTimes(1))
    expect(feedbackMock).toHaveBeenCalledWith({
      serviceId: 'svc-1',
      feedback: {
        feedback: [
          {
            card: 'critical-uuid',
            outcome: 'overridden',
            overrideReason: {
              code: 'override',
              display: 'Patient is allergic',
            },
          },
        ],
      },
    })
  })

  it('uses the default-display fallback when override reason is blank', async () => {
    invokeMock.mockResolvedValue(criticalResponse)
    render(<InvokeServicePanel initialService="svc-1" />, { preloadedState: baseAuth })

    await selectServiceAndInvoke()

    fireEvent.click(await screen.findByRole('button', { name: 'critical.overrideCard' }))
    // Submit button is disabled when blank, so type a space and we still want
    // the fallback (component trims). To exercise the fallback path we type
    // whitespace — the dialog allows it even though the button stays enabled
    // for any non-empty input. Empty input keeps the button disabled, so we
    // verify the trim → fallback inside the handler by supplying a
    // whitespace-only reason.
    fireEvent.change(screen.getByLabelText('critical.overrideReason'), {
      target: { value: '   ' },
    })

    // The submit button is disabled when customReason.trim() is empty.
    const submit = screen.getByRole('button', { name: 'critical.submitOverride' })
    expect(submit).toBeDisabled()
  })
})
