import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '../../../test/test-utils'
import TestCaseCopyDialog from '../TestCaseCopyDialog'
import type { MeasureDefinition, TestCase } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const getMeasures = vi.fn()
const copyTestCasesTo = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    getMeasures: (...args: unknown[]) => getMeasures(...args),
    copyTestCasesTo: (...args: unknown[]) => copyTestCasesTo(...args),
  },
}))

const measure: MeasureDefinition = { id: 1, name: 'HbA1c', version: '1.0.0', status: 'active', scoringType: 'proportion', cqlContent: '' }
const testCases: TestCase[] = [
  { id: 11, title: 'controlled', series: 'adults' },
  { id: 12, title: 'uncontrolled' },
]

// PAT-246 — copy test cases to another measure: same-named versions first, chosen cases only,
// and the result names the copies whose expectation was dropped.
describe('TestCaseCopyDialog', () => {
  beforeEach(() => {
    getMeasures.mockReset()
    copyTestCasesTo.mockReset()
    getMeasures.mockResolvedValue([
      { id: 3, name: 'Other', version: '2.0.0', status: 'draft' },
      { id: 2, name: 'HbA1c', version: '1.1.0', status: 'draft' },
      measure,
    ])
  })

  it('lists other measures with the same-named version first and copies the selected cases', async () => {
    copyTestCasesTo.mockResolvedValue({ sourceMeasureId: 1, targetMeasureId: 2, copied: [{ id: 21, title: 'controlled' }], warnings: ["'uncontrolled': expected values dropped — Unknown population group"] })
    render(<TestCaseCopyDialog open onClose={vi.fn()} measure={measure} testCases={testCases} />)

    // deselect the second case
    fireEvent.click(screen.getByRole('checkbox', { name: 'uncontrolled' }))

    // open the target select and pick the same-named version (listed first)
    const select = await screen.findByRole('combobox')
    // the select is disabled until the measure list has loaded
    await waitFor(() => expect(screen.getByRole('combobox')).not.toHaveAttribute('aria-disabled', 'true'))
    fireEvent.mouseDown(select)
    const listbox = await screen.findByRole('listbox')
    const options = within(listbox).getAllByRole('option')
    expect(options[0]).toHaveTextContent('HbA1c v1.1.0')
    expect(options[0]).toHaveTextContent('testCases.copyDialog.sameMeasure')
    expect(options.map((o) => o.textContent)).not.toContainEqual(expect.stringContaining('v1.0.0'))
    fireEvent.click(options[0])

    fireEvent.click(screen.getByRole('button', { name: 'testCases.copyDialog.copy' }))

    await waitFor(() => expect(copyTestCasesTo).toHaveBeenCalledWith(1, 2, [11]))
    expect(await screen.findByTestId('copy-result')).toHaveTextContent('testCases.copyDialog.copied')
    expect(screen.getByText("'uncontrolled': expected values dropped — Unknown population group")).toBeInTheDocument()
  })

  it('cannot copy without a target or without a selection', async () => {
    render(<TestCaseCopyDialog open onClose={vi.fn()} measure={measure} testCases={testCases} />)
    await screen.findByRole('combobox')

    expect(screen.getByRole('button', { name: 'testCases.copyDialog.copy' })).toBeDisabled()
    expect(copyTestCasesTo).not.toHaveBeenCalled()
  })
})
