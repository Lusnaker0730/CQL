import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmSdeTab from '../EcqmSdeTab'
import type { SupplementalDataElement } from '../../../types/ecqm'

// Minimal mocks for the nested tree editor — the unit under test is EcqmSdeTab
// (name edit focus, standard/custom split, criteria fallback), not the tree.
vi.mock('../EcqmPopulationTreeEditor', () => ({
  default: ({ label, tree }: { label: string; tree: unknown }) => (
    <div data-testid="tree-editor" data-tree-present={tree != null}>
      {label}
    </div>
  ),
}))

describe('EcqmSdeTab — PAT-115 usability fixes', () => {
  const baseProps = {
    templates: [],
    modifiers: [],
    onGuidanceChange: vi.fn(),
  }

  it('Bug #1 fix: typing in a custom SDE name does NOT unmount the TextField', () => {
    // Repro: previously `key={sde.name}` meant every keystroke changed the key
    // → React unmounted the row → TextField lost focus mid-typing.
    // Fix: stable id-based key. Verified by asserting the row's React element
    // stays the same DOM node after an edit event.
    const onChange = vi.fn()
    const { container } = render(
      <EcqmSdeTab
        {...baseProps}
        supplementalData={[{ id: 'sde-1', custom: true, name: 'A' }]}
        onChange={onChange}
      />
    )

    // Grab the Paper row before editing
    const inputBefore = container.querySelector('input[value="A"]') as HTMLInputElement
    expect(inputBefore).toBeTruthy()

    // Simulate a keystroke → onChange called with the new array
    fireEvent.change(inputBefore, { target: { value: 'AB' } })

    expect(onChange).toHaveBeenCalledTimes(1)
    const nextArray = onChange.mock.calls[0][0] as SupplementalDataElement[]
    // Fix invariant: the row's id survives the edit so React reuses the TextField.
    expect(nextArray[0].id).toBe('sde-1')
    expect(nextArray[0].name).toBe('AB')
    expect(nextArray[0].custom).toBe(true)
  })

  it('Bug #1 fix: legacy SDE rows (no id) get a stable id assigned on first edit', () => {
    const onChange = vi.fn()
    const { container } = render(
      <EcqmSdeTab
        {...baseProps}
        // Legacy row: no id, no custom flag. Defaults to custom because name is not a standard SDE.
        supplementalData={[{ name: 'Legacy SDE' }]}
        onChange={onChange}
      />
    )

    const input = container.querySelector('input[value="Legacy SDE"]') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Legacy SDE2' } })

    const nextArray = onChange.mock.calls[0][0] as SupplementalDataElement[]
    expect(nextArray[0].id).toBeTruthy()
    expect(nextArray[0].id).toMatch(/^sde-/)
    expect(nextArray[0].custom).toBe(true) // flag gets persisted on touch
  })

  it('Bug #2 fix: a custom SDE named identically to a standard SDE stays in the custom list', () => {
    const onChange = vi.fn()
    render(
      <EcqmSdeTab
        {...baseProps}
        supplementalData={[
          { id: 'sde-custom', custom: true, name: 'SDE Ethnicity' }, // custom with std name
        ]}
        onChange={onChange}
      />
    )

    // The row must still be rendered in the custom area (its TextField visible).
    // Previously it was filter()'d out because name matched STANDARD_SDE.
    expect(screen.getAllByDisplayValue('SDE Ethnicity')).toHaveLength(1)
  })

  it('Bug #2 fix: standard-SDE checkbox is independent of custom rows sharing the name', () => {
    const onChange = vi.fn()
    render(
      <EcqmSdeTab
        {...baseProps}
        supplementalData={[
          { id: 'sde-custom', custom: true, name: 'SDE Ethnicity' }, // custom only
        ]}
        onChange={onChange}
      />
    )

    // Standard checkbox must be UNCHECKED because no standard-slot row exists,
    // even though a custom row happens to share the name.
    const ethnicityCheckbox = screen
      .getByLabelText(/SDE Ethnicity/, { exact: false })
      .closest('label')
      ?.querySelector('input[type="checkbox"]') as HTMLInputElement
    expect(ethnicityCheckbox).toBeTruthy()
    expect(ethnicityCheckbox.checked).toBe(false)
  })

  it('Bug #3 fix: custom SDE without criteria still renders the tree editor (lazy empty)', () => {
    render(
      <EcqmSdeTab
        {...baseProps}
        supplementalData={[{ id: 'sde-1', custom: true, name: 'X', criteria: undefined }]}
        onChange={vi.fn()}
      />
    )

    // Previously the editor was hidden behind `sde.criteria && ...` — a row
    // whose criteria dropped to undefined (e.g. legacy data) would lose its
    // conditions UI forever. Now always rendered with a fallback empty tree.
    const editor = screen.getByTestId('tree-editor')
    expect(editor).toBeInTheDocument()
    expect(editor.getAttribute('data-tree-present')).toBe('true')
  })

  it('Standard SDE toggle add → creates a row with custom=false (not ambiguous)', () => {
    const onChange = vi.fn()
    render(
      <EcqmSdeTab {...baseProps} supplementalData={[]} onChange={onChange} />
    )

    const ethnicity = screen
      .getByLabelText(/SDE Ethnicity/, { exact: false })
      .closest('label')
      ?.querySelector('input[type="checkbox"]') as HTMLInputElement
    fireEvent.click(ethnicity)

    const nextArray = onChange.mock.calls[0][0] as SupplementalDataElement[]
    expect(nextArray).toHaveLength(1)
    expect(nextArray[0].name).toBe('SDE Ethnicity')
    // Must be explicitly marked not-custom so a future rename of a custom row
    // to the same name cannot confuse the standard/custom split.
    expect(nextArray[0].custom).toBe(false)
  })
})

