import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import TerminologyPage from '../TerminologyPage'

vi.mock('../../components/terminology/TerminologyBrowser', () => ({
  default: () => <div data-testid="terminology-browser">TerminologyBrowser</div>,
}))

describe('TerminologyPage', () => {
  it('should render page title', () => {
    render(<TerminologyPage />)
    expect(screen.getByText('page.title')).toBeInTheDocument()
  })

  it('should render page subtitle', () => {
    render(<TerminologyPage />)
    expect(screen.getByText('page.subtitle')).toBeInTheDocument()
  })

  it('should render TerminologyBrowser', () => {
    render(<TerminologyPage />)
    expect(screen.getByTestId('terminology-browser')).toBeInTheDocument()
  })
})
