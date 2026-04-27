import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmObservationEditor from '../EcqmObservationEditor'
import type { ObservationEntry } from '../../../types/ecqm'

vi.mock('../EcqmPopulationTreeEditor', () => ({
  default: ({ label }: { label: string }) => <div data-testid="tree-editor">{label}</div>,
}))

const baseObs: ObservationEntry = {
  observationId: 'obs-1',
  criteria: { type: 'and', children: [] } as never,
  aggregateMethod: 'Average',
  populationRef: 'measure-population',
  observationType: 'duration',
  observationUnit: 'days',
  observationProperty: 'period',
}

describe('EcqmObservationEditor — PAT-129 validation', () => {
  it('shows the Percentile value field only when method is Percentile', () => {
    const { rerender } = render(
      <EcqmObservationEditor
        observation={baseObs}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    expect(screen.queryByLabelText(/percentile/i)).toBeNull()

    rerender(
      <EcqmObservationEditor
        observation={{ ...baseObs, aggregateMethod: 'Percentile' }}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    expect(screen.getByLabelText(/percentile/i)).toBeInTheDocument()
  })

  it('flags Percentile field as required when value is missing and out-of-range when invalid', () => {
    const { rerender } = render(
      <EcqmObservationEditor
        observation={{ ...baseObs, aggregateMethod: 'Percentile' }}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    expect(screen.getByText(/percentile value is required/i)).toBeInTheDocument()

    rerender(
      <EcqmObservationEditor
        observation={{ ...baseObs, aggregateMethod: 'Percentile', percentileValue: 150 }}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    )

    expect(screen.getByText(/between 0 and 100/i)).toBeInTheDocument()
  })

  it('flags populationRef as required when empty', () => {
    render(
      <EcqmObservationEditor
        observation={{ ...baseObs, populationRef: '' }}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.getByText(/population reference is required/i)).toBeInTheDocument()
  })

  it('Remove button has an accessible label', () => {
    const onRemove = vi.fn()
    render(
      <EcqmObservationEditor
        observation={baseObs}
        populationBasis="boolean"
        templates={[]}
        modifiers={[]}
        onChange={vi.fn()}
        onRemove={onRemove}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /remove observation/i }))
    expect(onRemove).toHaveBeenCalled()
  })
})
