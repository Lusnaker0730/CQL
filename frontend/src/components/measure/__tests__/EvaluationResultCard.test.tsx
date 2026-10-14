import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '../../../test/test-utils'
import EvaluationResultCard from '../EvaluationResultCard'
import type { MeasureEvaluationResult } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const base: MeasureEvaluationResult = {
  measureId: '1',
  measureName: 'Demo',
  status: 'complete',
  periodStart: '2022-01-01',
  periodEnd: '2022-06-30',
  groups: [{
    groupId: 'group-1',
    populations: [{ populationType: 'initial-population', count: 7 }],
    measureScore: 60,
  }],
} as unknown as MeasureEvaluationResult

// PAT-234 — supplemental data / risk adjustment factors are shown as value distributions.
describe('EvaluationResultCard — supplemental data distributions', () => {
  it('lists every value with its patient count and share, the no-value row, and the usage', () => {
    render(<EvaluationResultCard result={{
      ...base,
      supplementalDataResults: [
        {
          definition: 'SDE Sex', usage: 'supplemental-data', patientsWithoutValue: 1,
          values: [{ value: 'female', count: 4 }, { value: 'male', count: 3 }],
        },
        {
          definition: 'RAF Age Band', usage: 'risk-adjustment-factor', description: 'Age at period end',
          patientsWithoutValue: 0, values: [{ value: '65+', count: 3 }],
        },
        { definition: 'RAF Payer', usage: 'risk-adjustment-factor', patientsWithoutValue: 0, values: [] },
      ],
    }} />)

    const table = screen.getByRole('table', { name: 'evaluationResult.supplementalDataResults.title' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(5)

    expect(within(rows[0]).getByText('SDE Sex')).toBeInTheDocument()
    expect(within(rows[0]).getByText('evaluationResult.supplementalDataResults.supplementalData')).toBeInTheDocument()
    expect(within(rows[0]).getByText('female')).toBeInTheDocument()
    expect(within(rows[0]).getByText('4')).toBeInTheDocument()
    expect(within(rows[0]).getByText('50.0%')).toBeInTheDocument() // 4 of 8 (7 with a value + 1 without)
    expect(within(rows[2]).getByText('evaluationResult.supplementalDataResults.noValue')).toBeInTheDocument()
    expect(within(rows[2]).getByText('12.5%')).toBeInTheDocument()

    expect(within(rows[3]).getByText('RAF Age Band')).toBeInTheDocument()
    expect(within(rows[3]).getByText('Age at period end')).toBeInTheDocument()
    expect(within(rows[3]).getByText('evaluationResult.supplementalDataResults.riskAdjustment')).toBeInTheDocument()
    expect(within(rows[3]).getByText('100.0%')).toBeInTheDocument()

    expect(within(rows[4]).getByText('evaluationResult.supplementalDataResults.empty')).toBeInTheDocument()
  })

  it('falls back to the legacy count map only when there is no distribution', () => {
    const { unmount } = render(<EvaluationResultCard result={{ ...base, supplementalData: { 'SDE Sex': 7 } }} />)
    expect(screen.getByText('evaluationResult.supplementalData')).toBeInTheDocument()
    expect(screen.queryByTestId('supplemental-data-results')).not.toBeInTheDocument()
    unmount()

    render(<EvaluationResultCard result={{
      ...base,
      supplementalData: { 'SDE Sex': 7 },
      supplementalDataResults: [{ definition: 'SDE Sex', usage: 'supplemental-data', patientsWithoutValue: 0, values: [{ value: 'female', count: 7 }] }],
    }} />)
    expect(screen.getByTestId('supplemental-data-results')).toBeInTheDocument()
    expect(screen.queryByText('evaluationResult.supplementalData')).not.toBeInTheDocument()
  })
})

// PAT-235 — a multi-component stratum shows one chip per component instead of the combined text.
describe('EvaluationResultCard — component strata', () => {
  it('renders component chips for a multi-component stratum and a plain chip otherwise', () => {
    render(<EvaluationResultCard result={{
      ...base,
      groups: [{
        ...base.groups[0],
        stratifiers: [
          { strataId: 'sex-age', strataValue: 'female | 65+', components: [{ code: 'sex', value: 'female' }, { code: 'age', value: '65+' }],
            populations: [{ populationType: 'initial-population', count: 3 }], measureScore: 100 },
          { strataId: 'gender', strataValue: 'true', populations: [{ populationType: 'initial-population', count: 3 }], measureScore: 100 },
        ],
      }],
    }} />)

    const chips = within(screen.getByTestId('stratum-components')).getAllByText(/./)
    expect(chips.map((c) => c.textContent)).toEqual(['sex: female', 'age: 65+'])
    expect(screen.getByText('true')).toBeInTheDocument()
    expect(screen.queryByText('female | 65+')).not.toBeInTheDocument()
  })
})

// PAT-243 — episode-based groups label their counts as episodes; evaluation warnings are shown.
describe('EvaluationResultCard — episode-based groups and warnings', () => {
  it('labels the count column with the basis and lists the warnings', () => {
    render(<EvaluationResultCard result={{
      ...base,
      groups: [{ ...base.groups[0], populationBasis: 'Encounter' }],
      warnings: ['Group group-1: population basis is Encounter but "Initial Population" did not return a list of Encounter resources — this group was counted per patient, not per episode.'],
    }} />)

    expect(screen.getByText('evaluationResult.tableHeaders.countEpisodes')).toBeInTheDocument()
    expect(screen.getByTestId('evaluation-warnings')).toHaveTextContent('counted per patient')
  })

  it('keeps the plain count label for a patient-based group and shows no warning box', () => {
    render(<EvaluationResultCard result={{ ...base, groups: [{ ...base.groups[0], populationBasis: 'boolean' }] }} />)

    expect(screen.getByText('evaluationResult.tableHeaders.count')).toBeInTheDocument()
    expect(screen.queryByTestId('evaluation-warnings')).not.toBeInTheDocument()
  })
})

// PAT-259 — a composite result lists what every component contributed.
describe('EvaluationResultCard — composite components', () => {
  it('shows one row per component with numerator / effective denominator and score; weights only when set', () => {
    render(<EvaluationResultCard result={{
      ...base,
      componentScores: [
        { measureId: 2, measureName: 'HbA1c', version: '1.0.0', score: 50, numerator: 1, denominator: 3, denominatorExclusions: 1, weight: 0.7 },
        { measureId: 3, measureName: 'BloodPressure', version: '2.0.0', status: 'error' },
      ],
    }} />)

    const table = screen.getByRole('table', { name: 'evaluationResult.components.title' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('HbA1c v1.0.0')
    expect(rows[1]).toHaveTextContent('1 / 2')          // denominator − exclusions
    expect(rows[1]).toHaveTextContent('50.0%')
    expect(rows[1]).toHaveTextContent('0.7')
    expect(rows[2]).toHaveTextContent('BloodPressure v2.0.0')
    expect(rows[2]).toHaveTextContent('evaluationResult.components.failed')
    expect(within(rows[2]).getAllByText('evaluationResult.na').length).toBeGreaterThan(0)
  })

  it('renders no component table for an ordinary measure', () => {
    render(<EvaluationResultCard result={base} />)
    expect(screen.queryByTestId('component-scores')).not.toBeInTheDocument()
  })
})

// PAT-260 — the value set versions a run resolved are listed; external ones are marked.
describe('EvaluationResultCard — terminology versions', () => {
  it('shows one chip per value set with the version used', () => {
    render(<EvaluationResultCard result={{
      ...base,
      terminologyVersions: {
        'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders': '1.0.0',
        'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001': 'external',
      },
    }} />)
    const box = screen.getByTestId('terminology-versions')
    expect(box).toHaveTextContent('HbA1cOrders · v1.0.0')
    expect(box).toHaveTextContent('evaluationResult.terminologyVersions.external')
  })
})
