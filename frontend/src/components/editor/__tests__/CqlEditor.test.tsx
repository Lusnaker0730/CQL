import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../../test/test-utils'

// Monaco editor is globally stubbed in src/test/setup.ts (vi.mock on
// '@monaco-editor/react' + 'monaco-editor'); the per-file mock that lived
// here got out of sync with PR #526's wrapper (didn't export `loader`).
import CqlEditor from '../CqlEditor'

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
