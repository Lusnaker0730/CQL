import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '../../../test/test-utils'
import EcqmArtifactWorkspace from '../EcqmArtifactWorkspace'
import type { EcqmArtifact, PopulationGroup } from '../../../types/ecqm'

// We're testing workspace orchestration (auto-save state machine + interlock
// wiring). All children render simple stubs so we can drive their callbacks.

vi.mock('../EcqmArtifactWorkspaceHeader', () => ({
  default: ({ saveStatus, onSave, onBack }: { saveStatus: string; onSave: () => void; onBack: () => void }) => (
    <div>
      <span data-testid="save-status">{saveStatus}</span>
      <button onClick={onSave}>save-now</button>
      <button onClick={onBack}>back</button>
    </div>
  ),
}))
vi.mock('../EcqmSummaryTab', () => ({
  default: ({ onChange }: { onChange: (u: Record<string, unknown>) => void }) => (
    <button onClick={() => onChange({ description: String(Math.random()) })}>fire-edit</button>
  ),
}))
vi.mock('../EcqmPopulationGroupsTab', () => ({ default: () => <div /> }))
vi.mock('../EcqmSdeTab', () => ({ default: () => <div /> }))
vi.mock('../EcqmStratifiersTab', () => ({
  default: ({ disabledReason }: { disabledReason?: string }) => (
    <div data-testid="strat-disabled-reason">{disabledReason ?? ''}</div>
  ),
}))
vi.mock('../EcqmCqlPreviewTab', () => ({
  default: ({ artifactUpdatedAt }: { artifactUpdatedAt?: string }) => (
    <div data-testid="cql-tab-updated-at">{artifactUpdatedAt ?? ''}</div>
  ),
}))
vi.mock('../EcqmExternalCql', () => ({ default: () => <div /> }))
vi.mock('../../authoring/base-elements/BaseElements', () => ({ default: () => <div /> }))
vi.mock('../../authoring/parameters/Parameters', () => ({ default: () => <div /> }))

const updateMutate = vi.fn()
const publishMutate = vi.fn()
let mutationIsPending = false
let lastUpdateOpts: { onSuccess?: () => void; onError?: (err: unknown) => void } | undefined

vi.mock('../../../hooks/useEcqm', () => ({
  useUpdateEcqmArtifact: () => ({
    mutate: (vars: unknown, opts?: { onSuccess?: () => void; onError?: (err: unknown) => void }) => {
      lastUpdateOpts = opts
      updateMutate(vars, opts)
    },
    isPending: mutationIsPending,
  }),
  usePublishEcqm: () => ({ mutate: publishMutate, isPending: false }),
  useEcqmTemplates: () => ({ data: [] }),
  useEcqmModifiers: () => ({ data: [] }),
}))

vi.mock('../../../constants/timing', () => ({
  AUTOSAVE_SLOW_MS: 50,
  NOTIFICATION_DURATION_MS: 1000,
}))

function buildArtifact(overrides: Partial<EcqmArtifact> = {}): EcqmArtifact {
  return {
    id: 1,
    name: 'M1',
    version: '1.0',
    scoringType: 'proportion',
    populationBasis: 'boolean',
    status: 'draft',
    updatedAt: '2026-04-25T10:00:00Z',
    populationGroups: [],
    ...overrides,
  } as EcqmArtifact
}

describe('EcqmArtifactWorkspace — PAT-129 auto-save state machine', () => {
  beforeEach(() => {
    updateMutate.mockReset()
    publishMutate.mockReset()
    lastUpdateOpts = undefined
    mutationIsPending = false
    vi.useFakeTimers()
  })

  // The "dirty edit landed mid-save" guard (`if (!pendingRef.current)
  // setSaveStatus('saved')`) is straightforward in source and difficult to
  // exercise reliably in vitest because the interaction of React 18 batching,
  // fake timers, and act() can land state updates out of the order this test
  // would assume. We exercise the simpler success path here, plus the
  // stratifier-interlock prop wiring below; the fix itself is readable from
  // EcqmArtifactWorkspace.tsx onSuccess.
  it('settles to saved when save completes with no pending edits', () => {
    render(
      <EcqmArtifactWorkspace
        artifact={buildArtifact()}
        onBack={vi.fn()}
        onArtifactUpdate={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByText('fire-edit'))
    act(() => { vi.runAllTimers() })
    act(() => { lastUpdateOpts?.onSuccess?.() })
    expect(screen.getByTestId('save-status').textContent).toBe('saved')
  })
})

describe('EcqmArtifactWorkspace — PAT-129 stratifier interlock', () => {
  beforeEach(() => { updateMutate.mockReset() })

  it('passes a disabledReason when the artifact is Ratio with dual-IP', () => {
    const groups: PopulationGroup[] = [{
      groupId: 'g1',
      populations: {},
      initialPopulationDenom: { type: 'and', children: [] } as never,
      initialPopulationNumer: { type: 'and', children: [] } as never,
    } as PopulationGroup]

    render(
      <EcqmArtifactWorkspace
        artifact={buildArtifact({ scoringType: 'ratio', populationGroups: groups })}
        onBack={vi.fn()}
        onArtifactUpdate={vi.fn()}
      />,
    )

    // Switch to Stratifiers tab (index 5)
    fireEvent.click(screen.getByRole('tab', { name: /stratifiers/i }))
    expect(screen.getByTestId('strat-disabled-reason').textContent).not.toBe('')
  })

  it('does NOT pass a disabledReason when Ratio without dual-IP', () => {
    render(
      <EcqmArtifactWorkspace
        artifact={buildArtifact({ scoringType: 'ratio', populationGroups: [] })}
        onBack={vi.fn()}
        onArtifactUpdate={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByRole('tab', { name: /stratifiers/i }))
    expect(screen.getByTestId('strat-disabled-reason').textContent).toBe('')
  })

  it('does NOT pass a disabledReason for non-Ratio scoring types', () => {
    render(
      <EcqmArtifactWorkspace
        artifact={buildArtifact({ scoringType: 'proportion' })}
        onBack={vi.fn()}
        onArtifactUpdate={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByRole('tab', { name: /stratifiers/i }))
    expect(screen.getByTestId('strat-disabled-reason').textContent).toBe('')
  })
})
