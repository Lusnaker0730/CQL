import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '../../../test/test-utils'
import VersionDiffDialog from '../VersionDiffDialog'
import type { MeasureVersionDiff } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts && typeof opts.count === 'number' ? `${key}:${opts.count}` : opts && typeof opts.id === 'string' ? `${key}:${opts.id}` : key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))
vi.mock('../../../hooks/usePreferences', () => ({ usePreferences: () => ({ preferences: { themeMode: 'light' } }) }))
vi.mock('react-diff-viewer-continued', () => ({
  default: () => <div data-testid="cql-diff-viewer" />,
  DiffMethod: { WORDS: 'words' },
}))

const structured: MeasureVersionDiff = {
  oldCql: 'define A: 1', newCql: 'define A: 2', oldVersion: '1.0.0', newVersion: '1.1.0', cqlChanged: true,
  cqlLines: [], metadata: [{ section: 'identity', field: 'title', kind: 'changed', oldValue: 'Old', newValue: 'New' }],
  groups: [
    { groupId: 'g1', kind: 'changed', fields: [], populations: [{ key: 'numerator', kind: 'changed', oldValue: 'Numerator', newValue: 'Numerator Controlled' }], stratifiers: [], observations: [] },
    { groupId: 'g2', kind: 'unchanged', fields: [], populations: [], stratifiers: [], observations: [] },
  ],
  summary: { metadata: 1, groups: 0, populations: 1, stratifiers: 0, observations: 0, cqlAdded: 1, cqlRemoved: 1, total: 3 },
  metadataChanges: ['Title changed: "Old" → "New"'],
  populationChanges: ['g1: population numerator changed: "Numerator" → "Numerator Controlled"'],
}

const pick = async (labelId: string, option: string) => {
  fireEvent.mouseDown(screen.getByLabelText(labelId))
  fireEvent.click(await within(screen.getByRole('listbox')).findByText(option))
}

// PAT-263 — the measure compare brings a structured diff; the dialog renders it as tables and can
// download the HTML report. The CQL-library compare (sentences only) still renders its lists.
describe('VersionDiffDialog', () => {
  it('renders the structured metadata and population tables, and downloads the report', async () => {
    const onCompare = vi.fn().mockResolvedValue(structured)
    const onExportHtml = vi.fn().mockResolvedValue(undefined)
    render(<VersionDiffDialog open onClose={vi.fn()} versions={[{ id: 1, version: '1.0.0' }, { id: 2, version: '1.1.0' }]} onCompare={onCompare} onExportHtml={onExportHtml} />)

    await pick('diff.oldVersion', '1.0.0')
    await pick('diff.newVersion', '1.1.0')
    fireEvent.click(screen.getByRole('button', { name: 'diff.compare' }))
    await waitFor(() => expect(onCompare).toHaveBeenCalledWith(1, 2))
    expect(await screen.findByText('diff.changes:3')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /diff\.metadataDiff/ }))
    const row = screen.getByTestId('diff-field-title')
    expect(within(row).getByText('diff.sections.identity')).toBeInTheDocument()
    expect(within(row).getByText('Old')).toBeInTheDocument()
    expect(within(row).getByText('New')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /diff\.populationDiff/ }))
    expect(screen.getByTestId('diff-group-g1')).toBeInTheDocument()
    expect(screen.queryByTestId('diff-group-g2')).not.toBeInTheDocument()
    expect(within(screen.getByTestId('diff-item-numerator')).getByText('Numerator Controlled')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'diff.downloadHtml' }))
    await waitFor(() => expect(onExportHtml).toHaveBeenCalledWith(1, 2))
  })

  it('still lists plain sentences for a comparison without structure (CQL libraries)', async () => {
    const onCompare = vi.fn().mockResolvedValue({ oldCql: 'a', newCql: 'b', oldVersion: '1', newVersion: '2', metadataChanges: ['Description changed'], populationChanges: [] })
    render(<VersionDiffDialog open onClose={vi.fn()} versions={[{ id: 'a', version: '1' }, { id: 'b', version: '2' }]} onCompare={onCompare} />)

    await pick('diff.oldVersion', '1')
    await pick('diff.newVersion', '2')
    fireEvent.click(screen.getByRole('button', { name: 'diff.compare' }))
    expect(await screen.findByText('diff.changes:1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: /diff\.metadataDiff/ }))
    expect(screen.getByText('Description changed')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'diff.downloadHtml' })).not.toBeInTheDocument()
  })
})
