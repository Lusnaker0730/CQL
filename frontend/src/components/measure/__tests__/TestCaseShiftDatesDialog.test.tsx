import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '../../../test/test-utils'
import TestCaseShiftDatesDialog from '../TestCaseShiftDatesDialog'
import type { MeasureDefinition, TestCase } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const shiftTestCaseDates = vi.fn()
const shiftAllTestCaseDates = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    shiftTestCaseDates: (...args: unknown[]) => shiftTestCaseDates(...args),
    shiftAllTestCaseDates: (...args: unknown[]) => shiftAllTestCaseDates(...args),
  },
}))

const showNotification = vi.fn()
vi.mock('../../../hooks/useNotification', () => ({
  useNotification: () => ({ showNotification }),
}))

const measure: MeasureDefinition = {
  id: 1, name: 'HbA1c', version: '1.0.0', status: 'active', scoringType: 'proportion', cqlContent: '',
  measurementPeriodStart: '2025-01-01', measurementPeriodEnd: '2025-12-31',
}
const testCase: TestCase = { id: 11, title: 'controlled' }

// PAT-248 — shifting stored test cases by whole years: one case or all, years must be a non-zero
// integer within the bound, and the list is refreshed afterwards.
describe('TestCaseShiftDatesDialog', () => {
  beforeEach(() => {
    shiftTestCaseDates.mockReset()
    shiftAllTestCaseDates.mockReset()
    showNotification.mockReset()
  })

  it('shifts one test case by the entered years and reports success', async () => {
    shiftTestCaseDates.mockResolvedValue({ ...testCase, status: 'pending' })
    const onClose = vi.fn()
    render(<TestCaseShiftDatesDialog open onClose={onClose} measure={measure} target={testCase} count={3} />)

    expect(screen.getByText('testCases.shiftDates.titleOne')).toBeInTheDocument()
    expect(screen.getByText('testCases.shiftDates.period')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '-1' } })
    fireEvent.click(screen.getByText('testCases.shiftDates.confirm'))

    await waitFor(() => expect(shiftTestCaseDates).toHaveBeenCalledWith(1, 11, -1))
    expect(shiftAllTestCaseDates).not.toHaveBeenCalled()
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(showNotification).toHaveBeenCalledWith('testCases.shiftDates.done', 'success')
  })

  it('shifts every test case when the target is "all", and refuses zero years', async () => {
    shiftAllTestCaseDates.mockResolvedValue({ measureDefinitionId: 1, years: 2, shifted: 3, testCaseIds: [1, 2, 3] })
    render(<TestCaseShiftDatesDialog open onClose={vi.fn()} measure={measure} target="all" count={3} />)

    expect(screen.getByText('testCases.shiftDates.titleAll')).toBeInTheDocument()
    const confirm = screen.getByText('testCases.shiftDates.confirm').closest('button') as HTMLButtonElement

    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '0' } })
    expect(confirm).toBeDisabled()
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '500' } })
    expect(confirm).toBeDisabled()

    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '2' } })
    expect(confirm).not.toBeDisabled()
    fireEvent.click(confirm)

    await waitFor(() => expect(shiftAllTestCaseDates).toHaveBeenCalledWith(1, 2))
    expect(shiftTestCaseDates).not.toHaveBeenCalled()
  })
})
