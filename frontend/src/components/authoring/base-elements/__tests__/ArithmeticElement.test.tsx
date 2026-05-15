import { describe, it, expect, vi } from 'vitest'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      // Trivial interpolation so operandLabel etc. expand for assertions.
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// Bare render -- see PR #501 / ArithmeticUnaryElement.test for rationale.
// Sub-path icon imports in the SUT prevent the vitest collection hang.
import { render, screen, fireEvent, within } from '@testing-library/react'
import ArithmeticElement from '../ArithmeticElement'
import type { BaseElement, ElementField } from '../../../../types/authoring'
import type { NaryOperand } from '../arithmeticEmit'

function makeNaryElement(
  operands: NaryOperand[],
  operators: string[],
  overrides: Partial<BaseElement> = {},
): BaseElement {
  const fields: ElementField[] = [
    { id: 'operands', type: 'json', name: 'operands', value: operands as unknown as string },
    { id: 'operators', type: 'json', name: 'operators', value: operators as unknown as string },
  ]
  return {
    uniqueId: 'arith-1',
    name: 'TestCalc',
    type: 'arithmeticExpression',
    returnType: 'decimal',
    fields,
    ...overrides,
  }
}

function makeLegacyElement(fieldValues: Record<string, string>): BaseElement {
  const fields: ElementField[] = Object.entries(fieldValues).map(([id, value]) => ({
    id, type: 'string', name: id, value,
  }))
  return {
    uniqueId: 'arith-legacy',
    name: 'LegacyCalc',
    type: 'arithmeticExpression',
    returnType: 'decimal',
    fields,
  }
}

