import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmStratifiersTab from '../EcqmStratifiersTab'

// The shared test-utils wrapper does NOT initialize i18next — `useTranslation`
// falls back to returning the key string verbatim. Mock it so we can both
// drive simple interpolation (`{{number}}` etc.) and rely on stable, English
// — independent label strings in our queries.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

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

    const addButton = screen.getByRole('button', { name: 'stratifiers.addStratifier' })
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

    const deleteButton = screen.getByRole('button', { name: 'stratifiers.removeStratifier' })
    expect(deleteButton).toBeDisabled()
    fireEvent.click(deleteButton)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('Add and Delete work normally when disabledReason is undefined', () => {
    const onChange = vi.fn()
    render(<EcqmStratifiersTab {...baseProps} onChange={onChange} />)

    expect(screen.queryByRole('alert')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'stratifiers.addStratifier' }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(2)

    onChange.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'stratifiers.removeStratifier' }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(0)
  })
})
