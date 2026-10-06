import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '../../../test/test-utils'
import ApprovalReadinessPanel from '../ApprovalReadinessPanel'
import type { ApprovalReadiness } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { defaultValue?: string }) => opts?.defaultValue ?? key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const base: ApprovalReadiness = {
  measureId: 7,
  status: 'draft',
  ready: true,
  blockers: [],
  warnings: [],
  testCases: { total: 3, passed: 3, failed: 0, errored: 0, notRun: 0, stale: 0, valid: 3, invalid: 0, validationPending: 0, validationError: 0, neverValidated: 0 },
  cqlErrorCount: 0,
  cqlWarningCount: 0,
  fourEyes: { enabled: true, author: 'alice', submittedBy: undefined, selfApprovalBlocked: false },
  checkedAt: '2026-10-06T10:00:00',
}

// PAT-249 — the readiness panel: blockers and warnings with their named test cases, the tally,
// the four-eyes verdict for the current user, and a refresh button.
describe('ApprovalReadinessPanel', () => {
  it('shows a ready measure with its test case tally and the four-eyes note', () => {
    render(<ApprovalReadinessPanel readiness={base} isLoading={false} onRefresh={vi.fn()} />)

    expect(screen.getByText('editor.readiness.ready')).toBeInTheDocument()
    expect(screen.getByText('editor.readiness.testCaseTally')).toBeInTheDocument()
    expect(screen.getByTestId('four-eyes-info')).toBeInTheDocument()
    expect(screen.queryByTestId('four-eyes-blocked')).not.toBeInTheDocument()
  })

  it('lists blockers and warnings with the test cases concerned, and tells the author they cannot approve', () => {
    const onRefresh = vi.fn()
    const readiness: ApprovalReadiness = {
      ...base,
      ready: false,
      blockers: [
        { code: 'TEST_CASE_NOT_PASSING', count: 2, message: '2 of 3 test case(s) do not pass', items: ['wrong expectation (fail)', 'never run (pending)'] },
        { code: 'CQL_ERRORS', count: 1, message: 'CQL has 1 translation error(s)', items: ['line 7: Could not resolve identifier X'] },
      ],
      warnings: [{ code: 'TEST_CASE_NEVER_VALIDATED', count: 1, message: '1 test case(s) were never FHIR-validated', items: ['old'] }],
      fourEyes: { enabled: true, author: 'alice', submittedBy: 'alice', selfApprovalBlocked: true },
    }
    render(<ApprovalReadinessPanel readiness={readiness} isLoading={false} onRefresh={onRefresh} />)

    expect(screen.getByText('editor.readiness.notReady')).toBeInTheDocument()
    expect(screen.getByTestId('readiness-error-TEST_CASE_NOT_PASSING')).toHaveTextContent('wrong expectation (fail) · never run (pending)')
    expect(screen.getByTestId('readiness-error-CQL_ERRORS')).toHaveTextContent('line 7: Could not resolve identifier X')
    expect(screen.getByTestId('readiness-warning-TEST_CASE_NEVER_VALIDATED')).toBeInTheDocument()
    expect(screen.getByTestId('four-eyes-blocked')).toBeInTheDocument()

    fireEvent.click(screen.getByText('editor.readiness.refresh'))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('renders nothing but a spinner while the first check is loading', () => {
    const { container } = render(<ApprovalReadinessPanel readiness={undefined} isLoading onRefresh={vi.fn()} />)
    expect(screen.getByTestId('readiness-loading')).toBeInTheDocument()
    expect(container.querySelector('[data-testid="approval-readiness"]')).toBeNull()
  })
})
