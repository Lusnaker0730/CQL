import { describe, it, expect } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import StatusChip from '../StatusChip'

describe('StatusChip', () => {
  it('should render draft status', () => {
    render(<StatusChip status="draft" />)
    expect(screen.getByText('status.draft')).toBeInTheDocument()
  })

  it('should render active status', () => {
    render(<StatusChip status="active" />)
    expect(screen.getByText('status.active')).toBeInTheDocument()
  })

  it('should render retired status', () => {
    render(<StatusChip status="retired" />)
    expect(screen.getByText('status.retired')).toBeInTheDocument()
  })

  it('should render in-review status', () => {
    render(<StatusChip status="in-review" />)
    expect(screen.getByText('status.inReview')).toBeInTheDocument()
  })

  it('should fallback to draft for unknown status', () => {
    render(<StatusChip status="unknown" />)
    expect(screen.getByText('status.draft')).toBeInTheDocument()
  })

  it('should render with outlined variant', () => {
    render(<StatusChip status="active" />)
    const chip = screen.getByText('status.active').closest('.MuiChip-root')
    expect(chip).toHaveClass('MuiChip-outlined')
  })
})
