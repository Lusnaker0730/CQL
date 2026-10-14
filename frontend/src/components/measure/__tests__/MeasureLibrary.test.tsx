import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '../../../test/test-utils'
import MeasureLibrary from '../MeasureLibrary'
import type { MeasureDefinition, MeasurePage } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const getMeasurePage = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    getMeasurePage: (...args: unknown[]) => getMeasurePage(...args),
    exportFhirMeasure: vi.fn(),
  },
}))
vi.mock('../../common/LibraryPicker', () => ({ default: () => null }))
vi.mock('../../common/DepartmentSelector', () => ({ default: () => <div data-testid="department-selector" /> }))
vi.mock('../BatchEvaluationDialog', () => ({ default: () => null }))

const row = (id: number, name: string, status = 'draft'): MeasureDefinition =>
  ({ id, name, version: '1.0.0', status, scoringType: 'proportion', cqlContent: '' })
const pageOf = (content: MeasureDefinition[], totalElements: number): MeasurePage =>
  ({ content, page: 0, size: 25, totalElements, totalPages: Math.ceil(totalElements / 25) })

// PAT-257 — the measure list is one server page: status filter, scope tab, column sort and paging are
// query params, not browser-side filtering of everything.
describe('MeasureLibrary (paged list)', () => {
  beforeEach(() => {
    getMeasurePage.mockReset()
    getMeasurePage.mockResolvedValue(pageOf([row(1, 'Alpha'), row(2, 'Beta', 'active')], 2))
  })

  it('renders the page and asks for the defaults: all statuses, scope all, newest first, 25 rows', async () => {
    render(<MeasureLibrary />)
    expect(await screen.findByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
    expect(getMeasurePage).toHaveBeenLastCalledWith(
      expect.objectContaining({ statuses: [], scope: 'all', sort: 'updatedAt,desc', page: 0, size: 25 }),
    )
    expect(screen.getByText('library.allStatuses')).toBeInTheDocument()
  })

  it('status filter, scope tab, header sort and paging travel as query params', async () => {
    getMeasurePage.mockResolvedValue(pageOf([row(1, 'Alpha')], 60))
    render(<MeasureLibrary />)
    await screen.findByText('Alpha')

    // status: pick "active" in the multi-select
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /library\.statusFilter/ }))
    const listbox = screen.getByRole('listbox')
    fireEvent.click(within(listbox).getByText('status.active'))
    await waitFor(() => expect(getMeasurePage).toHaveBeenLastCalledWith(expect.objectContaining({ statuses: ['active'], page: 0 })))
    fireEvent.keyDown(listbox, { key: 'Escape' })

    // scope: the "My measures" tab is a server-side scope now
    fireEvent.click(screen.getByRole('tab', { name: 'library.tabs.myMeasures' }))
    await waitFor(() => expect(getMeasurePage).toHaveBeenLastCalledWith(expect.objectContaining({ scope: 'mine' })))

    // sort: clicking the name header sorts ascending, clicking again flips the direction
    fireEvent.click(screen.getByText('library.tableHeaders.name'))
    await waitFor(() => expect(getMeasurePage).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,asc' })))
    fireEvent.click(screen.getByText('library.tableHeaders.name'))
    await waitFor(() => expect(getMeasurePage).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,desc' })))

    // paging: 60 rows at 25 per page → the next-page button is enabled and asks for page 1
    fireEvent.click(screen.getByLabelText('Go to next page'))
    await waitFor(() => expect(getMeasurePage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 25 })))
  })

  it('the All tab shows the server total only while that scope is active', async () => {
    getMeasurePage.mockResolvedValue(pageOf([row(1, 'Alpha')], 42))
    render(<MeasureLibrary />)
    await screen.findByText('Alpha')
    expect(screen.getByRole('tab', { name: 'library.tabs.all' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'library.tabs.public' }))
    await waitFor(() => expect(screen.getByRole('tab', { name: 'library.tabs.allNoCount' })).toBeInTheDocument())
  })
})
