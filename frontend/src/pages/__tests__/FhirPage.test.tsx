import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import FhirPage from '../FhirPage'

vi.mock('../../components/fhir/FhirBrowser', () => ({
  default: () => <div data-testid="fhir-browser">FhirBrowser</div>,
}))

vi.mock('../../components/fhir/ImplementationGuideBrowser', () => ({
  default: () => <div data-testid="ig-browser">ImplementationGuideBrowser</div>,
}))

vi.mock('../../components/ehr/EhrConnectionList', () => ({
  default: () => <div data-testid="ehr-list">EhrConnectionList</div>,
}))

describe('FhirPage', () => {
  it('should render page title and subtitle', () => {
    render(<FhirPage />)
    expect(screen.getByText('page.title')).toBeInTheDocument()
    expect(screen.getByText('page.subtitle')).toBeInTheDocument()
  })

  it('should render three tabs', () => {
    render(<FhirPage />)
    expect(screen.getByText('page.tabFhirBrowser')).toBeInTheDocument()
    expect(screen.getByText('page.tabTwCoreIg')).toBeInTheDocument()
    expect(screen.getByText('page.tabEhrConnections')).toBeInTheDocument()
  })

  it('should show FhirBrowser by default', () => {
    render(<FhirPage />)
    expect(screen.getByTestId('fhir-browser')).toBeInTheDocument()
  })

  it('should switch to IG browser on tab click', async () => {
    const user = userEvent.setup()
    render(<FhirPage />)

    await user.click(screen.getByText('page.tabTwCoreIg'))

    expect(screen.getByTestId('ig-browser')).toBeInTheDocument()
    expect(screen.queryByTestId('fhir-browser')).not.toBeInTheDocument()
  })

  it('should switch to EHR connections on tab click', async () => {
    const user = userEvent.setup()
    render(<FhirPage />)

    await user.click(screen.getByText('page.tabEhrConnections'))

    expect(screen.getByTestId('ehr-list')).toBeInTheDocument()
    expect(screen.queryByTestId('fhir-browser')).not.toBeInTheDocument()
  })

  it('should switch back to FHIR browser', async () => {
    const user = userEvent.setup()
    render(<FhirPage />)

    await user.click(screen.getByText('page.tabTwCoreIg'))
    await user.click(screen.getByText('page.tabFhirBrowser'))

    expect(screen.getByTestId('fhir-browser')).toBeInTheDocument()
  })
})
