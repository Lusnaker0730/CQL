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

// PAT-233 — value stratifiers: the stratum is the expression's value, not true / false.
describe('EcqmStratifiersTab — value stratifiers', () => {
  const criteriaStrat = { stratifierId: 's1', description: '', criteria: { type: 'and', children: [] } as never }

  it('defaults to a criteria stratifier and switches to "by gender" when the value kind is chosen', () => {
    const onChange = vi.fn()
    render(<EcqmStratifiersTab templates={[]} modifiers={[]} stratifiers={[criteriaStrat]} onChange={onChange} />)

    expect(screen.getByTestId('tree-editor')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'stratifiers.kind.criteria', pressed: true })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'stratifiers.kind.value' }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0][0]).toMatchObject({
      stratifierId: 's1', kind: 'value', value: { source: 'gender' },
    })
    // the condition tree is kept, so switching back loses nothing
    expect(onChange.mock.calls[0][0][0].criteria).toEqual(criteriaStrat.criteria)
  })

  it('seeds the default age bands when the source becomes age bands, and edits a band', () => {
    const onChange = vi.fn()
    const first = render(<EcqmStratifiersTab templates={[]} modifiers={[]} onChange={onChange}
      stratifiers={[{ ...criteriaStrat, kind: 'value', value: { source: 'gender' } }]} />)

    expect(screen.queryByTestId('tree-editor')).not.toBeInTheDocument()
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'stratifiers.value.source' }))
    fireEvent.click(screen.getByRole('option', { name: 'stratifiers.value.sources.ageBands' }))
    expect(onChange).toHaveBeenCalledTimes(1)
    const seeded = onChange.mock.calls[0][0][0]
    expect(seeded.value.source).toBe('ageBands')
    expect(seeded.value.bands.map((b: { label: string }) => b.label)).toEqual(['0-17', '18-49', '50-64', '65+'])

    // re-render with the state the parent would now hold
    first.unmount()
    onChange.mockClear()
    render(<EcqmStratifiersTab templates={[]} modifiers={[]} onChange={onChange} stratifiers={[seeded]} />)
    // three inputs per band: label, min, max
    const inputs = screen.getAllByLabelText('stratifiers.value.bandAria')
    expect(inputs).toHaveLength(12)
    fireEvent.change(inputs[11], { target: { value: '80' } }) // last band's "to"
    const edited = onChange.mock.calls[0][0][0]
    expect(edited.value.bands[3]).toEqual({ label: '65-80', min: 65, max: 80 }) // label followed the bounds

    fireEvent.click(screen.getByRole('button', { name: 'stratifiers.value.addBand' }))
    const added = onChange.mock.calls[1][0][0]
    expect(added.value.bands).toHaveLength(5)
    expect(added.value.bands[4]).toEqual({ label: '', min: undefined }) // the seeded last band has no "to" yet
  })

  it('warns about an invalid band instead of silently dropping it', () => {
    render(<EcqmStratifiersTab templates={[]} modifiers={[]} onChange={vi.fn()}
      stratifiers={[{ ...criteriaStrat, kind: 'value', value: { source: 'ageBands', bands: [{ label: 'a<b', min: 60, max: 40 }] } }]} />)

    expect(screen.getByRole('alert')).toHaveTextContent('stratifiers.bandErrors.label')
    expect(screen.getByRole('alert')).toHaveTextContent('stratifiers.bandErrors.order')
  })
})
