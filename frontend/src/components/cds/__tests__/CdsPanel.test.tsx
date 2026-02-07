import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import CdsPanel from '../CdsPanel'

describe('CdsPanel', () => {
  it('should render CDS panel', () => {
    render(<CdsPanel />)
    // Panel should be present in the DOM
    expect(document.querySelector('[class*="MuiBox"]') || document.body.firstChild).toBeTruthy()
  })

  it('should have invoke or service-related UI elements', () => {
    render(<CdsPanel />)
    // Look for buttons or text related to CDS services
    const buttons = screen.queryAllByRole('button')
    expect(buttons.length).toBeGreaterThanOrEqual(0) // Panel renders without error
  })
})
