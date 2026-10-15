import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '../../../test/test-utils'
import HumanReadablePreviewDialog from '../HumanReadablePreviewDialog'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { changeLanguage: vi.fn() } }),
}))
const exportHumanReadable = vi.fn()
vi.mock('../../../api', () => ({ measureApi: { exportHumanReadable: (...a: unknown[]) => exportHumanReadable(...a) } }))
const downloadBlob = vi.fn()
vi.mock('../../../utils/download', () => ({ downloadBlob: (...a: unknown[]) => downloadBlob(...a) }))

// PAT-263 — the human-readable document is shown in place (sandboxed iframe) and can be downloaded.
describe('HumanReadablePreviewDialog', () => {
  beforeEach(() => {
    exportHumanReadable.mockReset()
    downloadBlob.mockReset()
  })

  it('loads the HTML into a sandboxed frame and downloads the same blob', async () => {
    const blob = new Blob(['<!DOCTYPE html><html><body><h1>HbA1c</h1></body></html>'], { type: 'text/html' })
    exportHumanReadable.mockResolvedValue(blob)
    render(<HumanReadablePreviewDialog open onClose={vi.fn()} measureId={7} measureName="HbA1c" />)

    const frame = await screen.findByTestId('human-readable-frame')
    expect(exportHumanReadable).toHaveBeenCalledWith(7)
    expect(frame.getAttribute('sandbox')).toBe('')
    await waitFor(() => expect(frame.getAttribute('srcdoc')).toContain('<h1>HbA1c</h1>'))

    fireEvent.click(screen.getByRole('button', { name: 'humanReadable.download' }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'HbA1c-narrative.html')
  })

  it('shows the error when the document cannot be loaded', async () => {
    exportHumanReadable.mockRejectedValue(new Error('boom'))
    render(<HumanReadablePreviewDialog open onClose={vi.fn()} measureId={7} measureName="HbA1c" />)
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    expect(screen.queryByTestId('human-readable-frame')).not.toBeInTheDocument()
  })
})
