import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import PopulationTracePanel from '../PopulationTracePanel'
import type { PopulationMembershipTrace } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const entries = [
  { populationType: 'initial-population', displayName: 'Initial Population', rawResult: true, effectiveResult: true, reasonCode: 'ip_true', memberCount: 2 },
  { populationType: 'numerator', displayName: 'Numerator', rawResult: true, effectiveResult: true, reasonCode: 'numer_true', memberCount: 1 },
]

// PAT-243 — an episode-based group's trace shows how many of the patient's episodes each population counts.
describe('PopulationTracePanel — episode counts', () => {
  it('adds an episode column for an Encounter-based group', () => {
    const trace: PopulationMembershipTrace = {
      groups: [{ groupId: 'group-1', scoringType: 'proportion', populationBasis: 'Encounter', populations: entries }],
    }
    render(<PopulationTracePanel trace={trace} />)

    expect(screen.getByText('populationTrace.columns.episodes')).toBeInTheDocument()
    expect(screen.getByTestId('episode-count-initial-population')).toHaveTextContent('2')
    expect(screen.getByTestId('episode-count-numerator')).toHaveTextContent('1')
  })

  it('has no episode column for a patient-based group', () => {
    const trace: PopulationMembershipTrace = {
      groups: [{ groupId: 'group-1', scoringType: 'proportion', populationBasis: 'boolean', populations: entries }],
    }
    render(<PopulationTracePanel trace={trace} />)

    expect(screen.queryByText('populationTrace.columns.episodes')).not.toBeInTheDocument()
    expect(screen.queryByTestId('episode-count-initial-population')).not.toBeInTheDocument()
  })
})
