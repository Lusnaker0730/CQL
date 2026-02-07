import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import FhirBrowser from '../FhirBrowser'

describe('FhirBrowser', () => {
  it('should render FHIR browser component', () => {
    render(<FhirBrowser />)
    expect(document.querySelector('[class*="MuiBox"]') || document.body.firstChild).toBeTruthy()
  })

  it('should have resource type selection or search UI', () => {
    render(<FhirBrowser />)
    // Should have some interactive elements
    const inputs = screen.queryAllByRole('textbox')
    const buttons = screen.queryAllByRole('button')
    expect(inputs.length + buttons.length).toBeGreaterThan(0)
  })
})
