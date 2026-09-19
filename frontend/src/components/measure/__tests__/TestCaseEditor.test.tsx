import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '../../../test/test-utils'
import TestCaseEditor from '../TestCaseEditor'
import { measureApi } from '../../../api'
import type { MeasureDefinition, TestCase } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
    i18n: { changeLanguage: vi.fn() },
  }),
}))

vi.mock('../../../api', () => ({
  measureApi: {
    createTestCase: vi.fn(() => Promise.resolve({ id: 99 })),
    updateTestCase: vi.fn(() => Promise.resolve({ id: 5 })),
  },
}))

// Heavy siblings that have nothing to do with the expectation payload.
vi.mock('../../testcase-builder/VisualBundleBuilder', () => ({ default: () => <div /> }))
vi.mock('../../ehr/EhrImportForTestCase', () => ({ default: () => <div /> }))

const pops = (...types: string[]) =>
  types.map((populationType) => ({ populationType, criteriaExpression: populationType }))

const twoGroupMeasure: MeasureDefinition = {
  id: 7,
  name: 'two-rates',
  version: '1.0.0',
  scoringType: 'proportion',
  cqlContent: '',
  groupDefinitions: [
    { groupId: 'group-1', populations: pops('initial-population', 'denominator', 'numerator') },
    { groupId: 'group-2', populations: pops('initial-population', 'denominator', 'numerator') },
  ],
}

const simpleMeasure: MeasureDefinition = {
  ...twoGroupMeasure,
  groupDefinitions: [twoGroupMeasure.groupDefinitions![0]],
}

// This file renders the whole editor (bundle builder context, Monaco stub, MUI form). On a
// loaded machine / CI runner the first cold render alone can take several seconds, so the
// default 5 s test timeout and 1 s waitFor window are too tight to be reliable here.
vi.setConfig({ testTimeout: 30_000 })
const WAIT = { timeout: 10_000 }

const createMock = vi.mocked(measureApi.createTestCase)
const updateMock = vi.mocked(measureApi.updateTestCase)

const typeTitle = () =>
  fireEvent.change(screen.getByRole('textbox', { name: /testCaseEditor\.fields\.title/ }), {
    target: { value: 'two stays' },
  })
const save = () => fireEvent.click(screen.getByRole('button', { name: /testCaseEditor\.save/ }))
const structuredToggle = () => screen.getByRole('switch', { name: 'structured expectations' })

// PAT-228 — what the editor actually sends on save.
describe('TestCaseEditor — structured expectations payload', () => {
  beforeEach(() => {
    localStorage.clear()
    createMock.mockClear()
    updateMock.mockClear()
  })

  it('a new test case on a multi-group measure defaults to per-group expectations and sends them', async () => {
    render(<TestCaseEditor measure={twoGroupMeasure} testCase={null} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(structuredToggle()).toBeChecked()

    typeTitle()
    // Group 2's numerator is expected, group 1's is not — the flat map cannot say this.
    fireEvent.click(screen.getByRole('switch', { name: 'group-2 numerator' }))
    save()

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1), WAIT)
    const [measureId, body] = createMock.mock.calls[0] as [number, TestCase]
    expect(measureId).toBe(7)
    expect(body.expectedValues?.groups).toEqual([
      { groupId: 'group-1', populations: { 'initial-population': 1, denominator: 1, numerator: 0 } },
      { groupId: 'group-2', populations: { 'initial-population': 1, denominator: 1, numerator: 1 } },
    ])
    // Flat map = first group, kept for list chips; the run ignores it.
    expect(body.expectedPopulations).toEqual({ 'initial-population': true, denominator: true, numerator: false })
  })

  it('a simple single-group measure stays on the flat switches and sends no structured form', async () => {
    render(<TestCaseEditor measure={simpleMeasure} testCase={null} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(structuredToggle()).not.toBeChecked()
    expect(screen.queryByText('testCaseEditor.structured.legacyWarning')).not.toBeInTheDocument()

    typeTitle()
    save()

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1), WAIT)
    const body = createMock.mock.calls[0][1] as TestCase
    expect(body.expectedValues).toBeNull()
    expect(body.expectedPopulations).toEqual({ 'initial-population': true, denominator: true, numerator: false })
  })

  it('warns when the flat switches are used on a measure they cannot describe', () => {
    render(<TestCaseEditor measure={twoGroupMeasure} testCase={null} onClose={vi.fn()} onSaved={vi.fn()} />)
    fireEvent.click(structuredToggle())
    expect(screen.getByText('testCaseEditor.structured.legacyWarning')).toBeInTheDocument()
  })

  it('switching an existing structured test case back to the flat form clears the stored expectation', async () => {
    const existing: TestCase = {
      id: 5,
      title: 'existing',
      patientBundleJson: '{"resourceType":"Bundle","type":"collection","entry":[]}',
      expectedPopulations: { 'initial-population': true },
      expectedValues: { groups: [{ groupId: 'group-1', populations: { numerator: 1 } }] },
    }
    render(<TestCaseEditor measure={twoGroupMeasure} testCase={existing} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(structuredToggle()).toBeChecked()
    // Saved values are kept and lined up with the measure's populations.
    expect(screen.getByRole('switch', { name: 'group-1 numerator' })).toBeChecked()

    fireEvent.click(structuredToggle())
    save()

    await waitFor(() => expect(updateMock).toHaveBeenCalledTimes(1), WAIT)
    const body = updateMock.mock.calls[0][2] as TestCase
    expect(body.expectedValues).toBeNull()
  })

  it('offers the last run’s actual values and adopts them as the expectation', async () => {
    const existing: TestCase = {
      id: 5,
      title: 'existing',
      patientBundleJson: '{"resourceType":"Bundle","type":"collection","entry":[]}',
      expectedValues: { groups: [{ groupId: 'group-1', populations: { numerator: 0 } }] },
      lastRunResultJson: JSON.stringify({
        status: 'fail',
        actualValues: {
          groups: [
            { groupId: 'group-1', populations: { 'initial-population': 1, denominator: 1, numerator: 1 } },
            { groupId: 'group-2', populations: { 'initial-population': 1, denominator: 0, numerator: 0 } },
          ],
        },
      }),
    }
    render(<TestCaseEditor measure={twoGroupMeasure} testCase={existing} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(screen.getByRole('switch', { name: 'group-1 numerator' })).not.toBeChecked()

    fireEvent.click(screen.getByRole('button', { name: /testCaseEditor\.structured\.useActual/ }))

    expect(screen.getByRole('switch', { name: 'group-1 numerator' })).toBeChecked()
    expect(screen.getByRole('switch', { name: 'group-2 denominator' })).not.toBeChecked()
  })
})
