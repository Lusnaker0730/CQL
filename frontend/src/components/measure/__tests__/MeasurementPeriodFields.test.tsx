import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import MeasurementPeriodFields from '../MeasurementPeriodFields'
import { clearedDatesAsEmpty, measurementPeriodInverted } from '../../../utils/measureMetadata'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// PAT-242 — the measure's own Measurement Period, shared by the measure details page and the
// eCQM summary tab: every edit is reported as a partial update the caller merges.
describe('MeasurementPeriodFields', () => {
  it('renders the current period with the hint', () => {
    render(
      <MeasurementPeriodFields
        value={{ measurementPeriodStart: '2024-01-01', measurementPeriodEnd: '2024-12-31' }}
        onChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('measurement-period-measurementPeriodStart')).toHaveValue('2024-01-01')
    expect(screen.getByTestId('measurement-period-measurementPeriodEnd')).toHaveValue('2024-12-31')
    expect(screen.getByText('measurementPeriod.hint')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports a set date as itself and a cleared date as null', () => {
    const onChange = vi.fn()
    render(<MeasurementPeriodFields value={{ measurementPeriodStart: '2024-01-01' }} onChange={onChange} />)

    fireEvent.change(screen.getByTestId('measurement-period-measurementPeriodEnd'), { target: { value: '2024-12-31' } })
    expect(onChange).toHaveBeenLastCalledWith({ measurementPeriodEnd: '2024-12-31' })

    fireEvent.change(screen.getByTestId('measurement-period-measurementPeriodStart'), { target: { value: '' } })
    expect(onChange).toHaveBeenLastCalledWith({ measurementPeriodStart: null })
  })

  it('flags an inverted period and disables the inputs when read-only', () => {
    render(
      <MeasurementPeriodFields
        value={{ measurementPeriodStart: '2024-12-31', measurementPeriodEnd: '2024-01-01' }}
        onChange={vi.fn()}
        readOnly
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('measurementPeriod.inverted')
    expect(screen.getByTestId('measurement-period-measurementPeriodStart')).toBeDisabled()
  })

  it('utils: inverted only when both ends are set and out of order; the artifact API gets "" for a cleared date', () => {
    expect(measurementPeriodInverted('2024-01-01', '2024-12-31')).toBe(false)
    expect(measurementPeriodInverted('2024-12-31', '2024-01-01')).toBe(true)
    expect(measurementPeriodInverted(null, '2024-01-01')).toBe(false)
    expect(clearedDatesAsEmpty({ measurementPeriodStart: null, measurementPeriodEnd: '2024-12-31' }))
      .toEqual({ measurementPeriodStart: '', measurementPeriodEnd: '2024-12-31' })
  })
})
