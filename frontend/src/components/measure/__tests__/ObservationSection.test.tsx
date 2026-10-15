import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen, within } from '../../../test/test-utils'
import ObservationSection from '../ObservationSection'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { changeLanguage: vi.fn() } }),
}))

// PAT-264 — the measure page stores the backend's canonical method KEY (it used to store the
// translated label, which the backend's pattern refused), and the percentile method asks for its
// percentile.
describe('ObservationSection', () => {
  it('stores the method key, not its label, and shows the percentile field for the percentile method', () => {
    const onChange = vi.fn()
    render(
      <ObservationSection
        observations={[{ criteriaExpression: 'Measure Observation', aggregateMethod: 'median', populationRef: 'measure-population' }]}
        onChange={onChange}
        expressionNames={['Measure Observation']}
        populationTypes={['measure-population']}
      />,
    )

    const methodInput = screen.getByLabelText('observations.fields.aggregateMethod')
    expect(methodInput).toHaveValue('observations.aggregateMethods.median')
    expect(screen.queryByTestId('observation-percentile')).not.toBeInTheDocument()

    fireEvent.mouseDown(methodInput)
    fireEvent.click(within(screen.getByRole('listbox')).getByText('observations.aggregateMethods.percentile'))
    expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ aggregateMethod: 'percentile' })])
    expect(onChange.mock.calls.at(-1)![0][0].percentileValue).toBeUndefined()
  })

  it('edits the percentile and drops it when another method is chosen', () => {
    const onChange = vi.fn()
    render(
      <ObservationSection
        observations={[{ criteriaExpression: 'Measure Observation', aggregateMethod: 'percentile', percentileValue: 90, populationRef: 'measure-population' }]}
        onChange={onChange}
        expressionNames={['Measure Observation']}
        populationTypes={['measure-population']}
      />,
    )

    const percentile = screen.getByTestId('observation-percentile')
    expect(percentile).toHaveValue(90)
    fireEvent.change(percentile, { target: { value: '75' } })
    expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ aggregateMethod: 'percentile', percentileValue: 75 })])

    fireEvent.mouseDown(screen.getByLabelText('observations.fields.aggregateMethod'))
    fireEvent.click(within(screen.getByRole('listbox')).getByText('observations.aggregateMethods.average'))
    expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ aggregateMethod: 'average' })])
    expect(onChange.mock.calls.at(-1)![0][0].percentileValue).toBeUndefined()
  })
})
