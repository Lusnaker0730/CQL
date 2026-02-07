import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'

// Mock Monaco editor since it requires a browser environment
vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange }: { value: string; onChange?: (v: string) => void }) => (
    <textarea
      data-testid="monaco-editor"
      value={value}
      onChange={(e) => onChange?.(e.target.value)}
    />
  ),
}))

// Dynamically import after mocking
const { default: CqlEditor } = await import('../CqlEditor')

describe('CqlEditor', () => {
  it('should render editor component', () => {
    render(<CqlEditor />)
    expect(screen.getByTestId('monaco-editor')).toBeInTheDocument()
  })

  it('should display default CQL content', () => {
    render(<CqlEditor />)
    const editor = screen.getByTestId('monaco-editor') as HTMLTextAreaElement
    expect(editor.value).toContain('DiabetesManagement')
  })
})
