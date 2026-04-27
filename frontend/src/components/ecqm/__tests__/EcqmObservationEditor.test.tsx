import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import EcqmObservationEditor from '../EcqmObservationEditor'
import type { ObservationEntry } from '../../../types/ecqm'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

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

    // No percentile field for non-Percentile methods (label key is 'observation.percentileValue')
    expect(screen.queryByLabelText('observation.percentileValue')).toBeNull()

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

    expect(screen.getByLabelText('observation.percentileValue')).toBeInTheDocument()
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

    expect(screen.getByText('observation.percentileRequired')).toBeInTheDocument()

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

    expect(screen.getByText('observation.percentileRange')).toBeInTheDocument()
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
    expect(screen.getByText('observation.populationRefRequired')).toBeInTheDocument()
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
    fireEvent.click(screen.getByRole('button', { name: 'observation.remove' }))
    expect(onRemove).toHaveBeenCalled()
  })
})