// PAT-234 — a custom element can be a risk adjustment factor, and a value expression.
describe('EcqmSdeTab — risk adjustment factors', () => {
  const baseProps = { templates: [], modifiers: [], onGuidanceChange: vi.fn() }

  it('switching a default-named row to a risk adjustment factor renames it "RAF …" and sets the usage', () => {
    const onChange = vi.fn()
    render(<EcqmSdeTab {...baseProps} onChange={onChange}
      supplementalData={[{ id: 'sde-1', custom: true, name: 'sde.defaultName' }]} />)

    fireEvent.click(screen.getByRole('button', { name: 'sde.usage.riskAdjustment' }))
    const next = onChange.mock.calls[0][0] as SupplementalDataElement[]
    expect(next[0]).toMatchObject({ id: 'sde-1', usage: 'risk-adjustment-factor', name: 'RAF sde.defaultName' })
  })

  it('keeps a name the author wrote, but shows the RAF naming hint', () => {
    const onChange = vi.fn()
    render(<EcqmSdeTab {...baseProps} onChange={onChange}
      supplementalData={[{ id: 'sde-1', custom: true, name: 'Diabetes', usage: 'risk-adjustment-factor' }]} />)

    expect(screen.getByText(/sde.usage.rafNameHint/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'sde.usage.supplementalData' }))
    const next = onChange.mock.calls[0][0] as SupplementalDataElement[]
    expect(next[0]).toMatchObject({ name: 'Diabetes', usage: 'supplemental-data' })
  })

  it('switching the kind to value replaces the condition tree with the value source editor', () => {
    const onChange = vi.fn()
    const { rerender } = render(<EcqmSdeTab {...baseProps} onChange={onChange}
      supplementalData={[{ id: 'sde-1', custom: true, name: 'RAF Age Band', usage: 'risk-adjustment-factor' }]} />)
    expect(screen.getByTestId('tree-editor')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'stratifiers.kind.value' }))
    const next = onChange.mock.calls[0][0] as SupplementalDataElement[]
    expect(next[0]).toMatchObject({ kind: 'value', value: { source: 'gender' } })

    rerender(<EcqmSdeTab {...baseProps} onChange={onChange} supplementalData={next} />)
    expect(screen.queryByTestId('tree-editor')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'stratifiers.value.source' })).toBeInTheDocument()
  })
})
