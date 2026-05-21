import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../../i18n'
import type { ElementMetadata, ResourceElementMetadata } from '../../../types'

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

// Stub constants module too — both paths point at the same file but vi.mock
// resolves by string, so we register both ways the SUT might import it.
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

// Mock the API layer so useFhirMetadata returns a deterministic StructureDefinition.
vi.mock('../../../api', () => ({
  fhirApi: {
    getResourceMetadata: vi.fn(),
    getResourceTypes: vi.fn(),
  },
}))

// Stub the form header (uses no icons but pulls i18n + Chip; not under test here).
vi.mock('../ResourceFormHeader', () => ({
  default: ({ resourceType, resourceId }: { resourceType: string; resourceId: string }) => (
    <div data-testid="form-header">{resourceType}/{resourceId}</div>
  ),
}))

import ResourceForm from '../ResourceForm'
import {
  BundleBuilderProvider,
  useBundleBuilder,
} from '../../../contexts/BundleBuilderContext'

import { fhirApi } from '../../../api'

// Stub field components — we only care about *which* keys ResourceForm writes
// into resourceData when handleFieldChange runs, not the visual rendering.
vi.mock('../ElementField', () => ({
  default: ({
    element,
    value,
    onChange,
    initialChoiceType,
  }: {
    element: ElementMetadata
    value: unknown
    onChange: (v: unknown, choiceFieldName?: string) => void
    initialChoiceType?: string
  }) => (
    <div data-testid={`field-${element.name}`}>
      <span data-testid={`value-${element.name}`}>
        {value === undefined ? '∅' : JSON.stringify(value)}
      </span>
      <span data-testid={`choice-${element.name}`}>{initialChoiceType ?? ''}</span>
      {element.isChoiceType && element.choiceTypes?.map((ct) => (
        <button
          key={ct}
          type="button"
          data-testid={`set-${element.name}-${ct}`}
          onClick={() =>
            onChange(
              { synthetic: ct },
              element.name + ct.charAt(0).toUpperCase() + ct.slice(1),
            )
          }
        >
          set {ct}
        </button>
      ))}
      {!element.isChoiceType && (
        <button
          type="button"
          data-testid={`clear-${element.name}`}
          onClick={() => onChange(undefined)}
        >
          clear
        </button>
      )}
    </div>
  ),
}))

function el(overrides: Partial<ElementMetadata>): ElementMetadata {
  return {
    name: 'x',
    path: 'Observation.x',
    type: 'string',
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

const observationMetadata: ResourceElementMetadata = {
  resourceType: 'Observation',
  elements: [
    el({ name: 'status', type: 'code', isRequired: true }),
    el({
      name: 'value',
      path: 'Observation.value',
      isChoiceType: true,
      choiceTypes: ['Quantity', 'CodeableConcept', 'string', 'boolean'],
    }),
    // Sibling whose name shares the prefix "value" but is NOT a choice variant.
    // The previous prefix+caps cleanup would have nuked this on every choice swap.
    el({ name: 'valueSet', type: 'string' }),
    el({ name: 'note', type: 'string' }),
  ],
}

function Harness({
  initialEntry,
  onState,
}: {
  initialEntry: { id: string; resourceType: string; resourceData: Record<string, unknown> }
  onState: (data: Record<string, unknown>) => void
}) {
  const { state, dispatch } = useBundleBuilder()
  // Seed the bundle once on mount.
  if (state.entries.length === 0) {
    dispatch({ type: 'ADD_ENTRY', payload: initialEntry })
  }
  const active = state.entries.find((e) => e.id === state.activeEntryId)
  if (active) onState(active.resourceData)
  return <ResourceForm onDirty={() => {}} />
}

function renderForm(initialData: Record<string, unknown>) {
  const states: Record<string, unknown>[] = []
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const result = render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <BundleBuilderProvider>
          <Harness
            initialEntry={{
              id: 'obs-1',
              resourceType: 'Observation',
              resourceData: { id: 'obs-1', ...initialData },
            }}
            onState={(d) => states.push(d)}
          />
        </BundleBuilderProvider>
      </QueryClientProvider>
    </I18nextProvider>,
  )
  return { ...result, states, latest: () => states[states.length - 1] }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(fhirApi.getResourceMetadata).mockResolvedValue(observationMetadata)
})

describe('ResourceForm — choice-type cleanup (high-severity regression)', () => {
  it('replaces only the previous choice variant when the user swaps types', async () => {
    const { findByTestId, latest } = renderForm({
      valueQuantity: { value: 5, unit: 'mg' },
      valueSet: 'http://example.org/vs',
      note: 'unchanged',
    })

    // Wait for metadata + form to render.
    const swapButton = await findByTestId('set-value-CodeableConcept')

    act(() => {
      fireEvent.click(swapButton)
    })

    const data = latest()
    // Old variant gone…
    expect(data.valueQuantity).toBeUndefined()
    // …new variant present…
    expect(data.valueCodeableConcept).toEqual({ synthetic: 'CodeableConcept' })
    // …and crucially: the prefix-sharing sibling is intact.
    expect(data.valueSet).toBe('http://example.org/vs')
    expect(data.note).toBe('unchanged')
  })

  it('does not delete the bare base name when there was no value before', async () => {
    const { findByTestId, latest } = renderForm({})
    const swapButton = await findByTestId('set-value-string')

    act(() => fireEvent.click(swapButton))

    const data = latest()
    expect(data.valueString).toEqual({ synthetic: 'string' })
    expect(data.value).toBeUndefined()
  })

  it('detects the active variant and forwards it as initialChoiceType', async () => {
    const { findByTestId } = renderForm({
      valueCodeableConcept: { coding: [{ code: 'x' }] },
    })
    // After metadata loads, the value field should report the detected variant.
    expect(await findByTestId('choice-value')).toHaveTextContent('CodeableConcept')
  })

  it('removes the field entirely when the user clears a non-choice element', async () => {
    const { findByTestId, latest } = renderForm({ note: 'will be deleted' })
    const clearButton = await findByTestId('clear-note')

    act(() => fireEvent.click(clearButton))

    expect(latest().note).toBeUndefined()
  })

  it('shows an error Alert when metadata loading fails', async () => {
    vi.mocked(fhirApi.getResourceMetadata).mockRejectedValueOnce(new Error('boom'))
    renderForm({})
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
