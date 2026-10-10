import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '../../../test/test-utils'
import MeasureExportConformanceDialog from '../MeasureExportConformanceDialog'
import type { MeasureExportConformance } from '../../../types'

// test-utils does NOT initialize i18next; mock so queries match the i18n key strings.
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

let getExportConformanceMock: ReturnType<typeof vi.fn>

vi.mock('../../../api', () => ({
  measureApi: {
    getExportConformance: (id: number) => getExportConformanceMock(id),
  },
}))

const QM = 'http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/'

const READY: MeasureExportConformance = {
  profiles: [`${QM}computable-measure-cqfm`, `${QM}proportion-measure-cqfm`],
  libraryProfiles: ['http://hl7.org/fhir/uv/cql/StructureDefinition/cql-library'],
  issues: [
    { severity: 'info', element: 'Measure.name', message: 'Name is not a computer-friendly token.' },
    { severity: 'warning', element: 'ValueSet', message: 'Value set https://example.org/vs/b is not loaded here.' },
  ],
  valueSets: [
    { url: 'https://example.org/vs/a', name: 'Diabetes', included: true, source: 'ig' },
    { url: 'https://example.org/vs/b', name: 'Not loaded', included: false, source: 'none' },
  ],
  canonicalBaseConfigured: true,
  exchangeReady: true,
}

describe('MeasureExportConformanceDialog — PAT-229', () => {
  beforeEach(() => {
    getExportConformanceMock = vi.fn().mockResolvedValue(READY)
  })

  it('does not ask the backend until it is opened', () => {
    render(<MeasureExportConformanceDialog measureId={7} open={false} onClose={vi.fn()} onExport={vi.fn()} />)
    expect(getExportConformanceMock).not.toHaveBeenCalled()
  })

  it('shows claimed profiles by their short name and which value sets travel in full', async () => {
    render(<MeasureExportConformanceDialog measureId={7} open onClose={vi.fn()} onExport={vi.fn()} />)

    expect(await screen.findByText('exportConformance.ready')).toBeInTheDocument()
    expect(getExportConformanceMock).toHaveBeenCalledWith(7)
    expect(screen.getByText('proportion-measure-cqfm')).toBeInTheDocument()
    expect(screen.getByText('cql-library')).toBeInTheDocument()

    const table = screen.getByRole('table', { name: 'exportConformance.valueSets' })
    const rows = within(table).getAllByRole('row')
    // header + two value sets
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('exportConformance.valueSetIncluded')).toBeInTheDocument()
    expect(within(rows[2]).getByText('exportConformance.valueSetNameOnly')).toBeInTheDocument()
    expect(screen.queryByText('exportConformance.canonicalBaseHint')).not.toBeInTheDocument()
  })

  it('lists findings with the most severe first', async () => {
    render(<MeasureExportConformanceDialog measureId={7} open onClose={vi.fn()} onExport={vi.fn()} />)
    await screen.findByText('exportConformance.ready')

    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('exportConformance.severity.warning')).toBeInTheDocument()
    expect(within(items[1]).getByText('exportConformance.severity.info')).toBeInTheDocument()
    expect(within(items[1]).getByText('Measure.name')).toBeInTheDocument()
  })

  it('says so when the package cannot be run elsewhere, and when canonical URLs are placeholders', async () => {
    getExportConformanceMock = vi.fn().mockResolvedValue({
      ...READY,
      profiles: [],
      issues: [{ severity: 'error', element: 'Library.content', message: 'CQL does not translate.' }],
      canonicalBaseConfigured: false,
      exchangeReady: false,
    })
    render(<MeasureExportConformanceDialog measureId={7} open onClose={vi.fn()} onExport={vi.fn()} />)

    expect(await screen.findByText('exportConformance.notReady')).toBeInTheDocument()
    expect(screen.getByText('exportConformance.noProfiles')).toBeInTheDocument()
    expect(screen.getByText('exportConformance.canonicalBaseHint')).toBeInTheDocument()
    expect(screen.getByText('CQL does not translate.')).toBeInTheDocument()
  })

  it('exports in the chosen format once the report is there', async () => {
    const onExport = vi.fn()
    render(<MeasureExportConformanceDialog measureId={7} open onClose={vi.fn()} onExport={onExport} />)

    const jsonButton = screen.getByRole('button', { name: 'editor.exportFormats.fhirJson' })
    expect(jsonButton).toBeDisabled()
    await screen.findByText('exportConformance.ready')

    fireEvent.click(jsonButton)
    fireEvent.click(screen.getByRole('button', { name: 'editor.exportFormats.fhirXml' }))
    expect(onExport.mock.calls).toEqual([['bundle-json'], ['bundle-xml']])
  })

  it('shows the backend message when the check itself fails', async () => {
    getExportConformanceMock = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'Measure not found' } },
    })
    render(<MeasureExportConformanceDialog measureId={7} open onClose={vi.fn()} onExport={vi.fn()} />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'editor.exportFormats.fhirJson' })).toBeDisabled()
  })
})
