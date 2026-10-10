import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import MeasureEditor from '../MeasureEditor'
import type { MeasureDefinition, ApprovalReadiness } from '../../../types'

// test-utils does NOT initialize i18next; useTranslation falls back to raw
// keys. Mock so queries match the key strings (with simple {{var}} interp).
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// Workspace tabs are large + unrelated to workflow button behavior; stub them
// so the test focuses on the toolbar.
vi.mock('../MeasureDetailsTab', () => ({ default: () => <div data-testid="details-tab" /> }))
vi.mock('../MeasureCqlTab', () => ({ default: () => <div /> }))
vi.mock('../DataRequirementsTab', () => ({ default: () => <div /> }))
vi.mock('../PopulationCriteriaTab', () => ({ default: () => <div /> }))
vi.mock('../MeasureEvaluationTab', () => ({ default: () => <div /> }))
vi.mock('../MeasureReportHistory', () => ({ default: () => <div /> }))
vi.mock('../TestCasesTab', () => ({ default: () => <div /> }))
vi.mock('../WorkflowIndicator', () => ({ default: () => <div /> }))
vi.mock('../MeasureValidationPanel', () => ({ default: () => <div /> }))
vi.mock('../ApprovalReadinessPanel', () => ({ default: () => <div data-testid="readiness-panel" /> }))
vi.mock('../MeasureShareDialog', () => ({ default: () => <div /> }))
vi.mock('../AuditTrailDialog', () => ({ default: () => <div /> }))
vi.mock('../../editor/CreateVersionDialog', () => ({ default: () => <div /> }))
vi.mock('../../editor/VersionHistoryDialog', () => ({ default: () => <div /> }))
vi.mock('../../editor/VersionDiffDialog', () => ({ default: () => <div /> }))

// Mock the workflow mutation hooks. Each hook returns a mutation-like object
// whose `mutate(id, opts)` we capture for assertions and drive via
// onSuccess/onError to verify the shared runner's behavior.
type MutOpts = { onSuccess?: (data: unknown) => void; onError?: (e: unknown) => void }
function buildMutation() {
  const calls: { id: unknown; opts: MutOpts | undefined }[] = []
  let pending = false
  const mutate = vi.fn((id: unknown, opts?: MutOpts) => {
    calls.push({ id, opts })
  })
  return {
    mutate,
    get isPending() { return pending },
    setPending: (v: boolean) => { pending = v },
    calls,
  }
}

const lockMutation = buildMutation()
const unlockMutation = buildMutation()
const submitMutation = buildMutation()
// PAT-249: the readiness query the editor reads; tests set `readinessData` to drive the buttons.
let readinessData: ApprovalReadiness | undefined
const readinessRefetch = vi.fn()

vi.mock('../../../hooks/useMeasures', () => ({
  useApprovalReadiness: () => ({ data: readinessData, isFetching: false, refetch: readinessRefetch }),
  useSubmitForReview: () => submitMutation,
  useApproveMeasure: () => buildMutation(),
  useRejectMeasure: () => buildMutation(),
  useRetireMeasure: () => buildMutation(),
  useLockMeasure: () => lockMutation,
  useUnlockMeasure: () => unlockMutation,
}))

vi.mock('../../../utils/validation', async () => {
  const real = await vi.importActual<Record<string, unknown>>('../../../utils/validation')
  return { ...real, getStoredUsername: () => 'alice' }
})

const baseMeasure: MeasureDefinition = {
  id: 7,
  name: 'M7',
  title: 'Test Measure',
  version: '1.0.0',
  description: '',
  status: 'draft',
  scoringType: 'proportion',
  cqlContent: 'library X version \'1\'',
  ownerUsername: 'alice',
} as unknown as MeasureDefinition

