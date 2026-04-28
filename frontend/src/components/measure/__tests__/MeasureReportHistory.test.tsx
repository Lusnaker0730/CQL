import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import MeasureReportHistory from '../MeasureReportHistory'

// test-utils does NOT initialize i18next; useTranslation falls back to raw
// keys. Mock so queries match the i18n key strings.
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

let getReportsForMeasureMock: ReturnType<typeof vi.fn>
let getReportsMock: ReturnType<typeof vi.fn>
let deleteReportMock: ReturnType<typeof vi.fn>

vi.mock('../../../api', () => ({
  measureApi: {
    getReportsForMeasure: (id: number) => getReportsForMeasureMock(id),
    getReports: () => getReportsMock(),
    deleteReport: (id: number) => deleteReportMock(id),
    exportReport: vi.fn(),
  },
}))

const REPORT = {
  id: 99,
  measureName: 'Diabetes Screening',
  measureScore: 87,
  status: 'complete',
  createdAt: '2026-04-01T00:00:00Z',
  evaluationDurationMs: 1234,
  periodStart: '2026-01-01',
  periodEnd: '2026-03-31',
}

describe('MeasureReportHistory — PAT-130 delete confirmation', () => {
  beforeEach(() => {
    getReportsForMeasureMock = vi.fn().mockResolvedValue([REPORT])
    getReportsMock = vi.fn().mockResolvedValue([REPORT])
    deleteReportMock = vi.fn().mockResolvedValue(undefined)
  })

  it('opens a confirmation dialog instead of deleting immediately', async () => {
    render(<MeasureReportHistory measureId={1} />)
    await waitFor(() =>
      expect(screen.getByText('Diabetes Screening')).toBeInTheDocument(),
    )

    fireEvent.click(screen.getByRole('button', { name: 'reports.deleteReport' }))

    // Dialog visible, but no delete request fired yet
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(deleteReportMock).not.toHaveBeenCalled()
  })

  it('cancels without deleting when the Cancel button is pressed', async () => {
    render(<MeasureReportHistory measureId={1} />)
    await screen.findByText('Diabetes Screening')
    fireEvent.click(screen.getByRole('button', { name: 'reports.deleteReport' }))
    fireEvent.click(screen.getByRole('button', { name: 'reports.cancel' }))
    expect(deleteReportMock).not.toHaveBeenCalled()
  })

  it('actually deletes only after explicit confirmation', async () => {
    render(<MeasureReportHistory measureId={1} />)
    await screen.findByText('Diabetes Screening')
    fireEvent.click(screen.getByRole('button', { name: 'reports.deleteReport' }))

    // Find the dialog's confirm button — label key is 'reports.confirmDelete'
    const dialog = screen.getByRole('dialog')
    const confirmBtn = Array.from(dialog.querySelectorAll('button')).find(
      (b) => b.textContent?.includes('reports.confirmDelete'),
    )
    expect(confirmBtn).toBeTruthy()
    fireEvent.click(confirmBtn!)

    await waitFor(() => expect(deleteReportMock).toHaveBeenCalledWith(99))
  })
})
