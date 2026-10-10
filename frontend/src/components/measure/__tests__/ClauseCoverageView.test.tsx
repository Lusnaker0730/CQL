import { describe, it, expect, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, within } from '../../../test/test-utils'
import ClauseCoverageView from '../ClauseCoverageView'
import type { ClauseCoverage } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, arg?: unknown) => {
      if (arg && typeof arg === 'object' && 'value' in arg) return `${key}:${(arg as { value: string }).value}`
      return key
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// PAT-232 — the Bonnie / MADiE style view: CQL text with clauses coloured by execution.
const cql = 'define "Adult":\n  40 > 18\n\ndefine "Never":\n  false\n'
const coverage: ClauseCoverage = {
  cql,
  libraryName: 'Demo',
  totalClauses: 4,
  coveredClauses: 3,
  percent: 75,
  statements: [
    {
      name: 'Adult', locator: '1:1-2:9', function: false, totalClauses: 3, coveredClauses: 3,
      clauses: [
        { localId: '1', locator: '2:3-2:9', type: 'Greater', hits: 1, value: 'true' },
        { localId: '2', locator: '2:3-2:4', type: 'Literal', hits: 1, value: '40' },
        { localId: '3', locator: '2:8-2:9', type: 'Literal', hits: 1, value: '18' },
      ],
    },
    {
      name: 'Never', locator: '4:1-5:7', function: false, totalClauses: 1, coveredClauses: 0,
      clauses: [{ localId: '4', locator: '5:3-5:7', type: 'Literal', hits: 0 }],
    },
  ],
}

describe('ClauseCoverageView', () => {
  it('shows the overall percentage, one chip per statement and the CQL coloured clause by clause', () => {
    render(<ClauseCoverageView coverage={coverage} subtitle="3 of 3" />)

    expect(screen.getByText('testCases.clauseCoverage.overall:75.0% (3/4)')).toBeInTheDocument()
    expect(screen.getByText('3 of 3')).toBeInTheDocument()
    expect(screen.getByText('Adult: 3/3')).toBeInTheDocument()
    expect(screen.getByText('Never: 0/1')).toBeInTheDocument()

    const source = screen.getByLabelText('testCases.clauseCoverage.sourceLabel')
    expect(source.textContent).toBe(cql) // rendered verbatim
    const covered = source.querySelectorAll('[data-coverage="covered"]')
    const uncovered = source.querySelectorAll('[data-coverage="uncovered"]')
    expect(Array.from(covered).map((el) => el.textContent)).toEqual(['40', ' > ', '18'])
    expect(Array.from(uncovered).map((el) => el.textContent)).toEqual(['false'])
  })

  it('marks functions and filters the statement chips down to those with gaps', async () => {
    const user = userEvent.setup()
    render(<ClauseCoverageView coverage={{
      ...coverage,
      statements: [
        { ...coverage.statements[0], name: 'IsAdult', function: true },
        coverage.statements[1],
      ],
    }} />)

    expect(screen.getByText('ƒ IsAdult: 3/3')).toBeInTheDocument()
    await user.click(screen.getByRole('switch', { name: 'testCases.clauseCoverage.onlyUncovered' }))
    expect(screen.queryByText('ƒ IsAdult: 3/3')).not.toBeInTheDocument()
    expect(screen.getByText('Never: 0/1')).toBeInTheDocument()
  })

  it('tells the user when every clause was evaluated', () => {
    render(<ClauseCoverageView coverage={{
      ...coverage,
      totalClauses: 3,
      coveredClauses: 3,
      percent: 100,
      statements: [coverage.statements[0]],
    }} />)

    expect(screen.getByText('testCases.clauseCoverage.allCovered')).toBeInTheDocument()
    expect(screen.queryByText('testCases.clauseCoverage.byStatement')).not.toBeInTheDocument()
    const source = screen.getByLabelText('testCases.clauseCoverage.sourceLabel')
    expect(source.querySelectorAll('[data-coverage="uncovered"]')).toHaveLength(0)
    expect(within(source).getByText('40')).toHaveAttribute('data-coverage', 'covered')
  })
})
