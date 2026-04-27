import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmStratifiersTab from '../EcqmStratifiersTab'

// The tree editor is heavy and unrelated to interlock behavior; stub it out.
vi.mock('../EcqmPopulationTreeEditor', () => ({
  default: ({ label }: { label: string }) => (
    <div data-testid="tree-editor">{label}</div>
  ),
}))

describe('EcqmStratifiersTab — PAT-129 dual-IP interlock', () => {
  const baseProps = {
    templates: [],
    modifiers: [],
    stratifiers: [
      { stratifierId: 's1', description: 'age band', criteria: { type: 'and', children: [] } as never },
    ],
  }

  it('renders the warning Alert and disables Add when disabledReason is set', () => {
    const onChange = vi.fn()
    render(
      <EcqmStratifiersTab
        {...baseProps}
        disabledReason="Stratifiers not allowed: dual-IP active"
        onChange={onChange}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('dual-IP active')

    const addButton = screen.getByRole('button', { name: /add stratifier/i })
    expect(addButton).toBeDisabled()

    fireEvent.click(addButton)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('keeps existing stratifier rows visible (so they reappear when interlock clears) but blocks delete', () => {
    const onChange = vi.fn()
    render(
      <EcqmStratifiersTab
        {...baseProps}
        disabledReason="cannot edit"
        onChange={onChange}
      />,
    )

    // Existing row still rendered (tree editor stub still mounted)
    expect(screen.getByTestId('tree-editor')).toBeInTheDocument()

    const deleteButton = screen.getByRole('button', { name: /remove stratifier 1/i })
    expect(deleteButton).toBeDisabled()
    fireEvent.click(deleteButton)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('Add and Delete work normally when disabledReason is undefined', () => {
    const onChange = vi.fn()
    render(<EcqmStratifiersTab {...baseProps} onChange={onChange} />)

    // No alert
    expect(screen.queryByRole('alert')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: /add stratifier/i }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(2)

    onChange.mockClear()
    fireEvent.click(screen.getByRole('button', { name: /remove stratifier 1/i }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(0)
  })
})
