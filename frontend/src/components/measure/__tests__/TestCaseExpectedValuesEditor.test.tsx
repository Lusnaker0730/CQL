import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { fireEvent, render, screen, within } from '../../../test/test-utils'
import TestCaseExpectedValuesEditor from '../TestCaseExpectedValuesEditor'
import { alignExpectedValues } from '../../../utils/testCaseExpectedValues'
import type { MeasureDefinition, TestCaseExpectedValues } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const pops = (...types: string[]) =>
  types.map((populationType) => ({ populationType, criteriaExpression: populationType }))

// Two CV groups (so both have a "measure-population"), the first one stratified.
const cvMeasure: MeasureDefinition = {
  id: 3,
  name: 'los',
  version: '1.0.0',
  scoringType: 'continuous-variable',
  cqlContent: '',
  groupDefinitions: [
    {
      groupId: 'group-1',
      description: 'Inpatient stays',
      populations: pops('initial-population', 'measure-population'),
      observations: [{ criteriaExpression: 'Measure Observation 1', aggregateMethod: 'average', populationRef: 'measure-population' }],
      stratifiers: [{ stratifierId: 'strat-elderly', criteriaExpression: 'Stratifier 1', description: 'Age 65+' }],
    },
    {
      groupId: 'group-2',
      populations: pops('initial-population', 'measure-population'),
      observations: [{ criteriaExpression: 'Measure Observation 2', aggregateMethod: 'sum', populationRef: 'measure-population' }],
    },
  ],
}

function Harness({ onChange, onValidity }: {
  onChange: (v: TestCaseExpectedValues) => void
  onValidity?: (valid: boolean) => void
}) {
  const [value, setValue] = useState(() => alignExpectedValues(cvMeasure, null))
  return (
    <TestCaseExpectedValuesEditor
      measure={cvMeasure}
      value={value}
      onChange={(next) => { setValue(next); onChange(next) }}
      onValidityChange={onValidity}
    />
  )
}

const last = (fn: ReturnType<typeof vi.fn>) => fn.mock.calls.at(-1)![0] as TestCaseExpectedValues

// PAT-228 — per-group expectations: the flat switches could not tell these two groups apart.
describe('TestCaseExpectedValuesEditor', () => {
  it('renders one block per group with that group’s own populations', () => {
    render(<Harness onChange={vi.fn()} />)

    const g1 = within(screen.getByTestId('expected-group-group-1'))
    const g2 = within(screen.getByTestId('expected-group-group-2'))
    expect(g1.getByText('Inpatient stays')).toBeInTheDocument()
    expect(g1.getByRole('switch', { name: 'group-1 measure-population' })).toBeChecked()
    expect(g2.getByRole('switch', { name: 'group-2 measure-population' })).toBeChecked()
    // Only group-1 is stratified.
    expect(g1.getByRole('combobox', { name: 'group-1 stratifier strat-elderly' })).toBeInTheDocument()
    expect(g2.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('changes a population in one group without touching the same-named one in the other', () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)

    fireEvent.click(screen.getByRole('switch', { name: 'group-2 measure-population' }))

    const value = last(onChange)
    expect(value.groups[0].populations!['measure-population']).toBe(1)
    expect(value.groups[1].populations!['measure-population']).toBe(0)
  })

  it('does not assert observations until asked, then parses the list', () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    expect(screen.queryByRole('textbox', { name: 'group-1 observation values' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('switch', { name: 'group-1 assert observations' }))
    expect(last(onChange).groups[0].observations).toEqual([])

    fireEvent.change(screen.getByRole('textbox', { name: 'group-1 observation values' }), {
      target: { value: '30, 45.5' },
    })
    expect(last(onChange).groups[0].observations).toEqual([30, 45.5])
    expect(last(onChange).groups[1].observations).toBeUndefined()

    // Turning it off again removes the assertion instead of expecting "no values".
    fireEvent.click(screen.getByRole('switch', { name: 'group-1 assert observations' }))
    expect(last(onChange).groups[0]).not.toHaveProperty('observations')
  })

  it('flags a value that is not a number and reports the form as invalid', () => {
    const onValidity = vi.fn()
    render(<Harness onChange={vi.fn()} onValidity={onValidity} />)

    fireEvent.click(screen.getByRole('switch', { name: 'group-1 assert observations' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'group-1 observation values' }), {
      target: { value: '30, abc' },
    })

    expect(screen.getByText('testCaseEditor.structured.observationsInvalid')).toBeInTheDocument()
    expect(onValidity).toHaveBeenLastCalledWith(false)

    fireEvent.change(screen.getByRole('textbox', { name: 'group-1 observation values' }), {
      target: { value: '30' },
    })
    expect(onValidity).toHaveBeenLastCalledWith(true)
  })

  it('stores an expected stratum and removes it again when cleared', () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const input = screen.getByRole('combobox', { name: 'group-1 stratifier strat-elderly' })

    fireEvent.change(input, { target: { value: 'true' } })
    expect(last(onChange).groups[0].stratifiers).toEqual({ 'strat-elderly': 'true' })

    fireEvent.change(input, { target: { value: '' } })
    expect(last(onChange).groups[0]).not.toHaveProperty('stratifiers')
  })

  it('explains itself when the measure has no groups', () => {
    render(
      <TestCaseExpectedValuesEditor
        measure={{ ...cvMeasure, groupDefinitions: [] }}
        value={{ groups: [] }}
        onChange={vi.fn()}
      />,
    )
    expect(screen.getByText('testCaseEditor.structured.noGroups')).toBeInTheDocument()
  })
})
