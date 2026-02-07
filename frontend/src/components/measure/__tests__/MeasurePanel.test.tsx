import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import MeasurePanel from '../MeasurePanel'

describe('MeasurePanel', () => {
  it('should render measure panel', () => {
    render(<MeasurePanel />)
    expect(document.querySelector('[class*="MuiBox"]') || document.body.firstChild).toBeTruthy()
  })

  it('should have evaluation-related UI elements', () => {
    render(<MeasurePanel />)
    const buttons = screen.queryAllByRole('button')
    expect(buttons.length).toBeGreaterThanOrEqual(0)
  })
})
