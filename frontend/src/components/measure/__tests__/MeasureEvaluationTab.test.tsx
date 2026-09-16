import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import MeasureEvaluationTab from '../MeasureEvaluationTab'
import type { MeasureDefinition } from '../../../types'

// test-utils does NOT initialize i18next; useTranslation falls back to raw
// keys. Mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

// Siblings irrelevant to the lifecycle gate — keep the render light.
vi.mock('../MeasureScheduleManager', () => ({ default: () => <div /> }))
vi.mock('../EvaluationResultCard', () => ({ default: () => <div /> }))
vi.mock('../../common/FhirServerUrlField', () => ({
  default: () => <div data-testid="fhir-url-field" />,
}))

const stored: MeasureDefinition = {
  id: 7,
  name: 'guarded-measure',
  version: '1.0.0',
  status: 'draft',
  scoringType: 'proportion',
  cqlContent: "library G version '1.0.0'",
}

const evaluateButton = () =>
  screen.getByRole('button', { name: /evaluation\.evaluateMeasure/ })

// PAT-219: the backend returns 409 Measure Not Evaluable for any stored measure
// that is not `active`. The tab mirrors that up front — warning + disabled
// button — so users learn why before clicking, not from an error afterwards.
describe('MeasureEvaluationTab — PAT-219 lifecycle gate', () => {
  it.each(['draft', 'in-review', 'retired'])(
    'warns and disables Evaluate for a stored %s measure',
    (status) => {
      render(<MeasureEvaluationTab measure={{ ...stored, status }} />)

      expect(screen.getByTestId('measure-not-active-warning')).toHaveTextContent(
        'evaluation.notActiveWarning',
      )
      expect(evaluateButton()).toBeDisabled()
    },
  )

  it('lets an active stored measure evaluate', () => {
    render(<MeasureEvaluationTab measure={{ ...stored, status: 'active' }} />)

    expect(screen.queryByTestId('measure-not-active-warning')).not.toBeInTheDocument()
    expect(evaluateButton()).toBeEnabled()
  })

  it('does not gate an unsaved measure (ad-hoc inline-CQL path has no lifecycle)', () => {
    render(<MeasureEvaluationTab measure={{ ...stored, id: undefined, status: 'draft' }} />)

    expect(screen.queryByTestId('measure-not-active-warning')).not.toBeInTheDocument()
    expect(evaluateButton()).toBeEnabled()
  })
})
