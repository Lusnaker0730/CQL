import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import CdsPage from '../CdsPage'

vi.mock('../../components/cds/CdsPanel', () => ({
  default: () => <div data-testid="cds-panel">CdsPanel</div>,
}))

vi.mock('../../components/editor/CqlEditor', () => ({
  default: () => <div data-testid="cql-editor">CqlEditor</div>,
}))

describe('CdsPage', () => {
  it('should render page title', () => {
    render(<CdsPage />)
    expect(screen.getByText('page.title')).toBeInTheDocument()
  })

  it('should render page subtitle', () => {
    render(<CdsPage />)
    expect(screen.getByText('page.subtitle')).toBeInTheDocument()
  })

  it('should render CqlEditor', () => {
    render(<CdsPage />)
    expect(screen.getByTestId('cql-editor')).toBeInTheDocument()
  })

  it('should render CdsPanel', () => {
    render(<CdsPage />)
    expect(screen.getByTestId('cds-panel')).toBeInTheDocument()
  })
})