describe('MeasureEditor — PAT-130 workflow button unification', () => {
  beforeEach(() => {
    lockMutation.calls.length = 0
    unlockMutation.calls.length = 0
    submitMutation.calls.length = 0
    readinessData = undefined
    lockMutation.mutate.mockClear()
    unlockMutation.mutate.mockClear()
    submitMutation.mutate.mockClear()
  })

  it('Lock button funnels through the shared runner and updates the measure on success', () => {
    const onMeasureUpdate = vi.fn()
    render(<MeasureEditor measure={baseMeasure} onMeasureUpdate={onMeasureUpdate} />)

    // Buttons render with t('editor.buttons.lock') → 'editor.buttons.lock'
    fireEvent.click(screen.getByRole('button', { name: 'editor.buttons.lock' }))
    expect(lockMutation.mutate).toHaveBeenCalledTimes(1)
    expect(lockMutation.mutate.mock.calls[0][0]).toBe(7)

    const opts = lockMutation.calls[0].opts!
    const updated = { ...baseMeasure, lockedBy: 'alice' }
    opts.onSuccess!(updated)
    expect(onMeasureUpdate).toHaveBeenCalledWith(updated)
  })

  it('Unlock button uses the same runner and produces no success alert (quiet success)', () => {
    const lockedByMe = { ...baseMeasure, lockedBy: 'alice', lockedAt: '2026-04-27T00:00:00Z' }
    render(<MeasureEditor measure={lockedByMe} onMeasureUpdate={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'editor.buttons.unlock' }))
    expect(unlockMutation.mutate).toHaveBeenCalledTimes(1)

    unlockMutation.calls[0].opts!.onSuccess!({ ...lockedByMe, lockedBy: null })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('Submit-for-review still shows the success alert (loud success)', async () => {
    render(<MeasureEditor measure={baseMeasure} onMeasureUpdate={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'editor.buttons.submitForReview' }))
    expect(submitMutation.mutate).toHaveBeenCalledTimes(1)

    submitMutation.calls[0].opts!.onSuccess!({
      ...baseMeasure,
      status: 'in-review',
    })

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
  })

  it('runner surfaces API error message on failure for lock action', async () => {
    render(<MeasureEditor measure={baseMeasure} onMeasureUpdate={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'editor.buttons.lock' }))
    lockMutation.calls[0].opts!.onError!(new Error('Database is locked'))
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/Database is locked|editor\.errors\.lockFailed/),
    )
  })
})

// BUG-147 — only a draft's logic can change: other statuses show why and (unless under review)
// offer to create a new version.
describe('MeasureEditor — BUG-147 logic lock notice', () => {
  it('a draft has no notice', () => {
    render(<MeasureEditor measure={baseMeasure} onMeasureUpdate={vi.fn()} />)
    expect(screen.queryByTestId('logic-locked-notice')).not.toBeInTheDocument()
  })

  it('PAT-249: blockers from the readiness check disable "Submit for Review" and show the panel', () => {
    readinessData = {
      measureId: 7, status: 'draft', ready: false,
      blockers: [{ code: 'TEST_CASE_NOT_PASSING', count: 1, message: '1 of 1 test case(s) do not pass', items: ['x (fail)'] }],
      warnings: [], cqlErrorCount: 0, cqlWarningCount: 0,
      fourEyes: { enabled: true, author: 'alice', selfApprovalBlocked: true },
      checkedAt: '2026-10-06T00:00:00',
    }
    render(<MeasureEditor measure={baseMeasure} onMeasureUpdate={vi.fn()} />)
    expect(screen.getByTestId('readiness-panel')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'editor.buttons.submitForReview' })).toBeDisabled()

    readinessData = { ...readinessData, ready: true, blockers: [] }
    render(<MeasureEditor measure={{ ...baseMeasure, id: 8 }} onMeasureUpdate={vi.fn()} />)
    expect(screen.getAllByRole('button', { name: 'editor.buttons.submitForReview' }).at(-1)).not.toBeDisabled()
  })

  it('an approved measure explains the lock and offers a new version', () => {
    render(<MeasureEditor measure={{ ...baseMeasure, status: 'active' }} onMeasureUpdate={vi.fn()} />)
    expect(screen.getByTestId('logic-locked-notice')).toHaveTextContent('editor.logicLocked.approved')
    expect(screen.getByRole('button', { name: 'editor.logicLocked.createVersion' })).toBeInTheDocument()
  })

  it('a measure under review explains the lock without the new-version shortcut', () => {
    render(<MeasureEditor measure={{ ...baseMeasure, status: 'in-review' }} onMeasureUpdate={vi.fn()} />)
    expect(screen.getByTestId('logic-locked-notice')).toHaveTextContent('editor.logicLocked.inReview')
    expect(screen.queryByRole('button', { name: 'editor.logicLocked.createVersion' })).not.toBeInTheDocument()
  })
})
