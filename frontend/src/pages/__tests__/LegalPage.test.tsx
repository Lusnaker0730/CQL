import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import LegalPage from '../LegalPage'

// The critical assertion: both legal pages show the DRAFT / pending-legal-review banner,
// so the placeholder content is never mistaken for binding terms. i18n returns keys in tests.
describe('LegalPage', () => {
  it('terms page shows the draft banner and title', () => {
    render(<LegalPage doc="terms" />)
    expect(screen.getByText('legal.draftBanner')).toBeInTheDocument()
    // title appears in header + body
    expect(screen.getAllByText('legal.terms.title').length).toBeGreaterThan(0)
    expect(screen.getByText(/legal\.terms\.sections\.disclaimer\.heading/)).toBeInTheDocument()
  })

  it('privacy page shows the draft banner and a PHI section', () => {
    render(<LegalPage doc="privacy" />)
    expect(screen.getByText('legal.draftBanner')).toBeInTheDocument()
    expect(screen.getAllByText('legal.privacy.title').length).toBeGreaterThan(0)
    expect(screen.getByText(/legal\.privacy\.sections\.phi\.heading/)).toBeInTheDocument()
  })
})
