import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '../../../test/test-utils'
import TestCaseValidationBadge from '../TestCaseValidationBadge'
import type { TestCase } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const base: TestCase = { id: 3, title: 'TC' }

// PAT-245 — the FHIR validation status of a test case: chip, error issues on demand, re-validate.
describe('TestCaseValidationBadge', () => {
  it('shows "not validated" for a test case that was never validated and nothing to expand', () => {
    render(<TestCaseValidationBadge testCase={base} />)

    expect(screen.getByTestId('validation-status-chip')).toHaveTextContent('testCases.validation.status.none')
    fireEvent.click(screen.getByTestId('validation-status-chip'))
    expect(screen.queryByTestId('validation-issues')).not.toBeInTheDocument()
  })

  it('an invalid bundle lists its error issues when the chip is clicked, with the overflow count', () => {
    render(<TestCaseValidationBadge testCase={{
      ...base,
      validationStatus: 'invalid',
      validation: {
        status: 'invalid', totalResources: 2, invalidResources: 1, errorCount: 3, warningCount: 1,
        issues: [
          { resourceType: 'Patient', resourceId: 'p1', severity: 'error', location: 'Patient.identifier', message: 'minimum required = 1' },
          { resourceType: 'Encounter', resourceId: 'e1', severity: 'error', message: 'status is required' },
        ],
      },
    }} />)

    expect(screen.getByTestId('validation-status-chip')).toHaveTextContent('testCases.validation.status.invalid')
    fireEvent.click(screen.getByTestId('validation-status-chip'))
    const issues = screen.getByTestId('validation-issues')
    expect(issues).toHaveTextContent('Patient/p1 Patient.identifier: minimum required = 1')
    expect(issues).toHaveTextContent('Encounter/e1: status is required')
    expect(issues).toHaveTextContent('testCases.validation.moreIssues')
  })

  it('offers re-validate (disabled while pending) and reports the click', () => {
    const onRevalidate = vi.fn()
    const { rerender } = render(<TestCaseValidationBadge testCase={{ ...base, validationStatus: 'valid', validation: { status: 'valid', totalResources: 1, invalidResources: 0, errorCount: 0, warningCount: 0, issues: [] } }} onRevalidate={onRevalidate} />)

    fireEvent.click(screen.getByRole('button', { name: 'testCases.validation.revalidate' }))
    expect(onRevalidate).toHaveBeenCalledTimes(1)

    rerender(<TestCaseValidationBadge testCase={{ ...base, validationStatus: 'pending' }} onRevalidate={onRevalidate} />)
    expect(screen.getByRole('button', { name: 'testCases.validation.revalidate' })).toBeDisabled()
    expect(screen.getByTestId('validation-status-chip')).toHaveTextContent('testCases.validation.status.pending')
  })
})
