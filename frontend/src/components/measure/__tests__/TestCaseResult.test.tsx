import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '../../../test/test-utils'
import TestCaseResult from '../TestCaseResult'
import type { TestCaseRunResult } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, arg?: unknown) => {
      if (typeof arg === 'string') return arg
      if (arg && typeof arg === 'object' && 'id' in arg) return `${key}:${(arg as { id: string }).id}`
      return key
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const base: TestCaseRunResult = { testCaseId: 1, testCaseTitle: 'TC', status: 'fail' }

// PAT-228 — the structured comparison table next to the legacy boolean one.
describe('TestCaseResult — structured comparison', () => {
  it('renders populations as yes / no, observations and strata as the backend rendered them', () => {
    render(<TestCaseResult result={{
      ...base,
      valueComparisons: [
        { groupId: 'group-1', kind: 'population', key: 'numerator', expected: '1', actual: '0', match: false },
        { groupId: 'group-1', kind: 'observation', key: 'values', expected: '[30, 60]', actual: '[30, 45]', match: false },
        { groupId: 'group-1', kind: 'stratifier', key: 'strat-elderly', expected: 'true', actual: '', match: false },
      ],
    }} />)

    const table = screen.getByRole('table', { name: 'testCaseResult.structuredTable' })
    const rows = within(table).getAllByRole('row').slice(1)

    expect(within(rows[0]).getByText('numerator')).toBeInTheDocument()
    expect(within(rows[0]).getByText('testCaseResult.yes')).toBeInTheDocument()
    expect(within(rows[0]).getByText('testCaseResult.no')).toBeInTheDocument()

    expect(within(rows[1]).getByText('testCaseResult.kinds.observation')).toBeInTheDocument()
    expect(within(rows[1]).getByText('[30, 60]')).toBeInTheDocument()
    expect(within(rows[1]).getByText('[30, 45]')).toBeInTheDocument()

    expect(within(rows[2]).getByText('testCaseResult.kinds.stratifier:strat-elderly')).toBeInTheDocument()
    expect(within(rows[2]).getByText('testCaseResult.noStratum')).toBeInTheDocument()
  })

  it('shows the group column only when more than one group was compared', () => {
    const one = render(<TestCaseResult result={{
      ...base,
      valueComparisons: [{ groupId: 'group-1', kind: 'population', key: 'numerator', expected: '1', actual: '1', match: true }],
    }} />)
    expect(screen.queryByText('testCaseResult.tableHeaders.group')).not.toBeInTheDocument()
    one.unmount()

    render(<TestCaseResult result={{
      ...base,
      valueComparisons: [
        { groupId: 'group-1', kind: 'population', key: 'numerator', expected: '1', actual: '1', match: true },
        { groupId: 'group-2', kind: 'population', key: 'numerator', expected: '0', actual: '0', match: true },
      ],
    }} />)
    expect(screen.getByText('testCaseResult.tableHeaders.group')).toBeInTheDocument()
    expect(screen.getByText('group-2')).toBeInTheDocument()
  })

  it('keeps a count above 1 as a number — ready for episode-level counting', () => {
    render(<TestCaseResult result={{
      ...base,
      valueComparisons: [{ groupId: 'group-1', kind: 'population', key: 'denominator', expected: '3', actual: '2', match: false }],
    }} />)
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('still renders the legacy boolean table for test cases without structured expectations', () => {
    render(<TestCaseResult result={{
      ...base,
      comparisons: [{ populationType: 'numerator', expected: true, actual: false, match: false }],
    }} />)
    expect(screen.queryByRole('table', { name: 'testCaseResult.structuredTable' })).not.toBeInTheDocument()
    expect(screen.getByText('testCaseResult.tableHeaders.population')).toBeInTheDocument()
  })
})
