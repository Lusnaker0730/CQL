import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import QualityReportPanel from '../QualityReportPanel'
import type { QualityReportData, MeasureScoreSummary } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object' && 'defaultValue' in opts && !('shown' in opts)) {
        return String((opts as { defaultValue: unknown }).defaultValue)
      }
      // Synthesize the truncation footer with simple {{var}} interp so we can
      // verify the values, not the whole sentence wording.
      if (key === 'dashboard.tablePreviewTruncated' && opts) {
        const o = opts as { shown?: number; total?: number }
        return `Showing ${o.shown} of ${o.total} measures`
      }
      if (key === 'common.notAvailable' || key === 'notAvailable') return 'N/A'
      return key
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

function makeMeasure(over: Partial<MeasureScoreSummary> = {}): MeasureScoreSummary {
  return {
    measureId: 1,
    measureName: 'HbA1c Control',
    score: 72.4,
    status: 'below_target',
    targetThreshold: 80,
    scoringType: 'proportion',
    ...over,
  }
}

function makeReport(over: Partial<QualityReportData> = {}): QualityReportData {
  return {
    reportType: 'monthly',
    periodLabel: '2025-Q1',
    totalMeasures: 1,
    measuresAboveTarget: 0,
    measuresBelowTarget: 1,
    averageScore: 72.4,
    measureScores: [makeMeasure()],
    departmentAverages: {},
    ...over,
  }
}

describe('QualityReportPanel — PAT-151', () => {
  it('renders empty state when report is null', () => {
    render(<QualityReportPanel report={null} />)
    expect(screen.getByText(/dashboard\.noReportData/)).toBeInTheDocument()
  })

  it('proportion measures render score and threshold with % suffix', () => {
    render(
      <QualityReportPanel
        report={makeReport({
          measureScores: [
            makeMeasure({ score: 72.4, targetThreshold: 80, scoringType: 'proportion' }),
          ],
        })}
      />,
    )
    // averageScore (h5) + table cell both render "72.4%" — both are correct.
    expect(screen.getAllByText('72.4%').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('80.0%')).toBeInTheDocument()
  })

  it('PAT-151 regression: continuous-variable threshold no longer rendered as percent', () => {
    // Pre-fix: targetThreshold 7.0 mmol/L showed as "7%" (line 84 hard-coded `${ms.targetThreshold}%`).
    render(
      <QualityReportPanel
        report={makeReport({
          measureScores: [
            makeMeasure({
              measureId: 1,
              measureName: 'HbA1c',
              score: 5.6,
              targetThreshold: 7.0,
              scoringType: 'continuous-variable',
            }),
          ],
        })}
      />,
    )
    // formatScoreValue uses formatRaw which gives 2 decimals for |v| < 10.
    expect(screen.getByText('5.60')).toBeInTheDocument()
    // Threshold cell — must NOT be "7%". Plain "7.00" (no unit, since panel
    // doesn't carry per-row unit in the type yet).
    expect(screen.getByText('7.00')).toBeInTheDocument()
    // Sanity: no "7%" anywhere in table cells.
    const cells = screen.getAllByRole('cell')
    expect(cells.some((c) => c.textContent === '7%')).toBe(false)
  })

  it('null targetThreshold renders as "-"', () => {
    render(
      <QualityReportPanel
        report={makeReport({
          measureScores: [makeMeasure({ targetThreshold: undefined })],
        })}
      />,
    )
    const cells = screen.getAllByRole('cell')
    expect(cells.some((c) => c.textContent === '-')).toBe(true)
  })

  it('truncates the table to 10 rows by default', () => {
    const measures = Array.from({ length: 15 }, (_, i) =>
      makeMeasure({ measureId: i + 1, measureName: `Measure ${i + 1}` }),
    )
    render(<QualityReportPanel report={makeReport({ measureScores: measures })} />)
    // Only first 10 measure names visible
    expect(screen.getByText('Measure 1')).toBeInTheDocument()
    expect(screen.getByText('Measure 10')).toBeInTheDocument()
    expect(screen.queryByText('Measure 11')).not.toBeInTheDocument()
  })

  it('PAT-151 regression: shows truncation footer when more than 10 measures', () => {
    const measures = Array.from({ length: 15 }, (_, i) =>
      makeMeasure({ measureId: i + 1, measureName: `Measure ${i + 1}` }),
    )
    render(<QualityReportPanel report={makeReport({ measureScores: measures })} />)
    expect(screen.getByText('Showing 10 of 15 measures')).toBeInTheDocument()
  })

  it('does NOT show truncation footer when count is within the preview limit', () => {
    const measures = Array.from({ length: 5 }, (_, i) =>
      makeMeasure({ measureId: i + 1, measureName: `Measure ${i + 1}` }),
    )
    render(<QualityReportPanel report={makeReport({ measureScores: measures })} />)
    expect(screen.queryByText(/Showing \d+ of \d+ measures/)).not.toBeInTheDocument()
  })
})
