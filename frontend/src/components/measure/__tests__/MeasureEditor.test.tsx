import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import MeasureEditor from '../MeasureEditor'
import type { MeasureDefinition } from '../../../types'

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

vi.mock('../../../hooks/useMeasures', () => ({
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
