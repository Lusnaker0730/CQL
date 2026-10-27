import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import TerminologyDegradedHint from '../TerminologyDegradedHint'
import type { ValueSetSearchResult } from '../../../types'

// test-utils does NOT initialize i18next; mock so assertions match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { changeLanguage: vi.fn() } }),
}))

const PLATFORM: ValueSetSearchResult = {
  id: '7', url: 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders', name: 'HbA1cOrders', title: 'HbA1c order codes',
  source: 'platform', version: '1.0.0', status: 'active',
}
const VSAC: ValueSetSearchResult = {
  id: '2.16.840.1.113883.3.464.1003.103.12.1001', url: 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001',
  name: 'Diabetes', title: 'Diabetes', source: 'remote',
}

describe('TerminologyDegradedHint — PAT-275', () => {
  it('renders nothing without a search or while the remote terminology server took part', () => {
    const { rerender } = render(<TerminologyDegradedHint />)
    expect(screen.queryByRole('alert')).toBeNull()

    rerender(<TerminologyDegradedHint search={{ valueSets: [PLATFORM, VSAC], terminologyDegraded: false }} />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('says only this installation\'s value sets are listed when nothing remote came back', () => {
    render(<TerminologyDegradedHint search={{ valueSets: [PLATFORM], terminologyDegraded: true }} />)
    expect(screen.getByRole('alert')).toHaveTextContent('valueSet.remoteDegraded')
  })

  it('says the results may be incomplete when the VSAC fallback contributed remote entries', () => {
    render(<TerminologyDegradedHint search={{ valueSets: [PLATFORM, VSAC], terminologyDegraded: true }} />)
    expect(screen.getByRole('alert')).toHaveTextContent('valueSet.remoteDegradedPartial')
  })

  it('shows the platform-only wording for an empty degraded answer', () => {
    render(<TerminologyDegradedHint search={{ valueSets: [], terminologyDegraded: true }} />)
    expect(screen.getByRole('alert')).toHaveTextContent('valueSet.remoteDegraded')
  })
})
