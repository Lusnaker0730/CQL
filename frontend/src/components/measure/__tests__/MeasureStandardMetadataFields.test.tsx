import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '../../../test/test-utils'
import MeasureStandardMetadataFields from '../MeasureStandardMetadataFields'
import { clearedDatesAsEmpty, effectivePeriodInverted, standardMetadataFilled } from '../../../utils/measureMetadata'
import type { MeasureStandardMetadata } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// PAT-236 — the standard FHIR Measure metadata fields shared by the measure details page and
// the eCQM summary tab: every edit is reported as a partial update the caller merges.
describe('MeasureStandardMetadataFields', () => {
  const filled: MeasureStandardMetadata = {
    measureTypes: ['process', 'outcome'],
    definitionTerms: [{ term: 'HbA1c control', definition: 'Most recent HbA1c < 7%' }],
    clinicalRecommendationStatement: 'ADA 2026',
    effectiveStart: '2026-01-01',
    effectiveEnd: '2026-12-31',
    approvalDate: '2025-11-20',
    lastReviewDate: '2026-06-15',
    experimental: true,
  }

  it('renders the current values — type chips, dates, statement, terms, experimental on', () => {
    render(<MeasureStandardMetadataFields value={filled} onChange={vi.fn()} />)

    expect(screen.getByText('standardMetadata.types.process')).toBeInTheDocument()
    expect(screen.getByText('standardMetadata.types.outcome')).toBeInTheDocument()
    expect(screen.getByTestId('standard-metadata-effectiveStart')).toHaveValue('2026-01-01')
    expect(screen.getByTestId('standard-metadata-effectiveEnd')).toHaveValue('2026-12-31')
    expect(screen.getByTestId('standard-metadata-approvalDate')).toHaveValue('2025-11-20')
    expect(screen.getByTestId('standard-metadata-lastReviewDate')).toHaveValue('2026-06-15')
    expect(screen.getByDisplayValue('ADA 2026')).toBeInTheDocument()
    expect(screen.getByDisplayValue('HbA1c control')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Most recent HbA1c < 7%')).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'standardMetadata.experimental' })).toBeChecked()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports each edit as a partial update: switch, date, statement, term text', () => {
    const onChange = vi.fn()
    render(<MeasureStandardMetadataFields value={filled} onChange={onChange} />)

    fireEvent.click(screen.getByRole('switch', { name: 'standardMetadata.experimental' }))
    expect(onChange).toHaveBeenLastCalledWith({ experimental: false })

    fireEvent.change(screen.getByTestId('standard-metadata-approvalDate'), { target: { value: '2026-02-03' } })
    expect(onChange).toHaveBeenLastCalledWith({ approvalDate: '2026-02-03' })

    // clearing a date is an explicit null (the measure PUT clears a LocalDate with it; the
    // artifact tab maps it to '' for its partial-update API)
    fireEvent.change(screen.getByTestId('standard-metadata-lastReviewDate'), { target: { value: '' } })
    expect(onChange).toHaveBeenLastCalledWith({ lastReviewDate: null })

    fireEvent.change(screen.getByDisplayValue('ADA 2026'), { target: { value: 'ADA 2027' } })
    expect(onChange).toHaveBeenLastCalledWith({ clinicalRecommendationStatement: 'ADA 2027' })

    fireEvent.change(screen.getByDisplayValue('HbA1c control'), { target: { value: 'Glycaemic control' } })
    expect(onChange).toHaveBeenLastCalledWith({
      definitionTerms: [{ term: 'Glycaemic control', definition: 'Most recent HbA1c < 7%' }],
    })
  })

  it('adds and removes definition terms', () => {
    const onChange = vi.fn()
    render(<MeasureStandardMetadataFields value={filled} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: 'standardMetadata.addTerm' }))
    expect(onChange).toHaveBeenLastCalledWith({
      definitionTerms: [{ term: 'HbA1c control', definition: 'Most recent HbA1c < 7%' }, { term: '', definition: '' }],
    })

    fireEvent.click(screen.getByRole('button', { name: 'standardMetadata.removeTerm' }))
    expect(onChange).toHaveBeenLastCalledWith({ definitionTerms: [] })
  })

  it('selecting a measure type reports the new list', () => {
    const onChange = vi.fn()
    render(<MeasureStandardMetadataFields value={{ measureTypes: ['process'] }} onChange={onChange} />)

    fireEvent.mouseDown(screen.getByRole('combobox'))
    const listbox = within(screen.getByRole('listbox'))
    fireEvent.click(listbox.getByText('standardMetadata.types.structure'))

    expect(onChange).toHaveBeenLastCalledWith({ measureTypes: ['process', 'structure'] })
  })

  it('flags an effective period that ends before it starts', () => {
    render(
      <MeasureStandardMetadataFields
        value={{ effectiveStart: '2026-06-01', effectiveEnd: '2026-01-01' }}
        onChange={vi.fn()}
      />
    )
    expect(screen.getByRole('alert')).toHaveTextContent('standardMetadata.effectivePeriodInverted')
  })

  it('read-only hides the add / remove controls and disables the inputs', () => {
    render(<MeasureStandardMetadataFields value={filled} onChange={vi.fn()} readOnly />)

    expect(screen.queryByRole('button', { name: 'standardMetadata.addTerm' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'standardMetadata.removeTerm' })).not.toBeInTheDocument()
    expect(screen.getByTestId('standard-metadata-effectiveStart')).toBeDisabled()
    expect(screen.getByRole('switch', { name: 'standardMetadata.experimental' })).toBeDisabled()
  })

  it('helpers: filled detection and period inversion', () => {
    expect(standardMetadataFilled({})).toBe(false)
    expect(standardMetadataFilled({ experimental: false })).toBe(false)
    expect(standardMetadataFilled({ clinicalRecommendationStatement: '   ' })).toBe(false)
    expect(standardMetadataFilled({ measureTypes: ['process'] })).toBe(true)
    expect(standardMetadataFilled({ approvalDate: '2026-01-01' })).toBe(true)
    expect(standardMetadataFilled({ experimental: true })).toBe(true)
    expect(effectivePeriodInverted('2026-01-01', '2026-12-31')).toBe(false)
    expect(effectivePeriodInverted('2026-01-01', '2026-01-01')).toBe(false)
    expect(effectivePeriodInverted('2026-12-31', '2026-01-01')).toBe(true)
    expect(effectivePeriodInverted(undefined, '2026-01-01')).toBe(false)
  })

  it('helper: the artifact partial-update API gets "" for a cleared date, other keys untouched', () => {
    expect(clearedDatesAsEmpty({ effectiveEnd: null, approvalDate: '2026-01-01', experimental: false }))
      .toEqual({ effectiveEnd: '', approvalDate: '2026-01-01', experimental: false })
    // a date key that is not part of the update stays absent (absent = keep on the server)
    expect(clearedDatesAsEmpty({ clinicalRecommendationStatement: 'x' })).toEqual({ clinicalRecommendationStatement: 'x' })
  })
})
