import { describe, it, expect, vi } from 'vitest'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// Bare render — see ArithmeticElement.test.tsx for rationale (avoids test-utils
// deep-dep tree). Sub-path icon import in the SUT prevents vitest collection
// hang.
import { render, screen, fireEvent, within } from '@testing-library/react'
import ArithmeticUnaryElement from '../ArithmeticUnaryElement'
import type { BaseElement } from '../../../../types/authoring'

function makeElement(overrides: Partial<BaseElement> = {}): BaseElement {
  return {
    uniqueId: 'unary-1',
    name: 'TestUnary',
    type: 'arithmeticUnary',
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

describe('ArithmeticUnaryElement (PAT-162)', () => {
  it('exposes 6 unary functions alphabetically in the dropdown', () => {
    const el = makeElement()
    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    const fnSelect = screen.getByRole('combobox', { name: 'arithmeticUnary.function' })
    fireEvent.mouseDown(fnSelect)

    const listbox = screen.getByRole('listbox')
    const options = within(listbox).getAllByRole('option')
    const labels = options.map((o) => o.textContent?.trim())
    expect(labels).toEqual(['Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate'])
  })

  it('preview emits `Abs(<literal>)`', () => {
    let el = makeElement()
    el = setField(el, 'function', 'Abs')
    el = setField(el, 'operand_mode', 'literal')
    el = setField(el, 'operand_literal', '-5')

    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/Abs\(-5\)/)).toBeInTheDocument()
  })

  it('preview emits `Floor(<quantity>)` for quantity operand', () => {
    let el = makeElement()
    el = setField(el, 'function', 'Floor')
    el = setField(el, 'operand_mode', 'quantity')
    el = setField(el, 'operand_literal_value', '5.7')
    el = setField(el, 'operand_literal_unit', 'a')

    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/Floor\(5\.7 'a'\)/)).toBeInTheDocument()
  })

  it('preview emits `Round("<elementName>")` for element-ref operand', () => {
    let el = makeElement()
    el = setField(el, 'function', 'Round')
    el = setField(el, 'operand_mode', 'element')
    el = setField(el, 'operand_id', 'egfr-id')

    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[
          { uniqueId: 'egfr-id', name: 'eGFR', returnType: 'decimal' },
        ]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/Round\("eGFR"\)/)).toBeInTheDocument()
  })

  it('Quantity unit with single quote yields no preview', () => {
    let el = makeElement()
    el = setField(el, 'function', 'Abs')
    el = setField(el, 'operand_mode', 'quantity')
    el = setField(el, 'operand_literal_value', '5')
    el = setField(el, 'operand_literal_unit', "mg' OR '1")

    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.queryByText(/arithmeticUnary.cqlPreview/)).not.toBeInTheDocument()
  })

  it('switching to quantity mode reveals value + UCUM unit fields', () => {
    const el = makeElement()
    const onUpdate = vi.fn()
    const { rerender } = render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )

    const quantityButton = screen.getByRole('button', { name: 'arithmetic.modeQuantity' })
    fireEvent.click(quantityButton)

    expect(onUpdate).toHaveBeenCalled()
    const update = onUpdate.mock.calls[0][0] as { fields: BaseElement['fields'] }
    expect(update.fields).toContainEqual(
      expect.objectContaining({ id: 'operand_mode', value: 'quantity' }),
    )

    rerender(
      <ArithmeticUnaryElement
        element={{ ...el, fields: update.fields }}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('arithmetic.quantityValue')).toBeInTheDocument()
    expect(screen.getByLabelText('arithmetic.quantityUnit')).toBeInTheDocument()
  })

  it('invalid function value (e.g. tampered) falls back to Abs in preview', () => {
    let el = makeElement()
    el = setField(el, 'function', 'NotARealFunction')
    el = setField(el, 'operand_mode', 'literal')
    el = setField(el, 'operand_literal', '5')

    render(
      <ArithmeticUnaryElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // Preview MUST NOT show the tampered function name; falls back to Abs.
    expect(screen.getByText(/Abs\(5\)/)).toBeInTheDocument()
    expect(screen.queryByText(/NotARealFunction/)).not.toBeInTheDocument()
  })
})
