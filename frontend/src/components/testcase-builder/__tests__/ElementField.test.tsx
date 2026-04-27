import { describe, it, expect, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '../../../i18n'
import type { ElementMetadata } from '../../../types'

// Stub Monaco — we want to drive its onChange directly without loading the real
// editor, which is heavy and not useful inside JSDOM.
const monacoOnChangeRef: { current: ((v: string | undefined) => void) | null } = { current: null }

vi.mock('@monaco-editor/react', () => ({
  default: ({ onChange, value }: { onChange?: (v: string | undefined) => void; value?: string }) => {
    monacoOnChangeRef.current = onChange ?? null
    return <div data-testid="monaco-stub" data-value={value ?? ''} />
  },
}))

// Stub @mui/icons-material via a Proxy: every named import becomes a no-op.
// Avoids resolving the barrel's thousands of icon files at collection (Windows EMFILE).
vi.mock('@mui/icons-material', () => {
  const Stub = () => null
  return new Proxy(
    { default: Stub },
    { get: (_t, prop) => (prop === '__esModule' ? true : Stub) },
  )
})

// Stub constants module so vite doesn't reach the icon set at all.
vi.mock('../constants', () => ({
  RESOURCE_ICONS: {} as Record<string, React.ReactElement>,
  DEFAULT_RESOURCE_ICON: null,
  getResourceIcon: () => null,
  FHIR_UCUM_SYSTEM: 'http://unitsofmeasure.org',
  FHIR_BUNDLE_TYPE: 'collection',
  getChildBoundCodes: (
    _el: unknown,
    _name: string,
    fallback: string[],
  ) => fallback,
  NAME_USE_CODES: ['usual'],
  ADDRESS_USE_CODES: ['home'],
  ADDRESS_TYPE_CODES: ['postal'],
  CONTACT_SYSTEM_CODES: ['phone'],
  CONTACT_USE_CODES: ['home'],
  IDENTIFIER_USE_CODES: ['usual'],
}))

// Stub each non-essential field — ElementField's depth-fallback path is
// the only one under test, so we avoid pulling in heavy children.
vi.mock('../CodeField', () => ({ default: () => <div data-testid="stub-code" /> }))
vi.mock('../CodeableConceptField', () => ({ default: () => <div data-testid="stub-cc" /> }))
vi.mock('../PeriodField', () => ({ default: () => <div data-testid="stub-period" /> }))
vi.mock('../QuantityField', () => ({ default: () => <div data-testid="stub-quantity" /> }))
vi.mock('../HumanNameField', () => ({ default: () => <div data-testid="stub-name" /> }))
vi.mock('../AddressField', () => ({ default: () => <div data-testid="stub-address" /> }))
vi.mock('../ContactPointField', () => ({ default: () => <div data-testid="stub-contact" /> }))
vi.mock('../ReferenceField', () => ({ default: () => <div data-testid="stub-ref" /> }))
vi.mock('../IdentifierField', () => ({ default: () => <div data-testid="stub-id" /> }))
vi.mock('../ChoiceTypeField', () => ({ default: () => <div data-testid="stub-choice" /> }))
vi.mock('../GenericComplexField', () => ({ default: () => <div data-testid="stub-generic" /> }))
vi.mock('../PrimitiveField', () => ({ default: () => <div data-testid="stub-primitive" /> }))
vi.mock('../ArrayFieldWrapper', () => ({ default: () => <div data-testid="stub-array" /> }))

// Import after mocks so the real ElementField sees the stubbed children.
import ElementField from '../ElementField'

function makeElement(overrides: Partial<ElementMetadata> = {}): ElementMetadata {
  return {
    name: 'note',
    path: 'X.note',
    type: 'Annotation',
    isArray: false,
    isRequired: false,
    min: 0,
    max: '1',
    isChoiceType: false,
    choiceTypes: [],
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

function renderField(
  overrides: Partial<React.ComponentProps<typeof ElementField>> = {},
) {
  return render(
    <I18nextProvider i18n={i18n}>
      <ElementField
        element={makeElement()}
        path="Patient.note"
        value={undefined}
        onChange={vi.fn()}
        depth={3}
        {...overrides}
      />
    </I18nextProvider>,
  )
}

describe('ElementField — depth fallback (Monaco JSON editor)', () => {
  it('renders the Monaco JSON editor at depth >= 3', async () => {
    renderField({ depth: 3 })
    // Monaco is lazy-loaded behind Suspense; flush the microtask queue.
    await screen.findByTestId('monaco-stub')
    expect(screen.getByTestId('monaco-stub')).toBeInTheDocument()
  })

  it('forwards parsed JSON to onChange and clears the error alert', async () => {
    const onChange = vi.fn()
    renderField({ onChange, depth: 3 })
    await screen.findByTestId('monaco-stub')

    act(() => monacoOnChangeRef.current?.('{"text": "hello"}'))
    expect(onChange).toHaveBeenCalledWith({ text: 'hello' })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('treats an empty editor as undefined (clears the field)', async () => {
    const onChange = vi.fn()
    renderField({ onChange, depth: 3 })
    await screen.findByTestId('monaco-stub')

    act(() => monacoOnChangeRef.current?.(''))
    expect(onChange).toHaveBeenCalledWith(undefined)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('surfaces a parse-error Alert for invalid JSON instead of silently swallowing edits', async () => {
    const onChange = vi.fn()
    renderField({ onChange, depth: 3 })
    await screen.findByTestId('monaco-stub')

    act(() => monacoOnChangeRef.current?.('{not valid'))
    expect(onChange).not.toHaveBeenCalled()
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
    expect(alert.textContent).toMatch(/JSON|parse/i)
  })

  it('clears the parse-error Alert once the user fixes the JSON', async () => {
    const onChange = vi.fn()
    renderField({ onChange, depth: 3 })
    await screen.findByTestId('monaco-stub')

    act(() => monacoOnChangeRef.current?.('{not valid'))
    expect(screen.getByRole('alert')).toBeInTheDocument()

    act(() => monacoOnChangeRef.current?.('{"ok": true}'))
    expect(onChange).toHaveBeenLastCalledWith({ ok: true })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('does not render the Monaco fallback at depth < 3 (delegates to a real field)', () => {
    renderField({ depth: 2 })
    expect(screen.queryByTestId('monaco-stub')).toBeNull()
  })
})
