import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import GradientButton from '../GradientButton'

describe('GradientButton', () => {
  it('should render with label text', () => {
    render(<GradientButton>Click Me</GradientButton>)
    expect(screen.getByRole('button', { name: 'Click Me' })).toBeInTheDocument()
  })

  it('should fire onClick when clicked', () => {
    const handleClick = vi.fn()
    render(<GradientButton onClick={handleClick}>Save</GradientButton>)

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('should not fire onClick when disabled', () => {
    const handleClick = vi.fn()
    render(<GradientButton disabled onClick={handleClick}>Save</GradientButton>)

    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).toBeDisabled()

    fireEvent.click(button)
    expect(handleClick).not.toHaveBeenCalled()
  })

  it('should default to contained variant and small size', () => {
    render(<GradientButton>Default</GradientButton>)
    const button = screen.getByRole('button', { name: 'Default' })
    expect(button).toHaveClass('MuiButton-contained')
    expect(button).toHaveClass('MuiButton-sizeSmall')
  })

  it('should accept custom variant and size props', () => {
    render(<GradientButton variant="outlined" size="large">Custom</GradientButton>)
    const button = screen.getByRole('button', { name: 'Custom' })
    expect(button).toHaveClass('MuiButton-outlined')
    expect(button).toHaveClass('MuiButton-sizeLarge')
  })
})
