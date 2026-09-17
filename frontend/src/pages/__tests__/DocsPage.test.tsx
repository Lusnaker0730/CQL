import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import DocsPage from '../DocsPage'

// i18n returns keys in tests; assert the structural keys render.
describe('DocsPage', () => {
  it('renders the docs hub sections', () => {
    render(<DocsPage />)
    expect(screen.getByText('docs.title')).toBeInTheDocument()
    expect(screen.getByText('docs.gettingStarted.title')).toBeInTheDocument()
    expect(screen.getByText('docs.features.title')).toBeInTheDocument()
    expect(screen.getByText('docs.integration.title')).toBeInTheDocument()
  })

  it('renders the four getting-started steps', () => {
    render(<DocsPage />)
    expect(screen.getByText('docs.gettingStarted.steps.apply.title')).toBeInTheDocument()
    expect(screen.getByText('docs.gettingStarted.steps.build.title')).toBeInTheDocument()
  })
})
