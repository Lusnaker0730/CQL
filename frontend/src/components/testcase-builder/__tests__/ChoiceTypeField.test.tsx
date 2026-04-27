import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '../../../i18n'
import ChoiceTypeField from '../ChoiceTypeField'
import type { ElementMetadata } from '../../../types'

// ElementField is mounted recursively for the chosen type — keep tests
// independent of whatever it routes to (Quantity, String, ...).
vi.mock('../ElementField', () => ({
  default: ({ element, value }: { element: ElementMetadata; value: unknown }) => (
    <div data-testid="element-field" data-name={element.name} data-type={element.type}>
      {String(value ?? '')}
    </div>
  ),
}))

function makeChoiceElement(overrides: Partial<ElementMetadata> = {}): ElementMetadata {
  return {
    name: 'value',
    path: 'Observation.value',
    type: '',
    isArray: false,
    isRequired: true,
    min: 0,
    max: '1',
    isChoiceType: true,
    choiceTypes: ['Quantity', 'CodeableConcept', 'string'],
    bindingStrength: null,
    bindingValueSetUrl: null,
    bindingCodeSystemUrl: null,
    boundCodes: [],
    children: [],
    description: null,
    referenceTargets: [],
    ...overrides,
  }
}

function renderField(props: Partial<React.ComponentProps<typeof ChoiceTypeField>> = {}) {
  return render(
    <I18nextProvider i18n={i18n}>
      <ChoiceTypeField
        element={makeChoiceElement()}
        value={undefined}
        onChange={vi.fn()}
        depth={0}
        {...props}
      />
    </I18nextProvider>,
  )
}

describe('ChoiceTypeField', () => {
  it('renders the type Select and the inner ElementField with the first choice as default', () => {
    renderField()
    expect(screen.getByRole('combobox')).toBeInTheDocument()
    const inner = screen.getByTestId('element-field')
    // syntheticElement.name === buildChoiceFieldName('value', 'Quantity')
    expect(inner.dataset.name).toBe('valueQuantity')
    expect(inner.dataset.type).toBe('Quantity')
  })

  it('respects initialChoiceType prop', () => {
    renderField({ initialChoiceType: 'CodeableConcept' })
    expect(screen.getByTestId('element-field').dataset.type).toBe('CodeableConcept')
  })

  it('shows a warning Alert (and no Select) when choiceTypes is empty', () => {
    renderField({ element: makeChoiceElement({ choiceTypes: [] }) })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).toBeNull()
    expect(screen.queryByTestId('element-field')).toBeNull()
  })

  it('treats undefined choiceTypes the same as an empty list', () => {
    // Defensive: metadata may omit choiceTypes for malformed StructureDefinitions.
    renderField({
      element: makeChoiceElement({ choiceTypes: undefined as unknown as string[] }),
    })
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('clears the value when the user picks a different variant', () => {
    const onChange = vi.fn()
    renderField({ onChange })
    // Open the MUI Select dropdown
    fireEvent.mouseDown(screen.getByRole('combobox'))
    // Pick a different option
    const opt = screen.getByRole('option', { name: 'CodeableConcept' })
    fireEvent.click(opt)
    // handleTypeChange clears the value
    expect(onChange).toHaveBeenCalledWith(undefined)
  })
})
