import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '../../../../test/test-utils'
import FunctionArgumentsEditor from '../FunctionArgumentsEditor'
import { ArtifactScopeProvider } from '../../../../contexts/ArtifactScopeContext'
import type { FunctionArgument } from '../../../../types/authoring'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// PAT-237 — one row per declared operand; each edit reports the whole argument list; problems
// the backend would refuse are shown inline; the Measurement Period source exists only in eCQM.
describe('FunctionArgumentsEditor', () => {
  const scope = {
    baseElements: [{ uniqueId: 'be-1', name: 'HbA1c Results', returnType: 'list_of_observations' }],
    parameters: [{ uniqueId: 'p-1', name: 'Threshold', type: 'Decimal' }],
  }
  const args: FunctionArgument[] = [
    { name: 'observations', type: 'List<FHIR.Observation>', mode: 'element' },
    { name: 'threshold', type: 'Decimal', mode: 'literal', literal_type: 'Decimal', literal_value: '7.0' },
  ]

  function renderEditor(value: FunctionArgument[], onChange = vi.fn(), hasMeasurementPeriod = true) {
    render(
      <ArtifactScopeProvider baseElements={scope.baseElements} parameters={scope.parameters} hasMeasurementPeriod={hasMeasurementPeriod}>
        <FunctionArgumentsEditor value={value} onChange={onChange} />
      </ArtifactScopeProvider>
    )
    return onChange
  }

  it('renders one row per argument with its declared type, and flags an unpicked reference', () => {
    renderEditor(args)

    expect(screen.getByText('observations')).toBeInTheDocument()
    expect(screen.getByText('List<FHIR.Observation>')).toBeInTheDocument()
    expect(screen.getByText('threshold')).toBeInTheDocument()
    expect(screen.getByLabelText('threshold value')).toHaveValue('7.0')
    // the element argument has no operand_id yet
    expect(screen.getByRole('alert')).toHaveTextContent('functionCall.errors.missingReference')
  })

  it('picking a base element reports the updated argument list', () => {
    const onChange = renderEditor(args)

    fireEvent.mouseDown(screen.getByLabelText('observations base element').closest('[role="combobox"]') ?? screen.getAllByRole('combobox')[1])
    const listbox = within(screen.getByRole('listbox'))
    fireEvent.click(listbox.getByText(/HbA1c Results/))

    expect(onChange).toHaveBeenLastCalledWith([
      { ...args[0], operand_id: 'be-1' },
      args[1],
    ])
  })

  it('editing a literal reports the value; an invalid value is flagged', () => {
    const onChange = renderEditor(args)
    fireEvent.change(screen.getByLabelText('threshold value'), { target: { value: '7.5' } })
    expect(onChange).toHaveBeenLastCalledWith([args[0], { ...args[1], literal_value: '7.5' }])

    render(
      <ArtifactScopeProvider baseElements={[]} parameters={[]} hasMeasurementPeriod={false}>
        <FunctionArgumentsEditor
          value={[{ name: 'n', type: 'Integer', mode: 'literal', literal_type: 'Integer', literal_value: '3; drop' }]}
          onChange={vi.fn()}
        />
      </ArtifactScopeProvider>
    )
    expect(screen.getByText('functionCall.errors.invalidLiteral')).toBeInTheDocument()
  })

  it('offers the Measurement Period source only when the artifact has one', () => {
    const { unmount } = render(
      <ArtifactScopeProvider baseElements={[]} parameters={[]} hasMeasurementPeriod>
        <FunctionArgumentsEditor value={[{ name: 'period', type: 'Interval<DateTime>', mode: 'element' }]} onChange={vi.fn()} />
      </ArtifactScopeProvider>
    )
    fireEvent.mouseDown(screen.getAllByRole('combobox')[0])
    expect(within(screen.getByRole('listbox')).getByText('functionCall.modes.measurementPeriod')).toBeInTheDocument()
    fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' })
    unmount()

    render(
      <ArtifactScopeProvider baseElements={[]} parameters={[]} hasMeasurementPeriod={false}>
        <FunctionArgumentsEditor value={[{ name: 'period', type: 'Interval<DateTime>', mode: 'measurementPeriod' }]} onChange={vi.fn()} />
      </ArtifactScopeProvider>
    )
    expect(screen.getByRole('alert')).toHaveTextContent('functionCall.errors.noMeasurementPeriod')
    fireEvent.mouseDown(screen.getAllByRole('combobox')[0])
    expect(within(screen.getByRole('listbox')).queryByText('functionCall.modes.measurementPeriod')).not.toBeInTheDocument()
  })

  it('a function without operands says so', () => {
    render(<FunctionArgumentsEditor value={[]} onChange={vi.fn()} />)
    expect(screen.getByText('functionCall.noArguments')).toBeInTheDocument()
  })
})