describe('ArithmeticElement (PAT-163 N-ary)', () => {
  // ===== Default 2-operand shape =====

  it('renders 2 operand rows by default', () => {
    const el = makeNaryElement(
      [{ mode: 'element' }, { mode: 'element' }],
      ['+'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    // Two operand mode-toggle groups, one for each row
    const elementToggles = screen.getAllByRole('button', { name: 'arithmetic.modeElement' })
    expect(elementToggles).toHaveLength(2)
  })

  it('renders + Add operand button enabled with 2 operands', () => {
    const el = makeNaryElement(
      [{ mode: 'element' }, { mode: 'element' }],
      ['+'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const addBtn = screen.getByRole('button', { name: /arithmetic.addOperand/ })
    expect(addBtn).toBeEnabled()
  })

  // ===== Add / remove =====

  it('clicking Add operand calls onUpdate with an extra operand + default + operator', () => {
    const el = makeNaryElement(
      [{ mode: 'element' }, { mode: 'element' }],
      ['+'],
    )
    const onUpdate = vi.fn()
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /arithmetic.addOperand/ }))
    expect(onUpdate).toHaveBeenCalled()
    const update = onUpdate.mock.calls[0][0] as { fields: ElementField[] }
    const operandsField = update.fields.find((f) => f.id === 'operands')
    const operatorsField = update.fields.find((f) => f.id === 'operators')
    expect(((operandsField?.value) as unknown as unknown[]).length).toBe(3)
    expect(((operatorsField?.value) as unknown as unknown[]).length).toBe(2)
    expect(((operatorsField?.value) as unknown as string[])[1]).toBe('+')
  })

  it('Remove operand button is disabled at min (2 operands)', () => {
    const el = makeNaryElement(
      [{ mode: 'element' }, { mode: 'element' }],
      ['+'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const removeBtns = screen.getAllByRole('button', { name: 'arithmetic.removeOperand' })
    expect(removeBtns).toHaveLength(2)
    removeBtns.forEach((btn) => expect(btn).toBeDisabled())
  })

  it('Remove operand is enabled with 3+ operands and removes the right one', () => {
    const el = makeNaryElement(
      [{ mode: 'element' }, { mode: 'literal' }, { mode: 'element' }],
      ['+', '*'],
    )
    const onUpdate = vi.fn()
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )
    const removeBtns = screen.getAllByRole('button', { name: 'arithmetic.removeOperand' })
    expect(removeBtns).toHaveLength(3)
    removeBtns.forEach((btn) => expect(btn).toBeEnabled())
    // Remove the middle operand
    fireEvent.click(removeBtns[1])
    const update = onUpdate.mock.calls[0][0] as { fields: ElementField[] }
    const ops = update.fields.find((f) => f.id === 'operands')!.value as unknown as NaryOperand[]
    const opsList = update.fields.find((f) => f.id === 'operators')!.value as unknown as string[]
    expect(ops.length).toBe(2)
    expect(opsList.length).toBe(1)
    // Removed middle (the literal); only element-mode operands remain
    expect(ops.every((o) => o.mode === 'element')).toBe(true)
  })

  // ===== Precedence preview =====

  it('preview shows parens for higher-precedence subexpression: a + b * c', () => {
    const el = makeNaryElement(
      [{ mode: 'literal', operand_literal: '1' },
       { mode: 'literal', operand_literal: '2' },
       { mode: 'literal', operand_literal: '3' }],
      ['+', '*'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    expect(screen.getByText(/1 \+ \(2 \* 3\)/)).toBeInTheDocument()
  })

  it('preview shows parens for descending precedence: a * b + c -> (a * b) + c', () => {
    const el = makeNaryElement(
      [{ mode: 'literal', operand_literal: '1' },
       { mode: 'literal', operand_literal: '2' },
       { mode: 'literal', operand_literal: '3' }],
      ['*', '+'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    expect(screen.getByText(/\(1 \* 2\) \+ 3/)).toBeInTheDocument()
  })

  it('preview emits element-ref operand with escaped identifier', () => {
    const el = makeNaryElement(
      [{ mode: 'element', operand_id: 'weight-id' },
       { mode: 'literal', operand_literal: '2.2' }],
      ['*'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[{ uniqueId: 'weight-id', name: 'Weight', returnType: 'decimal' }]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    expect(screen.getByText(/"Weight" \* 2\.2/)).toBeInTheDocument()
  })

  it('preview emits quantity operand correctly', () => {
    const el = makeNaryElement(
      [{ mode: 'literal', operand_literal: '50' },
       { mode: 'quantity', operand_literal_value: '5', operand_literal_unit: 'mg/dL' }],
      ['-'],
    )
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

  // ===== Injection defense =====

  it('Quantity unit with single quote yields no CQL preview', () => {
    const el = makeNaryElement(
      [{ mode: 'literal', operand_literal: '1' },
       { mode: 'quantity', operand_literal_value: '5', operand_literal_unit: "mg' OR '1" }],
      ['+'],
    )
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

  // ===== Backward-compat with legacy 2-ary shape =====

  it('renders legacy 2-ary fields via in-memory N-ary conversion', () => {
    const el = makeLegacyElement({
      left_mode: 'literal', left_literal: '5',
      operator: '+',
      right_mode: 'literal', right_literal: '3',
    })
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    // Legacy 2-ary gets read as N=2 N-ary; preview should render
    expect(screen.getByText(/5 \+ 3/)).toBeInTheDocument()
  })

  it('first edit on a legacy 2-ary element migrates to N-ary on save', () => {
    const el = makeLegacyElement({
      left_mode: 'literal', left_literal: '5',
      operator: '+',
      right_mode: 'literal', right_literal: '3',
    })
    const onUpdate = vi.fn()
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={onUpdate}
        onDelete={vi.fn()}
      />,
    )
    // Add an operand to trigger a write
    fireEvent.click(screen.getByRole('button', { name: /arithmetic.addOperand/ }))
    const update = onUpdate.mock.calls[0][0] as { fields: ElementField[] }
    // Saved shape: only operands + operators fields, no leftover left_*/right_*
    const fieldIds = update.fields.map((f) => f.id)
    expect(fieldIds).toEqual(expect.arrayContaining(['operands', 'operators']))
    expect(fieldIds).not.toContain('left_mode')
    expect(fieldIds).not.toContain('right_literal')
    expect(fieldIds).not.toContain('operator')
  })

  // ===== Operator dropdown still has all 7 =====

  it('operator dropdown exposes all 7 operators', () => {
    const el = makeNaryElement(
      [{ mode: 'literal', operand_literal: '1' },
       { mode: 'literal', operand_literal: '2' }],
      ['+'],
    )
    render(
      <ArithmeticElement
        element={el}
        availableOperands={[]}
        onUpdate={vi.fn()}
        onDelete={vi.fn()}
      />,
    )
    const operatorSelect = screen.getByRole('combobox', { name: 'arithmetic.operator' })
    fireEvent.mouseDown(operatorSelect)
    const listbox = screen.getByRole('listbox')
    const labels = within(listbox).getAllByRole('option').map((o) => o.textContent?.trim())
    expect(labels).toEqual(['+', '−', '×', '÷', 'mod', 'div', '^'])
  })
})
