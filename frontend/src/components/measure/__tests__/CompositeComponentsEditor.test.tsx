import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '../../../test/test-utils'
import CompositeComponentsEditor from '../CompositeComponentsEditor'
import type { MeasureDefinition } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const getMeasures = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: { getMeasures: (...args: unknown[]) => getMeasures(...args) },
}))

const measure = (id: number, name: string, status: string, scoringType = 'proportion'): MeasureDefinition =>
  ({ id, name, version: '1.0.0', status, scoringType, cqlContent: '' })

// PAT-259 — the component picker of a composite measure.
describe('CompositeComponentsEditor', () => {
  beforeEach(() => {
    getMeasures.mockReset()
    getMeasures.mockResolvedValue([
      measure(1, 'Self', 'draft', 'composite'),
      measure(2, 'HbA1c', 'active'),
      measure(3, 'BloodPressure', 'draft'),
      measure(4, 'OtherComposite', 'active', 'composite'),
    ])
  })

  it('offers the other non-composite measures, keeps the order, and warns about an inactive component', async () => {
    const onChange = vi.fn()
    render(<CompositeComponentsEditor selfId={1} componentMeasureIds={[3]} method="opportunity" onChange={onChange} />)
    await waitFor(() => expect(getMeasures).toHaveBeenCalled())

    expect(screen.getByText('details.composite.needTwo')).toBeInTheDocument()
    expect(await screen.findByText('details.composite.inactiveWarning')).toBeInTheDocument()   // BloodPressure is a draft

    fireEvent.mouseDown(screen.getByRole('combobox'))
    const options = await screen.findAllByRole('option')
    const labels = options.map((o) => o.textContent)
    expect(labels.some((l) => l?.startsWith('HbA1c v1.0.0'))).toBe(true)
    expect(labels.some((l) => l?.startsWith('Self'))).toBe(false)             // never itself
    expect(labels.some((l) => l?.startsWith('OtherComposite'))).toBe(false)   // no nesting

    fireEvent.click(options.find((o) => o.textContent?.startsWith('HbA1c'))!)
    expect(onChange).toHaveBeenLastCalledWith([3, 2], undefined)
  })

  it('weighted: one weight field per component, reported as a map keyed by measure id', async () => {
    const onChange = vi.fn()
    render(
      <CompositeComponentsEditor
        selfId={1} componentMeasureIds={[2, 3]} componentWeights={{ '2': 0.7 }} method="weighted" onChange={onChange}
      />,
    )
    await waitFor(() => expect(getMeasures).toHaveBeenCalled())
    expect(screen.getByTestId('component-weight-2')).toHaveValue(0.7)

    fireEvent.change(screen.getByTestId('component-weight-3'), { target: { value: '0.3' } })
    expect(onChange).toHaveBeenLastCalledWith([2, 3], { '2': 0.7, '3': 0.3 })

    fireEvent.change(screen.getByTestId('component-weight-2'), { target: { value: '' } })
    expect(onChange).toHaveBeenLastCalledWith([2, 3], undefined)   // the remaining map is empty → no weights
  })

  it('a non-weighted method shows no weight fields', async () => {
    render(<CompositeComponentsEditor selfId={1} componentMeasureIds={[2, 3]} method="all-or-nothing" onChange={vi.fn()} />)
    await waitFor(() => expect(getMeasures).toHaveBeenCalled())
    expect(screen.queryByTestId('component-weight-2')).not.toBeInTheDocument()
    expect(screen.getByText('details.composite.componentsHelper')).toBeInTheDocument()
  })
})
