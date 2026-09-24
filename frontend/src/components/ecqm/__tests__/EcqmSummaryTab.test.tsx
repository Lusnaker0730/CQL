import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmSummaryTab from '../EcqmSummaryTab'
import type { EcqmArtifact } from '../../../types/ecqm'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const artifact: EcqmArtifact = {
  id: 1,
  name: 'Demo',
  version: '1.0.0',
  description: '',
  status: 'draft',
  fhirVersion: '4.0.1',
  scoringType: 'proportion',
  populationBasis: 'boolean',
  improvementNotation: 'increase',
  populationGroups: [],
  supplementalData: [],
  stratifiers: [],
  baseElements: [],
  parameters: [],
  publishedMeasureId: null,
  ownerUsername: 'alice',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  measureTypes: ['process'],
  effectiveStart: '2026-01-01',
  effectiveEnd: '2026-12-31',
  experimental: false,
}

// PAT-236 — the summary tab carries the standard metadata and speaks the artifact API's
// partial-update dialect: a cleared date goes out as '' (an omitted key would keep the old value).
describe('EcqmSummaryTab — standard metadata', () => {
  it('shows the section with the artifact values', () => {
    render(<EcqmSummaryTab artifact={artifact} onChange={vi.fn()} />)

    expect(screen.getByText('summary.standardMetadata')).toBeInTheDocument()
    expect(screen.getByText('standardMetadata.types.process')).toBeInTheDocument()
    expect(screen.getByTestId('standard-metadata-effectiveStart')).toHaveValue('2026-01-01')
    expect(screen.getByRole('switch', { name: 'standardMetadata.experimental' })).not.toBeChecked()
  })

  it('a cleared date is sent as "" and a set date as itself', () => {
    const onChange = vi.fn()
    render(<EcqmSummaryTab artifact={artifact} onChange={onChange} />)

    fireEvent.change(screen.getByTestId('standard-metadata-effectiveEnd'), { target: { value: '' } })
    expect(onChange).toHaveBeenLastCalledWith({ effectiveEnd: '' })

    fireEvent.change(screen.getByTestId('standard-metadata-approvalDate'), { target: { value: '2026-03-01' } })
    expect(onChange).toHaveBeenLastCalledWith({ approvalDate: '2026-03-01' })

    fireEvent.click(screen.getByRole('switch', { name: 'standardMetadata.experimental' }))
    expect(onChange).toHaveBeenLastCalledWith({ experimental: true })
  })
})
