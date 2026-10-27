import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import ValueSetTab from '../ValueSetTab'

// test-utils does NOT initialize i18next; mock so queries match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { changeLanguage: vi.fn() } }),
}))

// No debounce: the typed title reaches the search hook on the same render.
vi.mock('../../../hooks/useDebouncedValue', () => ({ useDebouncedValue: (value: unknown) => value }))

const searchMock = vi.fn()
const igMock = vi.fn()
vi.mock('../../../hooks/useTerminology', () => ({
  useSearchValueSets: (title?: string) => searchMock(title),
  useExpandValueSet: () => ({
    mutate: vi.fn(), data: undefined, isPending: false, isError: false, error: null, variables: undefined,
  }),
}))
vi.mock('../../../hooks/useImplementationGuide', () => ({
  useIgValueSets: (title?: string) => igMock(title),
}))

const PLATFORM = {
  id: '7',
  url: 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders',
  name: 'HbA1cOrders',
  title: 'HbA1c order codes',
  source: 'platform' as const,
  version: '1.0.0',
  status: 'active',
}

// Mirrors the real hook: no title means the query is disabled and has no data.
function remoteSearch(degraded: boolean) {
  return (title?: string) =>
    title
      ? { data: { valueSets: [PLATFORM], terminologyDegraded: degraded }, isLoading: false, error: null }
      : { data: undefined, isLoading: false, error: null }
}

describe('ValueSetTab — PAT-275 remote terminology degraded hint', () => {
  beforeEach(() => {
    searchMock.mockReset()
    igMock.mockReset().mockReturnValue({ data: undefined, isLoading: false, error: null })
  })

  it('shows the hint next to the (platform-only) results when the remote server was unavailable', () => {
    searchMock.mockImplementation(remoteSearch(true))
    render(<ValueSetTab />)

    fireEvent.change(screen.getByLabelText('valueSet.searchLabel'), { target: { value: 'HbA1c' } })

    expect(searchMock).toHaveBeenLastCalledWith('HbA1c')
    expect(screen.getByRole('alert')).toHaveTextContent('valueSet.remoteDegraded')
    // Information, not a gate: the platform result is still listed.
    expect(screen.getByText(/HbA1c order codes/)).toBeInTheDocument()
  })

  it('shows no hint when the remote server answered', () => {
    searchMock.mockImplementation(remoteSearch(false))
    render(<ValueSetTab />)

    fireEvent.change(screen.getByLabelText('valueSet.searchLabel'), { target: { value: 'HbA1c' } })

    expect(screen.getByText(/HbA1c order codes/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('hides the hint when the source is switched to the local IG (remote search disabled)', () => {
    searchMock.mockImplementation(remoteSearch(true))
    render(<ValueSetTab />)
    fireEvent.change(screen.getByLabelText('valueSet.searchLabel'), { target: { value: 'HbA1c' } })
    expect(screen.getByRole('alert')).toBeInTheDocument()

    fireEvent.click(screen.getByTitle('valueSet.sourceLocalTitle'))

    expect(searchMock).toHaveBeenLastCalledWith(undefined)
    expect(igMock).toHaveBeenLastCalledWith('HbA1c')
    expect(screen.queryByRole('alert')).toBeNull()
  })
})
