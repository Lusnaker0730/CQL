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
