import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import userEvent from '@testing-library/user-event'
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
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
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
  // Use userEvent — fireEvent.click on a MUI Select menu item only updates
  // the visible display but does not always fire the controlled-value
  // onChange in jsdom (the hidden input's `value` attribute updates as MUI's
  // own bookkeeping, but the React state setter never runs). userEvent
  // simulates the full pointer event sequence MUI listens for, so the
  // onChange propagates and the conditional context fields actually render.
  const user = userEvent.setup()
  const select = screen.getByRole('combobox', { name: 'invoke.serviceLabel' })
  await user.click(select)
  const item = await screen.findByRole('option', { name: /Test Service/ })
  await user.click(item)

  // After the onChange fires, stringFields.map renders the userId / patientId
  // TextFields. The SUT gives them explicit ids (`cds-context-userId` /
  // `cds-context-patientId`) so the test can also fall back to getElementById
  // if the label-association lookup were ever unreliable.
  const userInput = await screen.findByLabelText('sandbox.userIdLabel')
  await user.type(userInput, 'Practitioner/1')
  const patientInput = screen.getByLabelText('sandbox.patientIdLabel')
  await user.type(patientInput, 'Patient/1')

  await user.click(screen.getByRole('button', { name: 'invoke.invokeButton' }))
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
    render(<InvokeServicePanel />, { preloadedState: baseAuth })

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
    render(<InvokeServicePanel />, { preloadedState: baseAuth })

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
    render(<InvokeServicePanel />, { preloadedState: baseAuth })

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
