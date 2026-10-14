import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '../../../test/test-utils'
import MeasureTerminologySection from '../MeasureTerminologySection'
import type { MeasureTerminology } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const getTerminology = vi.fn()
const pinTerminology = vi.fn()
const unpinTerminology = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    getTerminology: (...args: unknown[]) => getTerminology(...args),
    pinTerminology: (...args: unknown[]) => pinTerminology(...args),
    unpinTerminology: (...args: unknown[]) => unpinTerminology(...args),
  },
}))

const URL = 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders'
const terminology = (pinnable: boolean, pinnedVersion: string | null): MeasureTerminology => ({
  measureId: 5,
  status: pinnable ? 'draft' : 'active',
  pinnable,
  items: [
    {
      name: 'HbA1c Orders', url: URL, cqlVersion: null, pinnedVersion, source: 'platform',
      resolvedVersion: pinnedVersion ?? '2.0.0', resolvedStatus: 'active', pinned: pinnedVersion != null,
      versions: [{ id: 2, version: '2.0.0', status: 'active' }, { id: 1, version: '1.0.0', status: 'retired' }],
    },
    { name: 'Diabetes', url: 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001', cqlVersion: null,
      pinnedVersion: null, source: 'external', resolvedVersion: null, resolvedStatus: null, pinned: false, versions: [] },
    { name: 'Pinned In CQL', url: 'https://hospital.example.tw/fhir/ValueSet/Fixed', cqlVersion: '1.0.0', pinnedVersion: null,
      source: 'platform', resolvedVersion: '1.0.0', resolvedStatus: 'active', pinned: true, versions: [{ id: 3, version: '1.0.0', status: 'active' }] },
  ],
})

// PAT-260 — terminology pins on the measure page.
describe('MeasureTerminologySection', () => {
  beforeEach(() => {
    getTerminology.mockReset(); pinTerminology.mockReset(); unpinTerminology.mockReset()
  })

  it('lists every referenced value set with how it resolves, and pins one or all on a draft', async () => {
    getTerminology.mockResolvedValue(terminology(true, null))
    pinTerminology.mockResolvedValue(terminology(true, '2.0.0'))
    render(<MeasureTerminologySection measureId={5} />)
    fireEvent.click(await screen.findByText('details.terminology.title'))

    const row = await screen.findByTestId(`terminology-row-${URL}`)
    expect(within(row).getByText('details.terminology.resolvedFloating')).toBeInTheDocument()
    expect(screen.getByText('details.terminology.external')).toBeInTheDocument()
    expect(screen.getByText('details.terminology.pinnedInCql')).toBeInTheDocument()
    expect(screen.getByText('details.terminology.unpinnedCount')).toBeInTheDocument()   // 1 floating platform value set

    // the server now reports the pin (the view is also refetched after the measure invalidation)
    getTerminology.mockResolvedValue(terminology(true, '2.0.0'))
    fireEvent.click(within(row).getByRole('button', { name: /details\.terminology\.pinNow/ }))
    await waitFor(() => expect(pinTerminology).toHaveBeenCalledWith(5, { url: URL }))
    // the response replaces the view: now pinned, with an unpin button and the "all pinned" chip
    expect(await screen.findByText('details.terminology.resolvedPinned')).toBeInTheDocument()
    expect(screen.getByText('details.terminology.allPinned')).toBeInTheDocument()

    unpinTerminology.mockResolvedValue(terminology(true, null))
    getTerminology.mockResolvedValue(terminology(true, null))
    fireEvent.click(screen.getByRole('button', { name: /details\.terminology\.unpinAll/ }))
    await waitFor(() => expect(unpinTerminology).toHaveBeenCalledWith(5, undefined))
  })

  it('pin all sends no body; outside draft the buttons are disabled and the reason is shown', async () => {
    getTerminology.mockResolvedValue(terminology(true, null))
    pinTerminology.mockResolvedValue(terminology(true, '2.0.0'))
    const { unmount } = render(<MeasureTerminologySection measureId={5} />)
    fireEvent.click(await screen.findByText('details.terminology.title'))
    fireEvent.click(await screen.findByRole('button', { name: /details\.terminology\.pinAll/ }))
    await waitFor(() => expect(pinTerminology).toHaveBeenCalledWith(5, undefined))
    unmount()

    getTerminology.mockResolvedValue(terminology(false, '1.0.0'))
    render(<MeasureTerminologySection measureId={6} />)
    fireEvent.click(await screen.findByText('details.terminology.title'))
    const unpin = await screen.findByRole('button', { name: /details\.terminology\.unpin$/ })
    expect(unpin).toBeDisabled()
    expect(screen.getByText('details.terminology.draftOnly')).toBeInTheDocument()
  })
})
