import { describe, it, expect, vi } from 'vitest'

// Stub @mui/icons-material via a Proxy: every named import becomes a no-op
// component. Avoids resolving the barrel's thousands of icon files during
// test collection (Windows EMFILE).
vi.mock('@mui/icons-material', () => {
  const Stub = () => null
  return new Proxy(
    { default: Stub },
    { get: (_t, prop) => (prop === '__esModule' ? true : Stub) },
  )
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// Render bare (no test-utils) — ArithmeticElement only depends on MUI defaults
// + the i18n mock above. Pulling in test-utils.tsx drags Redux/Theme/Context
// providers and their deep dep trees, which makes vitest collection hang on
// Windows.
import { render, screen, fireEvent, within } from '@testing-library/react'
import ArithmeticElement from '../ArithmeticElement'
import type { BaseElement } from '../../../../types/authoring'

function makeElement(overrides: Partial<BaseElement> = {}): BaseElement {
  return {
    uniqueId: 'arith-1',
    name: 'TestCalc',
    type: 'arithmeticExpression',
    returnType: 'decimal',
    fields: [],
    ...overrides,
  }
}

function setField(el: BaseElement, fieldId: string, value: string): BaseElement {
  const fields = [...(el.fields ?? [])]
  const idx = fields.findIndex((f) => f.id === fieldId)
  if (idx >= 0) fields[idx] = { ...fields[idx], value }
  else fields.push({ id: fieldId, type: 'string', name: fieldId, value })
  return { ...el, fields }
}

describe('ArithmeticElement (PAT-161)', () => {
  // ===== Operator dropdown =====

  it('exposes all 7 operators in the dropdown', () => {
    const el = makeElement()
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // Open the operator select (MUI Select renders a button with role "combobox")
    const operatorSelect = screen.getByRole('combobox', { name: 'arithmetic.operator' })
    fireEvent.mouseDown(operatorSelect)

    const listbox = screen.getByRole('listbox')
    const options = within(listbox).getAllByRole('option')
    const labels = options.map((o) => o.textContent?.trim())
    // Display labels mirror the OPERATORS const; symbol operators get unicode
    // display chars to be readable in dense rows.
    expect(labels).toEqual(['+', '−', '×', '÷', 'mod', 'div', '^'])
  })

  // ===== Preview emits new operators =====

  it('preview emits `mod` keyword when mod operator is selected', () => {
    let el = makeElement()
    el = setField(el, 'operator', 'mod')
    el = setField(el, 'left_mode', 'literal')
    el = setField(el, 'left_literal', '5')
    el = setField(el, 'right_mode', 'literal')
    el = setField(el, 'right_literal', '2')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/5 mod 2/)).toBeInTheDocument()
  })

  it('preview emits `^` when power operator is selected', () => {
    let el = makeElement()
    el = setField(el, 'operator', '^')
    el = setField(el, 'left_mode', 'literal')
    el = setField(el, 'left_literal', '2')
    el = setField(el, 'right_mode', 'literal')
    el = setField(el, 'right_literal', '3')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/2 \^ 3/)).toBeInTheDocument()
  })

  // ===== Quantity operand mode =====

  it('exposes a "Quantity" toggle in the operand mode group', () => {
    const el = makeElement()
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // ToggleButton labels come straight from the i18n keys via our mock
    const quantityButtons = screen.getAllByRole('button', { name: 'arithmetic.modeQuantity' })
    // One per operand (left + right)
    expect(quantityButtons).toHaveLength(2)
  })

  it('switching left operand to quantity reveals value + UCUM unit fields', () => {
    const el = makeElement()
    const onUpdate = vi.fn()
    const { rerender } = render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )

    // Click the LEFT operand's "Quantity" toggle (first occurrence in the DOM)
    const quantityButtons = screen.getAllByRole('button', { name: 'arithmetic.modeQuantity' })
    fireEvent.click(quantityButtons[0])

    // onUpdate must persist mode change to fields
    expect(onUpdate).toHaveBeenCalled()
    const update = onUpdate.mock.calls[0][0] as { fields: BaseElement['fields'] }
    expect(update.fields).toContainEqual(
      expect.objectContaining({ id: 'left_mode', value: 'quantity' }),
    )

    // Apply the update and re-render — quantity inputs should now exist.
    rerender(
      <ArithmeticElement
        element={{ ...el, fields: update.fields }}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )

    // Value input is labeled with quantityValue key; UCUM field uses quantityUnit
    expect(screen.getByLabelText('arithmetic.quantityValue')).toBeInTheDocument()
    expect(screen.getByLabelText('arithmetic.quantityUnit')).toBeInTheDocument()
  })

  it('preview emits `<value> \'<unit>\'` for a well-formed Quantity operand', () => {
    let el = makeElement()
    el = setField(el, 'operator', '-')
    el = setField(el, 'left_mode', 'literal')
    el = setField(el, 'left_literal', '50')
    el = setField(el, 'right_mode', 'quantity')
    el = setField(el, 'right_literal_value', '5')
    el = setField(el, 'right_literal_unit', 'mg/dL')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/50 - 5 'mg\/dL'/)).toBeInTheDocument()
  })

  it('Quantity unit containing a single quote yields no CQL preview', () => {
    let el = makeElement()
    el = setField(el, 'operator', '+')
    el = setField(el, 'left_mode', 'literal')
    el = setField(el, 'left_literal', '1')
    el = setField(el, 'right_mode', 'quantity')
    el = setField(el, 'right_literal_value', '5')
    // Injection attempt — UCUM_UNIT_RE rejects single quote
    el = setField(el, 'right_literal_unit', "mg' OR '1")

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // No preview box should appear at all when an operand is unresolvable
    expect(screen.queryByText(/arithmetic.cqlPreview/)).not.toBeInTheDocument()
  })

  it('Quantity unit containing whitespace yields no CQL preview', () => {
    let el = makeElement()
    el = setField(el, 'operator', '+')
    el = setField(el, 'left_mode', 'quantity')
    el = setField(el, 'left_literal_value', '5')
    el = setField(el, 'left_literal_unit', 'mg dL') // space → invalid
    el = setField(el, 'right_mode', 'literal')
    el = setField(el, 'right_literal', '1')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.queryByText(/arithmetic.cqlPreview/)).not.toBeInTheDocument()
  })

  // ===== Regression: existing modes still work =====

  it('regression: literal + element mode combination renders preview unchanged', () => {
    let el = makeElement()
    el = setField(el, 'operator', '*')
    el = setField(el, 'left_mode', 'element')
    el = setField(el, 'left_operand_id', 'weight-id')
    el = setField(el, 'right_mode', 'literal')
    el = setField(el, 'right_literal', '2.2')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[
          { uniqueId: 'weight-id', name: 'Weight', returnType: 'decimal' },
        ]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/"Weight" \* 2\.2/)).toBeInTheDocument()
  })

  it('regression: legacy artifact without mode fields defaults to element mode', () => {
    let el = makeElement()
    el = setField(el, 'operator', '+')
    // No left_mode / right_mode — should silently default to "element"
    el = setField(el, 'left_operand_id', 'weight-id')
    el = setField(el, 'right_operand_id', 'weight-id')

    render(
      <ArithmeticElement
        element={el}
        availableOperands={[
          { uniqueId: 'weight-id', name: 'Weight', returnType: 'decimal' },
        ]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/"Weight" \+ "Weight"/)).toBeInTheDocument()
  })
})
