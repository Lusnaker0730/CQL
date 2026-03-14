import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import ExecutionPanel from '../ExecutionPanel'

describe('ExecutionPanel', () => {
  it('should render execution panel', () => {
    render(<ExecutionPanel />)
    // Check for any part of the execution panel - FHIR server or Patient input
    const inputs = screen.getAllByRole('textbox')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('should show FHIR server URL field', () => {
    render(<ExecutionPanel />)
    // The FhirServerUrlField renders an Autocomplete with the i18n key as label
    const combobox = screen.getByRole('combobox')
    expect(combobox).toBeInTheDocument()
  })

  it('should render execute button', () => {
    render(<ExecutionPanel />)
    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBeGreaterThan(0)
  })
})
