import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import ThresholdAlertPanel from '../ThresholdAlertPanel'
import type { ThresholdAlert } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      // For default-value lookups we want the explicit fallback, not the key.
      if (opts && typeof opts === 'object' && 'defaultValue' in opts) {
        return String((opts as { defaultValue: unknown }).defaultValue)
      }
      // Map a couple of well-known keys to friendly text so assertions read clearer.
      if (key === 'common.notAvailable' || key === 'notAvailable') return 'N/A'
      if (key === 'dashboard.score') return 'Score'
      return key
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

describe('ThresholdAlertPanel — PAT-151', () => {
  function makeAlert(overrides: Partial<ThresholdAlert> = {}): ThresholdAlert {
    return {
      measureId: 1,
      measureName: 'HbA1c Control',
      thresholdType: 'target',
      thresholdValue: 80,
      actualScore: 72.4,
      comparisonOperator: '<',
      severity: 'warning',
      ...overrides,
    }
  }

  it('shows empty-state copy when there are no alerts', () => {
    render(<ThresholdAlertPanel alerts={[]} />)
    expect(screen.getByText(/dashboard\.noAlerts/i)).toBeInTheDocument()
  })

  it('formats proportion measures with a percent sign', () => {
    render(
      <ThresholdAlertPanel
        alerts={[makeAlert({ scoringType: 'proportion' })]}
      />,
    )
    // Score: 72.4% < 80.0%
    const row = screen.getByText(/72\.4%.*<.*80\.0%/)
    expect(row).toBeInTheDocument()
  })

  it('PAT-151 regression: continuous-variable measures use the unit, never %', () => {
    // HbA1c < 7.0 mmol/L. Pre-fix this rendered as "5.6% < 7%".
    render(
      <ThresholdAlertPanel
        alerts={[
          makeAlert({
            measureName: 'HbA1c',
            scoringType: 'continuous-variable',
            unit: 'mmol/L',
            actualScore: 5.6,
            thresholdValue: 7.0,
          }),
        ]}
      />,
    )
    // Should contain mmol/L, must NOT contain %.
    const row = screen.getByText(/5\.60 mmol\/L.*<.*7\.00 mmol\/L/)
    expect(row).toBeInTheDocument()
    expect(row.textContent).not.toMatch(/%/)
  })

  it('PAT-151 regression: cohort measures render as integer counts', () => {
    render(
      <ThresholdAlertPanel
        alerts={[
          makeAlert({
            measureName: 'Diabetes Cohort',
            scoringType: 'cohort',
            actualScore: 1234,
            thresholdValue: 1000,
            comparisonOperator: '>',
          }),
        ]}
      />,
    )
    const row = screen.getByText(/1234.*>.*1000/)
    expect(row).toBeInTheDocument()
    expect(row.textContent).not.toMatch(/%/)
  })

  it('PAT-151 regression: null actualScore shows N/A instead of "undefined.0%"', () => {
    render(
      <ThresholdAlertPanel
        alerts={[
          makeAlert({
            // @ts-expect-error — exercise the null-score path explicitly
            actualScore: null,
            scoringType: 'proportion',
          }),
        ]}
      />,
    )
    expect(screen.getByText(/N\/A/)).toBeInTheDocument()
  })

  it('uses a stable composite key (not array index) for list items', () => {
    // Same measureId with two different thresholdTypes must both render distinctly.
    render(
      <ThresholdAlertPanel
        alerts={[
          makeAlert({ measureId: 1, thresholdType: 'target', actualScore: 50 }),
          makeAlert({ measureId: 1, thresholdType: 'warning', actualScore: 60 }),
        ]}
      />,
    )
    expect(screen.getAllByText('HbA1c Control').length).toBe(2)
  })
})
