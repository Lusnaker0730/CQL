import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import TerminologyDegradedHint from '../TerminologyDegradedHint'

// test-utils does NOT initialize i18next; mock so assertions match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { changeLanguage: vi.fn() } }),
}))

describe('TerminologyDegradedHint — PAT-275', () => {
  it('renders nothing while the remote terminology server took part in the search', () => {
    render(<TerminologyDegradedHint show={false} />)
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.queryByText('valueSet.remoteDegraded')).toBeNull()
  })

  it('shows the warning when the search was answered without the remote server', () => {
    render(<TerminologyDegradedHint show />)
    expect(screen.getByRole('alert')).toHaveTextContent('valueSet.remoteDegraded')
  })
})
