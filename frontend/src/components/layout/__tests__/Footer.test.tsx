import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'
import Footer from '../Footer'

describe('Footer', () => {
  it('should render cursor position and error indicators', () => {
    render(<Footer />)
    expect(screen.getByText(/Ln \d+, Col \d+/)).toBeInTheDocument()
    expect(screen.getByText('No errors')).toBeInTheDocument()
  })
})
